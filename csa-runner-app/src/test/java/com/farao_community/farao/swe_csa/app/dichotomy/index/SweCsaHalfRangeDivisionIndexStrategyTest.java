package com.farao_community.farao.swe_csa.app.dichotomy.index;

/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

/**
 * @author Jean-Pierre Arnould {@literal <jean-pierre.arnould at rte-france.com>}
 */

import com.farao_community.farao.data.crac_api.Crac;
import com.farao_community.farao.data.crac_api.cnec.FlowCnec;
import com.farao_community.farao.dichotomy.api.results.DichotomyStepResult;
import com.farao_community.farao.swe_csa.app.FileHelper;
import com.farao_community.farao.swe_csa.app.dichotomy.CounterTradingDirection;
import com.farao_community.farao.swe_csa.app.dichotomy.variable.MultipleDichotomyVariables;
import com.farao_community.farao.swe_csa.app.rao_result.RaoResultWithCounterTradeRangeActions;
import com.powsybl.iidm.network.Network;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
public class SweCsaHalfRangeDivisionIndexStrategyTest {

    @Autowired
    FileHelper fileHelper;

    @Test
    void testNextValuePrecisionReached() {
        Index<Object, MultipleDichotomyVariables> index1 = new Index<>(new MultipleDichotomyVariables(Map.of(CounterTradingDirection.PT_ES.getName(), 0.0, CounterTradingDirection.FR_ES.getName(), 0.0)),
            new MultipleDichotomyVariables(Map.of(CounterTradingDirection.PT_ES.getName(), 1500.0, CounterTradingDirection.FR_ES.getName(), 1000.0)), 10);
        DichotomyStepResult dichotomyStepResult1 = Mockito.mock(DichotomyStepResult.class);
        Mockito.when(dichotomyStepResult1.isValid()).thenReturn(false);
        DichotomyStepResult dichotomyStepResult2 = Mockito.mock(DichotomyStepResult.class);
        Mockito.when(dichotomyStepResult2.isValid()).thenReturn(true);
        index1.addDichotomyStepResult(new MultipleDichotomyVariables(Map.of(CounterTradingDirection.PT_ES.getName(), 1250.0, CounterTradingDirection.FR_ES.getName(), 550.0)), dichotomyStepResult1);
        index1.addDichotomyStepResult(new MultipleDichotomyVariables(Map.of(CounterTradingDirection.PT_ES.getName(), 1240.0, CounterTradingDirection.FR_ES.getName(), 540.0)), dichotomyStepResult2);
        Network networkMock = Mockito.mock(Network.class);
        Crac cracMock = Mockito.mock(Crac.class);

        SweCsaHalfRangeDivisionIndexStrategy indexStrategy1 = new SweCsaHalfRangeDivisionIndexStrategy(cracMock, networkMock);
        assertThrows(AssertionError.class, () -> indexStrategy1.nextValue(index1));
    }

    @Test
    void testNextValueOneLimitStepNull() {
        Index<Object, MultipleDichotomyVariables> index1 = new Index<>(new MultipleDichotomyVariables(Map.of(CounterTradingDirection.PT_ES.getName(), 0.0, CounterTradingDirection.FR_ES.getName(), 0.0)),
            new MultipleDichotomyVariables(Map.of(CounterTradingDirection.PT_ES.getName(), 1500.0, CounterTradingDirection.FR_ES.getName(), 1000.0)), 10);
        Network networkMock = Mockito.mock(Network.class);
        Crac cracMock = Mockito.mock(Crac.class);
        SweCsaHalfRangeDivisionIndexStrategy indexStrategy1 = new SweCsaHalfRangeDivisionIndexStrategy(cracMock, networkMock);

        MultipleDichotomyVariables result1 = new MultipleDichotomyVariables(Map.of(CounterTradingDirection.PT_ES.getName(), 1500.0, CounterTradingDirection.FR_ES.getName(), 1000.0));
        MultipleDichotomyVariables result2 = new MultipleDichotomyVariables(Map.of(CounterTradingDirection.PT_ES.getName(), 0.0, CounterTradingDirection.FR_ES.getName(), 0.0));

        //lowestInvalidStep is null
        assertEquals(result1.print(), indexStrategy1.nextValue(index1).print());
    }

