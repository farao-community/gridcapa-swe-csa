package com.farao_community.farao.swe_csa.app.dichotomy;

import com.farao_community.farao.rao_runner.starter.RaoRunnerClient;
import com.farao_community.farao.swe_csa.app.FileExporter;
import com.farao_community.farao.swe_csa.app.FileImporter;
import com.farao_community.farao.swe_csa.app.InterruptionService;
import com.farao_community.farao.swe_csa.app.s3.S3ArtifactsAdapter;
import com.powsybl.openrao.data.crac.impl.CounterTradeRangeActionImpl;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cloud.stream.function.StreamBridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyDouble;

@SpringBootTest
class SweCsaDichotomyRunnerTest {

    @Autowired
    ParallelDichotomiesRunner parallelDichotomiesRunner;

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
    void getMaxCounterTradingTestMaximumReached() {
        SweCsaRaoValidator sweCsaRaoValidatorMock = Mockito.mock(SweCsaRaoValidator.class);
        DichotomyRunner dichotomyRunner = new DichotomyRunner(sweCsaRaoValidatorMock, fileImporter, fileExporter, interruptionService, streamBridge, s3ArtifactsAdapter, LoggerFactory.getLogger(SweCsaDichotomyRunnerTest.class), parallelDichotomiesRunner);
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
        DichotomyRunner dichotomyRunner = new DichotomyRunner(sweCsaRaoValidatorMock, fileImporter, fileExporter, interruptionService, streamBridge, s3ArtifactsAdapter, LoggerFactory.getLogger(SweCsaDichotomyRunnerTest.class), parallelDichotomiesRunner);
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

