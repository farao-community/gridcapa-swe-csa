package com.farao_community.farao.swe_csa.app.dichotomy;

import com.farao_community.farao.dichotomy.api.results.ReasonInvalid;
import com.farao_community.farao.rao_runner.api.resource.RaoResponse;
import com.powsybl.openrao.data.raoresultapi.RaoResult;
import org.apache.commons.lang3.builder.ToStringBuilder;

public final class DichotomyStepResult {
    private final boolean ptEsCnecsSecure;
    private final boolean frEsCnecsSecure;
    private final RaoResult raoResult;
    private final RaoResponse raoResponse;
    private final ReasonInvalid reasonInvalid;
    private final String failureMessage;
    private final CounterTradingValues counterTradingValues;

    private DichotomyStepResult(ReasonInvalid reasonInvalid, String failureMessage, boolean fromPtEsFailure, boolean fromFrEsFailure, CounterTradingValues counterTradingValues) {
        this.ptEsCnecsSecure = !fromPtEsFailure;
        this.frEsCnecsSecure = !fromFrEsFailure;
        this.raoResult = null;
        this.raoResponse = null;
        this.counterTradingValues = counterTradingValues;
        this.reasonInvalid = reasonInvalid;
        this.failureMessage = failureMessage;
    }

    private DichotomyStepResult(RaoResult raoResult, RaoResponse raoResponse, boolean ptEsCnecsSecure, boolean frEsCnecsSecure, CounterTradingValues counterTradingValues) {
        this.raoResult = raoResult;
        this.raoResponse = raoResponse;
        this.ptEsCnecsSecure = ptEsCnecsSecure;
        this.frEsCnecsSecure = frEsCnecsSecure;
        this.reasonInvalid = ptEsCnecsSecure && frEsCnecsSecure ? ReasonInvalid.NONE : ReasonInvalid.UNSECURE_AFTER_VALIDATION;
        this.counterTradingValues = counterTradingValues;
        this.failureMessage = "None";
    }

    public static DichotomyStepResult fromFailure(ReasonInvalid reasonInvalid, String failureMessage, boolean ptEsCnecsSecure, boolean frEsCnecsSecure, CounterTradingValues counterTradingValues) {
        return new DichotomyStepResult(reasonInvalid, failureMessage, ptEsCnecsSecure, frEsCnecsSecure, counterTradingValues);
    }

    public static DichotomyStepResult fromNetworkValidationResult(RaoResult raoResult, RaoResponse raoResponse, boolean ptEsCnecsSecure, boolean frEsCnecsSecure, CounterTradingValues counterTradingValues) {
        return new DichotomyStepResult(raoResult, raoResponse, ptEsCnecsSecure, frEsCnecsSecure, counterTradingValues);
    }

    public RaoResult getRaoResult() {
        return this.raoResult;
    }

    public RaoResponse getRaoResponse() {
        return this.raoResponse;
    }

    public boolean isFailed() {
        return this.reasonInvalid == ReasonInvalid.GLSK_LIMITATION || this.reasonInvalid == ReasonInvalid.VALIDATION_FAILED;
    }

    public String getFailureMessage() {
        return this.failureMessage;
    }

    public boolean isValid() {
        return this.frEsCnecsSecure && this.ptEsCnecsSecure;
    }

    public ReasonInvalid getReasonInvalid() {
        return this.reasonInvalid;
    }

    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    public boolean isPtEsCnecsSecure() {
        return ptEsCnecsSecure;
    }

    public boolean isFrEsCnecsSecure() {
        return frEsCnecsSecure;
    }

    public CounterTradingValues getCounterTradingValues() {
        return counterTradingValues;
    }
}
