package com.farao_community.farao.swe_csa.app.dichotomy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;

class IndexTest {

    @Mock
    private DichotomyStepResult minPtEsCtResult;
    @Mock
    private DichotomyStepResult minFrEsCtResult;
    @Mock
    private DichotomyStepResult maxPtEsCtResult;
    @Mock
    private DichotomyStepResult maxFrEsCtResult;


    @Mock
    private DichotomyStepResult ptEsStepResult;
    @Mock
    private DichotomyStepResult frEsStepResult;

    private Index index;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        index = new Index(0, 0, 10, 10);
    }

    @Test
    void testAddPtEsDichotomyStepResultSecure() {
        when(ptEsStepResult.isSecure()).thenReturn(true);

        boolean result = index.addPtEsDichotomyStepResult(15.0, ptEsStepResult);

        assertTrue(result);
        assertEquals(Pair.of(15.0, ptEsStepResult), index.getPtEsLowestSecureStep());
    }

    @Test
    void testAddPtEsDichotomyStepResultUnsecure() {
        when(ptEsStepResult.isSecure()).thenReturn(false);

        boolean result = index.addPtEsDichotomyStepResult(5.0, ptEsStepResult);

        assertFalse(result);
        assertEquals(Pair.of(5.0, ptEsStepResult), index.getPtEsHighestUnsecureStep());
    }

    @Test
    void testAddFrEsDichotomyStepResultSecure() {
        when(frEsStepResult.isSecure()).thenReturn(true);

        boolean result = index.addFrEsDichotomyStepResult(18.0, frEsStepResult);

        assertTrue(result);
        assertEquals(Pair.of(18.0, frEsStepResult), index.getFrEsLowestSecureStep());
    }

    @Test
    void testAddFrEsDichotomyStepResultUnsecure() {
        when(frEsStepResult.isSecure()).thenReturn(false);

        boolean result = index.addFrEsDichotomyStepResult(12.0, frEsStepResult);

        assertFalse(result);
        assertEquals(Pair.of(12.0, frEsStepResult), index.getFrEsHighestUnsecureStep());
    }

    @Test
    void testExitConditionIsNotMetForPtEs() {
        when(minPtEsCtResult.isSecure()).thenReturn(false);
        when(minFrEsCtResult.isSecure()).thenReturn(false);
        when(maxPtEsCtResult.isSecure()).thenReturn(true);
        when(maxFrEsCtResult.isSecure()).thenReturn(true);
        index.addPtEsDichotomyStepResult(0, minPtEsCtResult);
        index.addFrEsDichotomyStepResult(0, minFrEsCtResult);
        index.addPtEsDichotomyStepResult(100, maxPtEsCtResult);
        index.addFrEsDichotomyStepResult(100, maxFrEsCtResult);
        when(ptEsStepResult.isSecure()).thenReturn(true);
        index.addPtEsDichotomyStepResult(15.0, ptEsStepResult);

        boolean result = index.exitConditionIsNotMetForPtEs();

        assertTrue(result);
    }

    @Test
    void testExitConditionIsNotMetForFrEs() {
        when(minPtEsCtResult.isSecure()).thenReturn(false);
        when(minFrEsCtResult.isSecure()).thenReturn(false);
        when(maxPtEsCtResult.isSecure()).thenReturn(true);
        when(maxFrEsCtResult.isSecure()).thenReturn(true);
        index.addPtEsDichotomyStepResult(0, minPtEsCtResult);
        index.addFrEsDichotomyStepResult(0, minFrEsCtResult);
        index.addPtEsDichotomyStepResult(100, maxPtEsCtResult);
        index.addFrEsDichotomyStepResult(100, maxFrEsCtResult);

        when(frEsStepResult.isSecure()).thenReturn(true);
        index.addFrEsDichotomyStepResult(18.0, frEsStepResult);

        boolean result = index.exitConditionIsNotMetForFrEs();

        assertTrue(result);
    }

    @Test
    void testNextValues() {
        when(minPtEsCtResult.isSecure()).thenReturn(false);
        when(minFrEsCtResult.isSecure()).thenReturn(false);
        when(maxPtEsCtResult.isSecure()).thenReturn(true);
        when(maxFrEsCtResult.isSecure()).thenReturn(true);
        index.addPtEsDichotomyStepResult(0, minPtEsCtResult);
        index.addFrEsDichotomyStepResult(0, minFrEsCtResult);
        index.addPtEsDichotomyStepResult(100, maxPtEsCtResult);
        index.addFrEsDichotomyStepResult(100, maxFrEsCtResult);

        when(ptEsStepResult.isSecure()).thenReturn(true);
        when(frEsStepResult.isSecure()).thenReturn(false);

        index.addPtEsDichotomyStepResult(15.0, ptEsStepResult);
        index.addFrEsDichotomyStepResult(50, frEsStepResult);

        CounterTradingValues result = index.nextValues();

        assertNotNull(result);
        assertEquals(7.5, result.ptEsCt());
        assertEquals(75, result.frEsCt());
    }
}