    @Test
    void testNextValue() {
        Index<Object, MultipleDichotomyVariables> index1 = new Index<>(new MultipleDichotomyVariables(Map.of(CounterTradingDirection.PT_ES.getName(), 0.0, CounterTradingDirection.FR_ES.getName(), 0.0)),
            new MultipleDichotomyVariables(Map.of(CounterTradingDirection.PT_ES.getName(), 1500.0, CounterTradingDirection.FR_ES.getName(), 1000.0)), 10);
        Path filePath = Paths.get(new File(getClass().getResource("/CSA_42_CustomExample.zip").getFile()).toString());
        Network network = fileHelper.importNetwork(Paths.get(new File(getClass().getResource("/CSA_42_CustomExample.zip").getFile()).toString()));
        Crac crac = fileHelper.importCrac(filePath, network, Instant.parse("2023-03-29T12:00:00Z"));
        SweCsaHalfRangeDivisionIndexStrategy indexStrategy1 = new SweCsaHalfRangeDivisionIndexStrategy(crac, network);

        DichotomyStepResult dichotomyStepResult1 = Mockito.mock(DichotomyStepResult.class);
        Mockito.when(dichotomyStepResult1.isValid()).thenReturn(false);
        DichotomyStepResult dichotomyStepResult2 = Mockito.mock(DichotomyStepResult.class);
        Mockito.when(dichotomyStepResult2.isValid()).thenReturn(true);
        RaoResultWithCounterTradeRangeActions raoResultCTMock1 = Mockito.mock(RaoResultWithCounterTradeRangeActions.class);
        Mockito.when(raoResultCTMock1.getMargin(Mockito.any(), Mockito.any(FlowCnec.class), Mockito.any())).thenAnswer(invocationOnMock -> {
            FlowCnec flowCnec = invocationOnMock.getArgument(1);
            return flowCnec.toString().equals("RTE_AE (183829bd-5c60-4c04-ad57-c72a15a75047) - RTE_CO1 - curative") ? -5.0 : 46.0;
        });
        RaoResultWithCounterTradeRangeActions raoResultCTMock2 = Mockito.mock(RaoResultWithCounterTradeRangeActions.class);
        Mockito.when(raoResultCTMock2.getMargin(Mockito.any(), Mockito.any(FlowCnec.class), Mockito.any())).thenReturn(3.54);
        Mockito.when(dichotomyStepResult1.getRaoResult()).thenReturn(raoResultCTMock1);
        Mockito.when(dichotomyStepResult2.getRaoResult()).thenReturn(raoResultCTMock2);

        MultipleDichotomyVariables result3 = new MultipleDichotomyVariables(Map.of(CounterTradingDirection.PT_ES.getName(), 1500.0, CounterTradingDirection.FR_ES.getName(), 550.0));
        index1.addDichotomyStepResult(new MultipleDichotomyVariables(Map.of(CounterTradingDirection.PT_ES.getName(), 1000.0, CounterTradingDirection.FR_ES.getName(), 1000.0)), dichotomyStepResult1);
        index1.addDichotomyStepResult(new MultipleDichotomyVariables(Map.of(CounterTradingDirection.PT_ES.getName(), 200.0, CounterTradingDirection.FR_ES.getName(), 100.0)), dichotomyStepResult2);
        //in general case
        assertEquals(result3.print(), indexStrategy1.nextValue(index1).print());
    }

