package com.farao_community.farao.swe_csa.app.dichotomy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


import com.farao_community.farao.swe_csa.api.resource.Status;
import com.powsybl.openrao.data.raoresult.api.RaoResult;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class FinalResultTest {

    @Mock
    private DichotomyStepResult ptEsDichotomyStepResult;

    @Mock
    private DichotomyStepResult frEsDichotomyStepResult;

    @Mock
    private RaoResult raoResultPtEs;

    @Mock
    private RaoResult raoResultFrEs;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testFromDichotomyStepResultsSecure() {
        when(ptEsDichotomyStepResult.getRaoResult()).thenReturn(raoResultPtEs);
        when(raoResultPtEs.isSecure()).thenReturn(true);
        when(frEsDichotomyStepResult.getRaoResult()).thenReturn(raoResultFrEs);
        when(raoResultFrEs.isSecure()).thenReturn(true);

        FinalResult result = FinalResult.fromDichotomyStepResults(ptEsDichotomyStepResult, frEsDichotomyStepResult);

        assertNotNull(result);
        assertTrue(result.ptEsResult().getLeft().isSecure());
        assertTrue(result.frEsResult().getLeft().isSecure());
    }

    @Test
    void testFromDichotomyStepResultsUnsecure() {
        when(ptEsDichotomyStepResult.getRaoResult()).thenReturn(raoResultPtEs);
        when(raoResultPtEs.isSecure()).thenReturn(false);
        when(frEsDichotomyStepResult.getRaoResult()).thenReturn(raoResultFrEs);
        when(raoResultFrEs.isSecure()).thenReturn(false);

        FinalResult result = FinalResult.fromDichotomyStepResults(ptEsDichotomyStepResult, frEsDichotomyStepResult);

        assertNotNull(result);
        assertFalse(result.ptEsResult().getLeft().isSecure());
        assertFalse(result.frEsResult().getLeft().isSecure());
    }

    @Test
    void testFromDichotomyStepResultsNullStatus() {
        when(ptEsDichotomyStepResult.getRaoResult()).thenReturn(raoResultPtEs);
        when(raoResultPtEs.isSecure()).thenReturn(true);
        when(frEsDichotomyStepResult.getRaoResult()).thenReturn(raoResultFrEs);
        when(raoResultFrEs.isSecure()).thenReturn(false);

        FinalResult result = FinalResult.fromDichotomyStepResults(ptEsDichotomyStepResult, frEsDichotomyStepResult);

        assertNotNull(result);
        assertTrue(result.ptEsResult().getLeft().isSecure());
        assertFalse(result.frEsResult().getLeft().isSecure());
    }

    @Test
    void testFromDichotomyStepResultsEmptyDichotomyStepResult() {
        RaoResult mockRaoResult = mock(RaoResult.class);
        when(ptEsDichotomyStepResult.getRaoResult()).thenReturn(mockRaoResult);
        when(frEsDichotomyStepResult.getRaoResult()).thenReturn(mockRaoResult);
        when(mockRaoResult.isSecure()).thenReturn(false);

        FinalResult result = FinalResult.fromDichotomyStepResults(ptEsDichotomyStepResult, frEsDichotomyStepResult);

        assertNotNull(result);
        assertNotNull(result.ptEsResult().getLeft());
        assertNotNull(result.frEsResult().getLeft());
        assertEquals(Status.FINISHED_UNSECURE, result.ptEsResult().getRight());
        assertEquals(Status.FINISHED_UNSECURE, result.frEsResult().getRight());
    }
}