package com.farao_community.farao.swe_csa.app.dichotomy;

import com.farao_community.farao.dichotomy.api.exceptions.GlskLimitationException;
import com.farao_community.farao.dichotomy.api.exceptions.ShiftingException;
import com.farao_community.farao.rao_runner.api.resource.AbstractRaoResponse;
import com.farao_community.farao.rao_runner.starter.RaoRunnerClient;
import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
import com.farao_community.farao.swe_csa.api.resource.Status;
import com.farao_community.farao.swe_csa.app.FileExporter;
import com.farao_community.farao.swe_csa.app.FileImporter;
import com.farao_community.farao.swe_csa.app.InterruptionService;
import com.farao_community.farao.swe_csa.app.rao_result.RaoResultWithCounterTradeRangeActions;
import com.farao_community.farao.swe_csa.app.s3.S3ArtifactsAdapter;
import com.farao_community.farao.swe_csa.app.shift.SweCsaZonalData;
import com.powsybl.glsk.commons.ZonalData;
import com.powsybl.iidm.modification.scalable.Scalable;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.cnec.VoltageCnec;
import com.powsybl.openrao.data.crac.impl.CounterTradeRangeActionImpl;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verify;

@SpringBootTest
class SweCsaDichotomyRunnerTest {

    @Autowired
    DichotomyRunner dichotomyRunner;

    @Mock
    FileImporter fileImporter;
    @Mock
    FileExporter fileExporter;
    @Mock
    RaoRunnerClient raoRunnerClient;

    @MockBean
    StreamBridge streamBridge;

    @MockBean
    S3ArtifactsAdapter s3ArtifactsAdapter;

    @MockBean
    InterruptionService interruptionService;

    @Autowired
    ParallelDichotomiesRunner parallelDichotomiesRunner;

    @Test
    void runCounterTradingTest() throws GlskLimitationException, ShiftingException {
        Instant utcInstant = Instant.parse("2023-09-13T09:30:00Z");
        Network network = Network.read("/dichotomy/TestCase_with_swe_countries.xiidm", getClass().getResourceAsStream("/dichotomy/TestCase_with_swe_countries.xiidm"));
        ZonalData<Scalable> scalableZonalData = SweCsaZonalData.getZonalData(network);

        // Mock CRACs with the required CT RAs
        Crac ptEsCrac = Mockito.mock(Crac.class);
        Crac frEsCrac = Mockito.mock(Crac.class);

        CounterTradeRangeActionImpl ctRaPtEs = mockCtAction(Country.PT, Country.ES);
        CounterTradeRangeActionImpl ctRaEsPt = mockCtAction(Country.ES, Country.PT);
        CounterTradeRangeActionImpl ctRaFrEs = mockCtAction(Country.FR, Country.ES);
        CounterTradeRangeActionImpl ctRaEsFr = mockCtAction(Country.ES, Country.FR);

        Mockito.when(ptEsCrac.getCounterTradeRangeActions()).thenReturn(Set.of(ctRaPtEs, ctRaEsPt));
        Mockito.when(frEsCrac.getCounterTradeRangeActions()).thenReturn(Set.of(ctRaFrEs, ctRaEsFr));

        Mockito.doNothing().when(s3ArtifactsAdapter).uploadFile(any(), any());
        Mockito.when(fileExporter.uploadRaoParameters(utcInstant, RaoParameters.load())).thenReturn("rao-parameters-url");
        Mockito.when(fileImporter.importNetwork("csa-task-id", "cgm-url")).thenReturn(network);
        Mockito.when(fileImporter.importCrac("csa-task-id", "pt-es-crac-url", network)).thenReturn(ptEsCrac);
        Mockito.when(fileImporter.importCrac("csa-task-id", "fr-es-crac-url", network)).thenReturn(frEsCrac);
        Mockito.when(fileImporter.getZonalData("csa-task-id", utcInstant, "glsk-url", network)).thenReturn(scalableZonalData);
        Mockito.when(fileExporter.saveNetworkInArtifact(Mockito.anyString(), Mockito.any(), Mockito.any())).thenReturn("scaled-network-url");
        AbstractRaoResponse raoResponse = Mockito.mock(AbstractRaoResponse.class);
        Mockito.when(raoRunnerClient.runRao(Mockito.any())).thenReturn(raoResponse);
        SweCsaRaoValidator sweCsaRaoValidator = new SweCsaRaoValidatorMock(fileExporter, raoRunnerClient);
        CsaRequest csaRequest = new CsaRequest("csa-task-id", "2023-09-13T09:30:00Z", "cgm-url", "glsk-url", "pt-es-crac-url", "fr-es-crac-url", null);

        DichotomyRunner sweCsaDichotomyRunner = new DichotomyRunner(sweCsaRaoValidator, fileImporter, fileExporter, interruptionService, streamBridge, s3ArtifactsAdapter, LoggerFactory.getLogger(SweCsaDichotomyRunnerTest.class), parallelDichotomiesRunner);
        sweCsaDichotomyRunner.setIndexPrecision(50);
        sweCsaDichotomyRunner.setMaxDichotomiesByBorder(10);
        FinalResult finalResult = sweCsaDichotomyRunner.runDichotomy(csaRequest, "pt-es-rao-result-path", "fr-es-rao-result-path");
        Assertions.assertEquals(Status.FINISHED_SECURE, finalResult.ptEsResult().getRight());
        Assertions.assertEquals(Status.FINISHED_SECURE, finalResult.frEsResult().getRight());

    }