    @Test
    void testPrecisionReach() {
        Index<Object, MultipleDichotomyVariables> index1 = new Index<>(new MultipleDichotomyVariables(Map.of(CounterTradingDirection.PT_ES.getName(), 0.0, CounterTradingDirection.FR_ES.getName(), 0.0)),
            new MultipleDichotomyVariables(Map.of(CounterTradingDirection.PT_ES.getName(), 1500.0, CounterTradingDirection.FR_ES.getName(), 1000.0)), 10);
        Network networkMock = Mockito.mock(Network.class);
        Crac cracMock = Mockito.mock(Crac.class);
        SweCsaHalfRangeDivisionIndexStrategy indexStrategy1 = new SweCsaHalfRangeDivisionIndexStrategy(cracMock, networkMock);
        // always false when there's no step
        assertFalse(indexStrategy1.precisionReached(index1));

        DichotomyStepResult dichotomyStepResult0 = Mockito.mock(DichotomyStepResult.class);
        Mockito.when(dichotomyStepResult0.isValid()).thenReturn(true);
        index1.addDichotomyStepResult(new MultipleDichotomyVariables(Map.of(CounterTradingDirection.PT_ES.getName(), 700.0, CounterTradingDirection.FR_ES.getName(), 700.0)), dichotomyStepResult0);
        // false when there's only valid step, away from max value
        assertFalse(indexStrategy1.precisionReached(index1));

        Index<Object, MultipleDichotomyVariables> index2 = new Index<>(new MultipleDichotomyVariables(Map.of(CounterTradingDirection.PT_ES.getName(), 0.0, CounterTradingDirection.FR_ES.getName(), 0.0)),
            new MultipleDichotomyVariables(Map.of(CounterTradingDirection.PT_ES.getName(), 1500.0, CounterTradingDirection.FR_ES.getName(), 1000.0)), 10);
        DichotomyStepResult dichotomyStepResult1 = Mockito.mock(DichotomyStepResult.class);
        Mockito.when(dichotomyStepResult1.isValid()).thenReturn(false);
        index2.addDichotomyStepResult(new MultipleDichotomyVariables(Map.of(CounterTradingDirection.PT_ES.getName(), SweCsaHalfRangeDivisionIndexStrategy.EPSILON / 2, CounterTradingDirection.FR_ES.getName(), SweCsaHalfRangeDivisionIndexStrategy.EPSILON / 2)), dichotomyStepResult1);
        // true when lowestInvalidStep is nearer to index min value than EPSILON
        assertTrue(indexStrategy1.precisionReached(index2));

        Index<Object, MultipleDichotomyVariables> index3 = new Index<>(new MultipleDichotomyVariables(Map.of(CounterTradingDirection.PT_ES.getName(), 0.0, CounterTradingDirection.FR_ES.getName(), 0.0)),
            new MultipleDichotomyVariables(Map.of(CounterTradingDirection.PT_ES.getName(), 1500.0, CounterTradingDirection.FR_ES.getName(), 1000.0)), 10);
        DichotomyStepResult dichotomyStepResult2 = Mockito.mock(DichotomyStepResult.class);
        Mockito.when(dichotomyStepResult2.isValid()).thenReturn(true);
        index3.addDichotomyStepResult(new MultipleDichotomyVariables(Map.of(CounterTradingDirection.PT_ES.getName(), 1500.0 - SweCsaHalfRangeDivisionIndexStrategy.EPSILON / 2, CounterTradingDirection.FR_ES.getName(), 1000.0 - SweCsaHalfRangeDivisionIndexStrategy.EPSILON / 2)), dichotomyStepResult2);
        // true when highestValidStep is nearer to index max value than EPSILON
        assertTrue(indexStrategy1.precisionReached(index3));

        Index<Object, MultipleDichotomyVariables> index4 = new Index<>(new MultipleDichotomyVariables(Map.of(CounterTradingDirection.PT_ES.getName(), 0.0, CounterTradingDirection.FR_ES.getName(), 0.0)),
            new MultipleDichotomyVariables(Map.of(CounterTradingDirection.PT_ES.getName(), 1500.0, CounterTradingDirection.FR_ES.getName(), 1000.0)), 10);
        DichotomyStepResult dichotomyStepResult4 = Mockito.mock(DichotomyStepResult.class);
        Mockito.when(dichotomyStepResult4.isValid()).thenReturn(false);
        index4.addDichotomyStepResult(new MultipleDichotomyVariables(Map.of(CounterTradingDirection.PT_ES.getName(), 100.0, CounterTradingDirection.FR_ES.getName(), 100.0)), dichotomyStepResult4);
        // false because there's an invalidStep more away from min value than index precision, and no validStep
        assertFalse(indexStrategy1.precisionReached(index4));

        DichotomyStepResult dichotomyStepResult5 = Mockito.mock(DichotomyStepResult.class);
        Mockito.when(dichotomyStepResult5.isValid()).thenReturn(true);
        index4.addDichotomyStepResult(new MultipleDichotomyVariables(Map.of(CounterTradingDirection.PT_ES.getName(), 90.0, CounterTradingDirection.FR_ES.getName(), 90.0)), dichotomyStepResult5);
        // true because distance between valid sted and valid step is equal to index precision
        assertTrue(indexStrategy1.precisionReached(index4));
    }
}
