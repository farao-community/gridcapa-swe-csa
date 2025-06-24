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
import com.farao_community.farao.swe_csa.app.s3.S3ArtifactsAdapter;
import com.farao_community.farao.swe_csa.app.shift.SweCsaZonalData;
import com.powsybl.glsk.commons.ZonalData;
import com.powsybl.iidm.modification.scalable.Scalable;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.CracFactory;
import com.powsybl.openrao.data.crac.impl.CounterTradeRangeActionImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.function.StreamBridge;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;

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
        Crac ptEsCrac = CracFactory.findDefault().create("pt-es-crac");
        Crac frEsCrac = CracFactory.findDefault().create("fr-es-crac");

        Mockito.doNothing().when(s3ArtifactsAdapter).uploadFile(any(), any());
        Mockito.when(fileImporter.uploadRaoParameters(utcInstant)).thenReturn("rao-parameters-url");
        Mockito.when(fileImporter.importNetwork("csa-task-id", "cgm-url")).thenReturn(network);
        Mockito.when(fileImporter.importCrac("csa-task-id", "pt-es-crac-url", network)).thenReturn(ptEsCrac);
        Mockito.when(fileImporter.importCrac("csa-task-id", "fr-es-crac-url", network)).thenReturn(frEsCrac);
        Mockito.when(fileImporter.getZonalData("csa-task-id", utcInstant, "glsk-url", network)).thenReturn(scalableZonalData);
        Mockito.when(fileExporter.saveNetworkInArtifact(Mockito.anyString(), Mockito.any(), Mockito.any())).thenReturn("scaled-network-url");
        AbstractRaoResponse raoResponse = Mockito.mock(AbstractRaoResponse.class);
        Mockito.when(raoRunnerClient.runRao(Mockito.any())).thenReturn(raoResponse);
        SweCsaRaoValidator sweCsaRaoValidator = new SweCsaRaoValidatorMock(fileExporter, raoRunnerClient);
        CsaRequest csaRequest = new CsaRequest("csa-task-id", "2023-09-13T09:30:00Z", "cgm-url", "glsk-url", "pt-es-crac-url", "fr-es-crac-url");

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

}

