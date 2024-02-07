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

import com.farao_community.farao.dichotomy.api.results.DichotomyStepResult;
import com.farao_community.farao.swe_csa.app.dichotomy.CounterTradingDirection;
import com.farao_community.farao.swe_csa.app.dichotomy.variable.MultipleDichotomyVariables;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SweCsaHalfRangeDivisionIndexStrategyTest {
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

        SweCsaHalfRangeDivisionIndexStrategy indexStrategy1 = new SweCsaHalfRangeDivisionIndexStrategy(CounterTradingDirection.FR_ES.getName(), CounterTradingDirection.PT_ES.getName());
        assertThrows(AssertionError.class, () -> indexStrategy1.nextValue(index1));
    }

    @Test
    void testNextValueOneLimitStepNull() {
        Index<Object, MultipleDichotomyVariables> index1 = new Index<>(new MultipleDichotomyVariables(Map.of(CounterTradingDirection.PT_ES.getName(), 0.0, CounterTradingDirection.FR_ES.getName(), 0.0)),
            new MultipleDichotomyVariables(Map.of(CounterTradingDirection.PT_ES.getName(), 1500.0, CounterTradingDirection.FR_ES.getName(), 1000.0)), 10);
        SweCsaHalfRangeDivisionIndexStrategy indexStrategy1 = new SweCsaHalfRangeDivisionIndexStrategy(CounterTradingDirection.FR_ES.getName(), CounterTradingDirection.PT_ES.getName());

        MultipleDichotomyVariables result1 = new MultipleDichotomyVariables(Map.of(CounterTradingDirection.PT_ES.getName(), 1500.0, CounterTradingDirection.FR_ES.getName(), 1000.0));
        MultipleDichotomyVariables result2 = new MultipleDichotomyVariables(Map.of(CounterTradingDirection.PT_ES.getName(), 0.0, CounterTradingDirection.FR_ES.getName(), 0.0));

        //lowestInvalidStep is null
        assertEquals(result1.print(), indexStrategy1.nextValue(index1).print());

        DichotomyStepResult dichotomyStepResult1 = Mockito.mock(DichotomyStepResult.class);
        Mockito.when(dichotomyStepResult1.isValid()).thenReturn(false);
        DichotomyStepResult dichotomyStepResult2 = Mockito.mock(DichotomyStepResult.class);
        Mockito.when(dichotomyStepResult2.isValid()).thenReturn(true);
        index1.addDichotomyStepResult(new MultipleDichotomyVariables(Map.of(CounterTradingDirection.PT_ES.getName(), 500.0, CounterTradingDirection.FR_ES.getName(), 400.0)), dichotomyStepResult1);
        //highestValidStep is null
        assertEquals(result2.print(), indexStrategy1.nextValue(index1).print());
    }

    @Test
    void testNextValueGeneralCase() {
        Index<Object, MultipleDichotomyVariables> index1 = new Index<>(new MultipleDichotomyVariables(Map.of(CounterTradingDirection.PT_ES.getName(), 0.0, CounterTradingDirection.FR_ES.getName(), 0.0)),
            new MultipleDichotomyVariables(Map.of(CounterTradingDirection.PT_ES.getName(), 1500.0, CounterTradingDirection.FR_ES.getName(), 1000.0)), 10);
        SweCsaHalfRangeDivisionIndexStrategy indexStrategy1 = new SweCsaHalfRangeDivisionIndexStrategy(CounterTradingDirection.FR_ES.getName(), CounterTradingDirection.PT_ES.getName());
        DichotomyStepResult dichotomyStepResult2 = Mockito.mock(DichotomyStepResult.class);
        Mockito.when(dichotomyStepResult2.isValid()).thenReturn(true);
        MultipleDichotomyVariables result3 = new MultipleDichotomyVariables(Map.of(CounterTradingDirection.PT_ES.getName(), 350.0, CounterTradingDirection.FR_ES.getName(), 250.0));
        index1.addDichotomyStepResult(new MultipleDichotomyVariables(Map.of(CounterTradingDirection.PT_ES.getName(), 200.0, CounterTradingDirection.FR_ES.getName(), 100.0)), dichotomyStepResult2);
        //in general case
        assertEquals(result3.print(), indexStrategy1.nextValue(index1).print());
    }

    @Test
    void testPrecisionReach() {
        Index<Object, MultipleDichotomyVariables> index1 = new Index<>(new MultipleDichotomyVariables(Map.of(CounterTradingDirection.PT_ES.getName(), 0.0, CounterTradingDirection.FR_ES.getName(), 0.0)),
            new MultipleDichotomyVariables(Map.of(CounterTradingDirection.PT_ES.getName(), 1500.0, CounterTradingDirection.FR_ES.getName(), 1000.0)), 10);
        SweCsaHalfRangeDivisionIndexStrategy indexStrategy1 = new SweCsaHalfRangeDivisionIndexStrategy(CounterTradingDirection.FR_ES.getName(), CounterTradingDirection.PT_ES.getName());
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
