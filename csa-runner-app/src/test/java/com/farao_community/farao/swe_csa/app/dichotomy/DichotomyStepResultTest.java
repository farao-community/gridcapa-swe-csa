package com.farao_community.farao.swe_csa.app.dichotomy;

import com.farao_community.farao.dichotomy.api.results.ReasonInvalid;
import com.farao_community.farao.rao_runner.api.resource.RaoSuccessResponse;
import com.powsybl.openrao.commons.PhysicalParameter;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DichotomyStepResultTest {

    @Mock
    private RaoResult raoResult;

    @Mock
    private RaoSuccessResponse raoSuccessResponse;

    @Mock
    private CounterTradingValues counterTradingValues;
    private DichotomyStepResult failureResult;
    private DichotomyStepResult successResult;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Setup failure result
        failureResult = DichotomyStepResult.fromFailure(ReasonInvalid.VALIDATION_FAILED, "Validation failed", counterTradingValues);

        // Setup success result
        successResult = DichotomyStepResult.fromNetworkValidationResult(raoResult, true, raoSuccessResponse, counterTradingValues);
    }

    @Test
    void creationFromFailureTest() {
        DichotomyStepResult dichotomyStepResult = DichotomyStepResult.fromFailure(ReasonInvalid.GLSK_LIMITATION, "failureMessage", new CounterTradingValues(1500.0, 900.0));

        assertNull(dichotomyStepResult.getRaoResult());
        assertNull(dichotomyStepResult.getRaoSuccessResponse());
        assertEquals("failureMessage", dichotomyStepResult.getFailureMessage());
        assertEquals(ReasonInvalid.GLSK_LIMITATION, dichotomyStepResult.getReasonInvalid());
        assertEquals(900.0, dichotomyStepResult.getCounterTradingValues().frEsCt());
        assertEquals(1500.0, dichotomyStepResult.getCounterTradingValues().ptEsCt());
        assertTrue(dichotomyStepResult.isFailed());
    }

    @Test
    void creationFromNetworkValidationResultTest() {
        DichotomyStepResult dichotomyStepResult = DichotomyStepResult.fromNetworkValidationResult(Mockito.mock(RaoResult.class), true,
            Mockito.mock(RaoSuccessResponse.class), new CounterTradingValues(1500.0, 900.0));

        assertNotNull(dichotomyStepResult.getRaoResult());
        assertNotNull(dichotomyStepResult.getRaoSuccessResponse());
        assertEquals(ReasonInvalid.UNSECURE_AFTER_VALIDATION, dichotomyStepResult.getReasonInvalid());
        assertEquals(900.0, dichotomyStepResult.getCounterTradingValues().frEsCt());
        assertEquals(1500.0, dichotomyStepResult.getCounterTradingValues().ptEsCt());
        assertFalse(dichotomyStepResult.isFailed());
        assertFalse(dichotomyStepResult.getRaoResult().isSecure(PhysicalParameter.FLOW));
    }

    @Test
    void testGetRaoResult() {
        assertEquals(raoResult, successResult.getRaoResult());
    }

    @Test
    void testGetRaoSuccessResponse() {
        assertEquals(raoSuccessResponse, successResult.getRaoSuccessResponse());
    }

    @Test
    void testGetCounterTradingValues() {
        assertEquals(counterTradingValues, successResult.getCounterTradingValues());
    }

    @Test
    void testIsFailed() {
        assertTrue(failureResult.isFailed());
        assertFalse(successResult.isFailed());
    }

    @Test
    void testToString() {
        assertNotNull(successResult.toString());
        assertNotNull(failureResult.toString());
    }
}
