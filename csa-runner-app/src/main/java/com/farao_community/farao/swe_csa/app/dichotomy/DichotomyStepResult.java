package com.farao_community.farao.swe_csa.app.dichotomy;

import com.farao_community.farao.dichotomy.api.results.ReasonInvalid;
import com.farao_community.farao.rao_runner.api.resource.RaoResponse;
import com.powsybl.openrao.data.raoresultapi.RaoResult;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.tuple.Pair;

public final class DichotomyStepResult {
    private final Pair<String, Double> ptEsMostLimitingCnec;
    private final Pair<String, Double> frEsMostLimitingCnec;
    private final RaoResult raoResult;
    private final RaoResponse raoResponse;
    private final ReasonInvalid reasonInvalid;
    private final String failureMessage;
    private final CounterTradingValues counterTradingValues;

    private DichotomyStepResult(ReasonInvalid reasonInvalid, String failureMessage, CounterTradingValues counterTradingValues) {
        this.ptEsMostLimitingCnec = null;
        this.frEsMostLimitingCnec = null;
        this.raoResult = null;
        this.raoResponse = null;
        this.counterTradingValues = counterTradingValues;
        this.reasonInvalid = reasonInvalid;
        this.failureMessage = failureMessage;
    }

    private DichotomyStepResult(RaoResult raoResult, RaoResponse raoResponse, Pair<String, Double> ptEsMostLimitingCnec, Pair<String, Double> frEsMostLimitingCnec, CounterTradingValues counterTradingValues) {
        this.raoResult = raoResult;
        this.raoResponse = raoResponse;
        this.ptEsMostLimitingCnec = ptEsMostLimitingCnec;
        this.frEsMostLimitingCnec = frEsMostLimitingCnec;
        this.reasonInvalid = ptEsMostLimitingCnec.getRight() >= 0 && frEsMostLimitingCnec.getRight() >= 0 ? ReasonInvalid.NONE : ReasonInvalid.UNSECURE_AFTER_VALIDATION;
        this.counterTradingValues = counterTradingValues;
        this.failureMessage = "None";
    }

    public static DichotomyStepResult fromFailure(ReasonInvalid reasonInvalid, String failureMessage, CounterTradingValues counterTradingValues) {
        return new DichotomyStepResult(reasonInvalid, failureMessage, counterTradingValues);
    }

    public static DichotomyStepResult fromNetworkValidationResult(RaoResult raoResult, RaoResponse raoResponse, Pair<String, Double> ptEsMostLimitingCnec, Pair<String, Double> frEsMostLimitingCnec, CounterTradingValues counterTradingValues) {
        return new DichotomyStepResult(raoResult, raoResponse, ptEsMostLimitingCnec, frEsMostLimitingCnec, counterTradingValues);
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
        return isPtEsCnecsSecure() && isFrEsCnecsSecure();
    }

    public ReasonInvalid getReasonInvalid() {
        return this.reasonInvalid;
    }

    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    public boolean isPtEsCnecsSecure() {
        return this.ptEsMostLimitingCnec != null && this.ptEsMostLimitingCnec.getRight() >= 0;
    }

    public boolean isFrEsCnecsSecure() {
        return this.frEsMostLimitingCnec != null && this.frEsMostLimitingCnec.getRight() >= 0;
    }

    public CounterTradingValues getCounterTradingValues() {
        return counterTradingValues;
    }

    public Pair<String, Double> getPtEsMostLimitingCnec() {
        return ptEsMostLimitingCnec;
    }

    public Pair<String, Double> getFrEsMostLimitingCnec() {
        return frEsMostLimitingCnec;
    }
}

