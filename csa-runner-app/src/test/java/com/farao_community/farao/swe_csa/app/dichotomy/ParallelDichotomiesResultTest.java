package com.farao_community.farao.swe_csa.app.dichotomy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ParallelDichotomiesResultTest {

    @Mock
    private DichotomyStepResult ptEsResult;

    @Mock
    private DichotomyStepResult frEsResult;

    @Mock
    private CounterTradingValues counterTradingValues;

    private ParallelDichotomiesResult parallelDichotomiesResult;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        parallelDichotomiesResult = new ParallelDichotomiesResult(ptEsResult, frEsResult, counterTradingValues);
    }

    @Test
    void testGetPtEsResult() {
        assertEquals(ptEsResult, parallelDichotomiesResult.getPtEsResult());
    }

    @Test
    void testSetPtEsResult() {
        DichotomyStepResult newResult = mock(DichotomyStepResult.class);
        parallelDichotomiesResult.setPtEsResult(newResult);
        assertEquals(newResult, parallelDichotomiesResult.getPtEsResult());
    }

    @Test
    void testGetFrEsResult() {
        assertEquals(frEsResult, parallelDichotomiesResult.getFrEsResult());
    }

    @Test
    void testSetFrEsResult() {
        DichotomyStepResult newResult = mock(DichotomyStepResult.class);
        parallelDichotomiesResult.setFrEsResult(newResult);
        assertEquals(newResult, parallelDichotomiesResult.getFrEsResult());
    }

    @Test
    void testGetCounterTradingValues() {
        assertEquals(counterTradingValues, parallelDichotomiesResult.getCounterTradingValues());
    }

    @Test
    void testSetCounterTradingValues() {
        CounterTradingValues newValues = mock(CounterTradingValues.class);
        parallelDichotomiesResult.setCounterTradingValues(newValues);
        assertEquals(newValues, parallelDichotomiesResult.getCounterTradingValues());
    }

    @Test
    void testIsSecure() {
        when(ptEsResult.isSecure()).thenReturn(true);
        when(frEsResult.isSecure()).thenReturn(false);
        assertTrue(parallelDichotomiesResult.getPtEsResult().isSecure());
        assertFalse(parallelDichotomiesResult.getFrEsResult().isSecure());
    }

    @Test
    void testIsFailed() {
        when(ptEsResult.isFailed()).thenReturn(true);
        when(frEsResult.isFailed()).thenReturn(false);
        assertTrue(parallelDichotomiesResult.getPtEsResult().isFailed());
        assertFalse(parallelDichotomiesResult.getFrEsResult().isFailed());
    }
}
