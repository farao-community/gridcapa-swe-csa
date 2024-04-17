package com.farao_community.farao.swe_csa.app.dichotomy;

import com.farao_community.farao.dichotomy.api.results.ReasonInvalid;
import com.farao_community.farao.rao_runner.api.resource.RaoResponse;
import com.powsybl.openrao.data.raoresultapi.RaoResult;
import org.apache.commons.lang3.builder.ToStringBuilder;

public final class DichotomyStepResult {
    private final boolean raoSecure;

    private final boolean ptEsCnecsSecure;
    private final boolean frEsCnecsSecure;
    private final RaoResult raoResult;
    private final RaoResponse raoResponse;
    private final ReasonInvalid reasonInvalid;
    private final String failureMessage;

    private DichotomyStepResult(ReasonInvalid reasonInvalid, String failureMessage) {
        this.raoSecure = false;
        this.ptEsCnecsSecure = false;
        this.frEsCnecsSecure = false;
        this.raoResult = null;
        this.raoResponse = null;
        this.reasonInvalid = reasonInvalid;
        this.failureMessage = failureMessage;
    }

    private DichotomyStepResult(RaoResult raoResult, RaoResponse raoResponse, boolean raoSecure, boolean ptEsCnecsSecure, boolean frEsCnecsSecure) {
        this.raoResult = raoResult;
        this.raoResponse = raoResponse;
        this.raoSecure = raoSecure;
        this.ptEsCnecsSecure = ptEsCnecsSecure;
        this.frEsCnecsSecure = frEsCnecsSecure;
        this.reasonInvalid = this.raoSecure ? ReasonInvalid.NONE : ReasonInvalid.UNSECURE_AFTER_VALIDATION;
        this.failureMessage = "None";
    }

    public static DichotomyStepResult fromFailure(ReasonInvalid reasonInvalid, String failureMessage) {
        return new DichotomyStepResult(reasonInvalid, failureMessage);
    }

    public static DichotomyStepResult fromNetworkValidationResult(RaoResult raoResult, RaoResponse raoResponse, boolean raoSecure, boolean ptEsCnecsSecure, boolean frEsCnecsSecure) {
        return new DichotomyStepResult(raoResult, raoResponse, raoSecure, ptEsCnecsSecure, frEsCnecsSecure);
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
        return this.raoSecure && this.frEsCnecsSecure && this.ptEsCnecsSecure;
    }

    public ReasonInvalid getReasonInvalid() {
        return this.reasonInvalid;
    }

    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    public boolean isRaoSecure() {
        return raoSecure;
    }

    public boolean isPtEsCnecsSecure() {
        return ptEsCnecsSecure;
    }

    public boolean isFrEsCnecsSecure() {
        return frEsCnecsSecure;
    }
}