    @Test
    void getMaxCounterTradingTestMaximumReached() {
        CounterTradeRangeActionImpl ctraMock1 = Mockito.mock(CounterTradeRangeActionImpl.class);
        Mockito.when(ctraMock1.getMinAdmissibleSetpoint(anyDouble())).thenReturn(-1000.0);
        Mockito.when(ctraMock1.getMaxAdmissibleSetpoint(anyDouble())).thenReturn(1000.0);
        CounterTradeRangeActionImpl ctraMock2 = Mockito.mock(CounterTradeRangeActionImpl.class);
        Mockito.when(ctraMock2.getMinAdmissibleSetpoint(anyDouble())).thenReturn(-1000.0);
        Mockito.when(ctraMock2.getMaxAdmissibleSetpoint(anyDouble())).thenReturn(1000.0);

        double initialExchange1 = 500.0;
        double initialExchange2 = -500.0;

        double resultMaxCT1 = dichotomyRunner.getMaxCounterTrading(ctraMock1, ctraMock2, initialExchange1, "");
        assertEquals(500.0, resultMaxCT1);

        double resultMaxCT2 = dichotomyRunner.getMaxCounterTrading(ctraMock1, ctraMock2, initialExchange2, "");
        assertEquals(500.0, resultMaxCT2);
    }

    @Test
    void getMaxCounterTradingTestMaximumUnreached() {
        CounterTradeRangeActionImpl ctraMock1 = Mockito.mock(CounterTradeRangeActionImpl.class);
        Mockito.when(ctraMock1.getMinAdmissibleSetpoint(anyDouble())).thenReturn(-350.0);
        Mockito.when(ctraMock1.getMaxAdmissibleSetpoint(anyDouble())).thenReturn(300.0);
        CounterTradeRangeActionImpl ctraMock2 = Mockito.mock(CounterTradeRangeActionImpl.class);
        Mockito.when(ctraMock2.getMinAdmissibleSetpoint(anyDouble())).thenReturn(-450.0);
        Mockito.when(ctraMock2.getMaxAdmissibleSetpoint(anyDouble())).thenReturn(400.0);

        double initialExchange1 = 500.0;
        double initialExchange2 = -500.0;

        double resultMaxCT1 = dichotomyRunner.getMaxCounterTrading(ctraMock1, ctraMock2, initialExchange1, "");
        assertEquals(350.0, resultMaxCT1);

        double resultMaxCT2 = dichotomyRunner.getMaxCounterTrading(ctraMock1, ctraMock2, initialExchange2, "");
        assertEquals(300.0, resultMaxCT2);
    }

    @Test
    void uploadFinalResultDoesNotRunVoltageMonitoringAgainWhenCracHasVoltageCnecs() {
        try (MockedConstruction<ResultHelper> mockedResultHelper = Mockito.mockConstruction(ResultHelper.class)) {
            FileExporter exporter = Mockito.mock(FileExporter.class);
            DichotomyRunner runner = newDichotomyRunnerWithMocks(exporter);
            ResultHelper resultHelper = mockedResultHelper.constructed().getFirst();

            Crac crac = Mockito.mock(Crac.class);
            Index index = Mockito.mock(Index.class);
            RaoResult monitoredRaoResult = Mockito.mock(RaoResult.class);
            RaoResultWithCounterTradeRangeActions ctEnrichedRaoResult = Mockito.mock(RaoResultWithCounterTradeRangeActions.class);
            Mockito.when(crac.getVoltageCnecs()).thenReturn(Set.of(Mockito.mock(VoltageCnec.class)));
            Mockito.when(resultHelper.updateRaoResultWithCounterTradingRangeActions(crac, index, monitoredRaoResult, "PT-ES")).thenReturn(ctEnrichedRaoResult);

            ReflectionTestUtils.invokeMethod(runner, "uploadFinalResult", crac, index, monitoredRaoResult, "pt-es-rao-result-path", "PT-ES");

            // voltage monitoring is already done in SweCsaMonitoring: it must not be run again here
            verify(resultHelper, never()).updateRaoResultWithVoltageMonitoring(any(), any(), any(), any());
            verify(resultHelper).updateRaoResultWithCounterTradingRangeActions(crac, index, monitoredRaoResult, "PT-ES");
            verifyNoMoreInteractions(resultHelper);
            verify(exporter).saveRaoResultInArtifact("pt-es-rao-result-path", ctEnrichedRaoResult, crac);
        }
    }

    private static CounterTradeRangeActionImpl mockCtAction(Country exporting, Country importing) {
        CounterTradeRangeActionImpl action = Mockito.mock(CounterTradeRangeActionImpl.class);
        Mockito.when(action.getExportingCountry()).thenReturn(exporting);
        Mockito.when(action.getImportingCountry()).thenReturn(importing);
        Mockito.when(action.getMinAdmissibleSetpoint(Mockito.anyDouble())).thenReturn(-50000.0);
        Mockito.when(action.getMaxAdmissibleSetpoint(Mockito.anyDouble())).thenReturn(50000.0);
        return action;
    }

    private static DichotomyRunner newDichotomyRunnerWithMocks(FileExporter exporter) {
        return new DichotomyRunner(
                Mockito.mock(SweCsaRaoValidator.class),
                Mockito.mock(FileImporter.class),
                exporter,
                Mockito.mock(InterruptionService.class),
                Mockito.mock(StreamBridge.class),
                Mockito.mock(S3ArtifactsAdapter.class),
                LoggerFactory.getLogger(SweCsaDichotomyRunnerTest.class),
                Mockito.mock(ParallelDichotomiesRunner.class));
    }
}
