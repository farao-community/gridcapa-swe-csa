package com.farao_community.farao.swe_csa.app.dichotomy;

import com.farao_community.farao.dichotomy.api.results.ReasonInvalid;
import com.farao_community.farao.rao_runner.api.resource.RaoSuccessResponse;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import org.apache.commons.lang3.builder.ToStringBuilder;

public final class DichotomyStepResult {
    private final RaoResult raoResult;
    private final boolean isSecure;
    private final RaoSuccessResponse raoSuccessResponse;
    private final ReasonInvalid reasonInvalid;
    private final String failureMessage;
    private final CounterTradingValues counterTradingValues;

    private DichotomyStepResult(boolean isSecure, ReasonInvalid reasonInvalid, String failureMessage, CounterTradingValues counterTradingValues) {
        this.isSecure = isSecure;
        this.raoResult = null;
        this.raoSuccessResponse = null;
        this.counterTradingValues = counterTradingValues;
        this.reasonInvalid = reasonInvalid;
        this.failureMessage = failureMessage;
    }

    private DichotomyStepResult(RaoResult raoResult, boolean isSecure, RaoSuccessResponse raoSuccessResponse, CounterTradingValues counterTradingValues) {
        this.raoResult = raoResult;
        this.isSecure = isSecure;
        this.raoSuccessResponse = raoSuccessResponse;
        this.reasonInvalid = isSecure ? ReasonInvalid.NONE : ReasonInvalid.UNSECURE_AFTER_VALIDATION;
        this.counterTradingValues = counterTradingValues;
        this.failureMessage = "None";
    }

    public static DichotomyStepResult fromFailure(ReasonInvalid reasonInvalid, String failureMessage, CounterTradingValues counterTradingValues) {
        return new DichotomyStepResult(false, reasonInvalid, failureMessage, counterTradingValues);
    }

    public static DichotomyStepResult fromNetworkValidationResult(RaoResult raoResult, boolean isSecure, RaoSuccessResponse raoResponse, CounterTradingValues counterTradingValues) {
        return new DichotomyStepResult(raoResult, isSecure, raoResponse, counterTradingValues);
    }

    public RaoResult getRaoResult() {
        return this.raoResult;
    }

    public RaoSuccessResponse getRaoSuccessResponse() {
        return this.raoSuccessResponse;
    }

    public boolean isFailed() {
        return this.reasonInvalid == ReasonInvalid.GLSK_LIMITATION || this.reasonInvalid == ReasonInvalid.VALIDATION_FAILED;
    }

    public String getFailureMessage() {
        return this.failureMessage;
    }

    public ReasonInvalid getReasonInvalid() {
        return this.reasonInvalid;
    }

    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    public CounterTradingValues getCounterTradingValues() {
        return counterTradingValues;
    }

    public boolean isSecure() {
        return isSecure;
    }
}

