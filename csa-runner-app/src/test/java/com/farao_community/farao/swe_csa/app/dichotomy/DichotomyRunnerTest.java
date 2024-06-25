package com.farao_community.farao.swe_csa.app.dichotomy;

import com.farao_community.farao.swe_csa.app.FileExporter;
import com.farao_community.farao.swe_csa.app.FileImporter;
import com.powsybl.openrao.data.cracimpl.CounterTradeRangeActionImpl;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyDouble;

public class DichotomyRunnerTest {

    @Test
    void getMaxCounterTradingTestMaximumReached() {
        SweCsaRaoValidator sweCsaRaoValidatorMock = Mockito.mock(SweCsaRaoValidator.class);
        FileImporter fileImporterMock = Mockito.mock(FileImporter.class);
        FileExporter fileExporterMock = Mockito.mock(FileExporter.class);
        DichotomyRunner dichotomyRunner = new DichotomyRunner(sweCsaRaoValidatorMock, fileImporterMock, fileExporterMock);
        CounterTradeRangeActionImpl ctraMock1 = Mockito.mock(CounterTradeRangeActionImpl.class);
        Mockito.when(ctraMock1.getMinAdmissibleSetpoint(anyDouble())).thenReturn(-1000.0);
        Mockito.when(ctraMock1.getMaxAdmissibleSetpoint(anyDouble())).thenReturn(1000.0);
        CounterTradeRangeActionImpl ctraMock2 = Mockito.mock(CounterTradeRangeActionImpl.class);
        Mockito.when(ctraMock2.getMinAdmissibleSetpoint(anyDouble())).thenReturn(-1000.0);
        Mockito.when(ctraMock2.getMaxAdmissibleSetpoint(anyDouble())).thenReturn(1000.0);

        double initialExchange1 = 500.0;
        double initialExchange2 = -500.0;

        double resultMaxCT1 = dichotomyRunner.getMaxCounterTrading(ctraMock1, ctraMock2, initialExchange1, initialExchange2, "");
        assertEquals(500.0, resultMaxCT1);

        double resultMaxCT2 = dichotomyRunner.getMaxCounterTrading(ctraMock1, ctraMock2, initialExchange2, initialExchange1, "");
        assertEquals(500.0, resultMaxCT2);
    }

    @Test
    void getMaxCounterTradingTestMaximumUnreached() {
        SweCsaRaoValidator sweCsaRaoValidatorMock = Mockito.mock(SweCsaRaoValidator.class);
        FileImporter fileImporterMock = Mockito.mock(FileImporter.class);
        FileExporter fileExporterMock = Mockito.mock(FileExporter.class);
        DichotomyRunner dichotomyRunner = new DichotomyRunner(sweCsaRaoValidatorMock, fileImporterMock, fileExporterMock);
        CounterTradeRangeActionImpl ctraMock1 = Mockito.mock(CounterTradeRangeActionImpl.class);
        Mockito.when(ctraMock1.getMinAdmissibleSetpoint(anyDouble())).thenReturn(-350.0);
        Mockito.when(ctraMock1.getMaxAdmissibleSetpoint(anyDouble())).thenReturn(300.0);
        CounterTradeRangeActionImpl ctraMock2 = Mockito.mock(CounterTradeRangeActionImpl.class);
        Mockito.when(ctraMock2.getMinAdmissibleSetpoint(anyDouble())).thenReturn(-450.0);
        Mockito.when(ctraMock2.getMaxAdmissibleSetpoint(anyDouble())).thenReturn(400.0);

        double initialExchange1 = 500.0;
        double initialExchange2 = -500.0;

        double resultMaxCT1 = dichotomyRunner.getMaxCounterTrading(ctraMock1, ctraMock2, initialExchange1, initialExchange2, "");
        assertEquals(350.0, resultMaxCT1);

        double resultMaxCT2 = dichotomyRunner.getMaxCounterTrading(ctraMock1, ctraMock2, initialExchange2, initialExchange1, "");
        assertEquals(300.0, resultMaxCT2);
    }
}
