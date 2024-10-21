package com.farao_community.farao.swe_csa.app.dichotomy;

import com.farao_community.farao.dichotomy.api.results.ReasonInvalid;
import com.farao_community.farao.rao_runner.api.resource.RaoResponse;
import com.powsybl.openrao.data.raoresultapi.RaoResult;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DichotomyStepResultTest {

    @Test
    void creationFromFailureTest() {
        DichotomyStepResult dichotomyStepResult = DichotomyStepResult.fromFailure(ReasonInvalid.GLSK_LIMITATION, "failureMessage", new CounterTradingValues(1500.0, 900.0));

        assertNull(dichotomyStepResult.getFrEsMostLimitingCnec());
        assertNull(dichotomyStepResult.getPtEsMostLimitingCnec());
        assertNull(dichotomyStepResult.getRaoResult());
        assertNull(dichotomyStepResult.getRaoResponse());
        assertEquals("failureMessage", dichotomyStepResult.getFailureMessage());
        assertEquals(ReasonInvalid.GLSK_LIMITATION, dichotomyStepResult.getReasonInvalid());
        assertEquals(900.0, dichotomyStepResult.getCounterTradingValues().frEsCt);
        assertEquals(1500.0, dichotomyStepResult.getCounterTradingValues().ptEsCt);
        assertTrue(dichotomyStepResult.isFailed());
        assertFalse(dichotomyStepResult.isValid());
        assertFalse(dichotomyStepResult.isFrEsCnecsSecure());
        assertFalse(dichotomyStepResult.isPtEsCnecsSecure());
    }

    @Test
    void creationFromNetworkValidationResultTest() {
        DichotomyStepResult dichotomyStepResult = DichotomyStepResult.fromNetworkValidationResult(Mockito.mock(RaoResult.class),
            Mockito.mock(RaoResponse.class), Pair.of("idPtEsCnec", 200.0), Pair.of("idFrEsCnec", -50.0), new CounterTradingValues(1500.0, 900.0));

        assertNotNull(dichotomyStepResult.getFrEsMostLimitingCnec());
        assertNotNull(dichotomyStepResult.getPtEsMostLimitingCnec());
        assertNotNull(dichotomyStepResult.getRaoResult());
        assertNotNull(dichotomyStepResult.getRaoResponse());
        assertEquals(ReasonInvalid.UNSECURE_AFTER_VALIDATION, dichotomyStepResult.getReasonInvalid());
        assertEquals(900.0, dichotomyStepResult.getCounterTradingValues().frEsCt);
        assertEquals(1500.0, dichotomyStepResult.getCounterTradingValues().ptEsCt);
        assertFalse(dichotomyStepResult.isFailed());
        assertFalse(dichotomyStepResult.isValid());
        assertFalse(dichotomyStepResult.isFrEsCnecsSecure());
        assertTrue(dichotomyStepResult.isPtEsCnecsSecure());
    }
}
