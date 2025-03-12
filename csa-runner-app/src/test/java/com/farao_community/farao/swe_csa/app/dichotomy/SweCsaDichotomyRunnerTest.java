package com.farao_community.farao.swe_csa.app.dichotomy;

import com.farao_community.farao.dichotomy.api.exceptions.GlskLimitationException;
import com.farao_community.farao.dichotomy.api.exceptions.ShiftingException;
import com.farao_community.farao.rao_runner.api.resource.AbstractRaoResponse;
import com.farao_community.farao.rao_runner.starter.RaoRunnerClient;
import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
import com.farao_community.farao.swe_csa.api.results.CounterTradeRangeActionResult;
import com.farao_community.farao.swe_csa.app.FileExporter;
import com.farao_community.farao.swe_csa.app.FileImporter;
import com.farao_community.farao.swe_csa.app.InterruptionService;
import com.farao_community.farao.swe_csa.app.rao_result.RaoResultWithCounterTradeRangeActions;
import com.farao_community.farao.swe_csa.app.s3.S3ArtifactsAdapter;
import com.farao_community.farao.swe_csa.app.shift.SweCsaZonalData;
import com.powsybl.glsk.commons.ZonalData;
import com.powsybl.iidm.modification.scalable.Scalable;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.CracFactory;
import com.powsybl.openrao.data.crac.impl.CounterTradeRangeActionImpl;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.function.StreamBridge;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyDouble;

@SpringBootTest
class SweCsaDichotomyRunnerTest {

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

    @Test
    void runCounterTradingTest() throws GlskLimitationException, ShiftingException {
        Instant utcInstant = Instant.parse("2023-09-13T09:30:00Z");
        Network network = Network.read("/dichotomy/TestCase_with_swe_countries.xiidm", getClass().getResourceAsStream("/dichotomy/TestCase_with_swe_countries.xiidm"));
        ZonalData<Scalable> scalableZonalData = SweCsaZonalData.getZonalData(network);
        Crac crac = CracFactory.findDefault().create("id");
        Mockito.when(fileImporter.uploadRaoParameters(utcInstant)).thenReturn("rao-parameters-url");
        Mockito.when(fileImporter.importNetwork("id", "cgm-url")).thenReturn(network);
        Mockito.when(fileImporter.importCrac("id", "ptEs-crac-url", network)).thenReturn(crac);
        Mockito.when(fileImporter.getZonalData("id", utcInstant, "glsk-url", network, false)).thenReturn(scalableZonalData);
        Mockito.when(fileImporter.getZonalData("id", utcInstant, "glsk-url", network, true)).thenReturn(scalableZonalData);
        Mockito.when(fileExporter.saveNetworkInArtifact(Mockito.anyString(), Mockito.any(), Mockito.any())).thenReturn("scaled-network-url");
        AbstractRaoResponse raoResponse = Mockito.mock(AbstractRaoResponse.class);
        Mockito.when(raoRunnerClient.runRao(Mockito.any())).thenReturn(raoResponse);
        SweCsaRaoValidator sweCsaRaoValidator = new SweCsaRaoValidatorMock(fileExporter, raoRunnerClient);

        DichotomyRunner sweCsaDichotomyRunner = new DichotomyRunner(sweCsaRaoValidator, fileImporter, fileExporter, interruptionService, streamBridge, s3ArtifactsAdapter, LoggerFactory.getLogger(SweCsaDichotomyRunnerTest.class));
        sweCsaDichotomyRunner.setIndexPrecision(50);
        sweCsaDichotomyRunner.setMaxDichotomiesByBorder(10);
        CsaRequest csaRequest = new CsaRequest("id", "2023-09-13T09:30:00Z", "cgm-url", "glsk-url", "ptEs-crac-url", "frEs-crac-url");
        RaoResultWithCounterTradeRangeActions raoResult = (RaoResultWithCounterTradeRangeActions) sweCsaDichotomyRunner.runDichotomy(csaRequest, "rao-result-url").getFirst();

        Iterator<CounterTradeRangeActionResult> ctRaResultIt  = raoResult.getCounterTradingResult().getCounterTradeRangeActionResults().values().stream().sorted(Comparator.comparing(CounterTradeRangeActionResult::getCtRangeActionId)).collect(Collectors.toCollection(LinkedHashSet::new)).iterator();

        CounterTradeRangeActionResult esFrCtRaResult = ctRaResultIt.next();
        assertEquals("CT_RA_ESFR", esFrCtRaResult.getCtRangeActionId());
        assertEquals(629., esFrCtRaResult.getSetPoint(), 1);

        CounterTradeRangeActionResult esPtCtRaResult = ctRaResultIt.next();
        assertEquals("CT_RA_ESPT", esPtCtRaResult.getCtRangeActionId());
        assertEquals(0., esPtCtRaResult.getSetPoint());

        CounterTradeRangeActionResult frEsCtRaResult = ctRaResultIt.next();
        assertEquals("CT_RA_FRES", frEsCtRaResult.getCtRangeActionId());
        assertEquals(629., frEsCtRaResult.getSetPoint(), 1);

        CounterTradeRangeActionResult ptEsCtRaResult = ctRaResultIt.next();
        assertEquals("CT_RA_PTES", ptEsCtRaResult.getCtRangeActionId());
        assertEquals(0., ptEsCtRaResult.getSetPoint());
    }

    @Test
    void getMaxCounterTradingTestMaximumReached() {
        SweCsaRaoValidator sweCsaRaoValidatorMock = Mockito.mock(SweCsaRaoValidator.class);
        DichotomyRunner dichotomyRunner = new DichotomyRunner(sweCsaRaoValidatorMock, fileImporter, fileExporter, interruptionService, streamBridge, s3ArtifactsAdapter, LoggerFactory.getLogger(SweCsaDichotomyRunnerTest.class));
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
        SweCsaRaoValidator sweCsaRaoValidatorMock = Mockito.mock(SweCsaRaoValidator.class);
        DichotomyRunner dichotomyRunner = new DichotomyRunner(sweCsaRaoValidatorMock, fileImporter, fileExporter, interruptionService, streamBridge, s3ArtifactsAdapter, LoggerFactory.getLogger(SweCsaDichotomyRunnerTest.class));
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

