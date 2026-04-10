package com.farao_community.farao.swe_csa.app.dichotomy;

import com.farao_community.farao.dichotomy.api.results.ReasonInvalid;
import com.farao_community.farao.rao_runner.api.resource.RaoSuccessResponse;
import com.farao_community.farao.swe_csa.api.exception.CsaInternalException;
import com.powsybl.openrao.data.crac.api.cnec.Cnec;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import com.powsybl.openrao.monitoring.results.MonitoringResult;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.slf4j.MDC;

public final class DichotomyStepResult {
    private final RaoResult raoResult;
    private final boolean isSecure;
    private final RaoSuccessResponse raoSuccessResponse;
    private final ReasonInvalid reasonInvalid;
    private final String failureMessage;
    private final CounterTradingValues counterTradingValues;
    private final MonitoringResult flowMonitoringResult;
    private final MonitoringResult angleMonitoringResult;
    private final MonitoringResult voltageMonitoringResult;

    private DichotomyStepResult(boolean isSecure, ReasonInvalid reasonInvalid, String failureMessage, CounterTradingValues counterTradingValues) {
        this.isSecure = isSecure;
        this.raoResult = null;
        this.raoSuccessResponse = null;
        this.counterTradingValues = counterTradingValues;
        this.reasonInvalid = reasonInvalid;
        this.failureMessage = failureMessage;
        this.flowMonitoringResult = null;
        this.angleMonitoringResult = null;
        this.voltageMonitoringResult = null;
    }

    private DichotomyStepResult(RaoResult raoResult, boolean isSecure, RaoSuccessResponse raoSuccessResponse, CounterTradingValues counterTradingValues) {
        this.raoResult = raoResult;
        this.isSecure = isSecure;
        this.raoSuccessResponse = raoSuccessResponse;
        this.reasonInvalid = isSecure ? ReasonInvalid.NONE : ReasonInvalid.UNSECURE_AFTER_VALIDATION;
        this.counterTradingValues = counterTradingValues;
        this.failureMessage = "None";
        this.flowMonitoringResult = null;
        this.angleMonitoringResult = null;
        this.voltageMonitoringResult = null;
    }

    private DichotomyStepResult(RaoResult raoResult, boolean isSecure, RaoSuccessResponse raoSuccessResponse, CounterTradingValues counterTradingValues,
                                MonitoringResult flowMonitoringResult, MonitoringResult angleMonitoringResult, MonitoringResult voltageMonitoringResult) {
        this.raoResult = raoResult;
        this.isSecure = isSecure;
        this.raoSuccessResponse = raoSuccessResponse;
        this.reasonInvalid = isSecure ? ReasonInvalid.NONE : ReasonInvalid.UNSECURE_AFTER_VALIDATION;
        this.counterTradingValues = counterTradingValues;
        this.failureMessage = "None";
        this.flowMonitoringResult = flowMonitoringResult;
        this.angleMonitoringResult = angleMonitoringResult;
        this.voltageMonitoringResult = voltageMonitoringResult;
    }

    public static DichotomyStepResult fromFailure(ReasonInvalid reasonInvalid, String failureMessage, CounterTradingValues counterTradingValues) {
        return new DichotomyStepResult(false, reasonInvalid, failureMessage, counterTradingValues);
    }

    public static DichotomyStepResult fromNetworkValidationResult(RaoResult raoResult, boolean isSecure, RaoSuccessResponse raoResponse, CounterTradingValues counterTradingValues) {
        return new DichotomyStepResult(raoResult, isSecure, raoResponse, counterTradingValues);
    }

    public static DichotomyStepResult fromNetworkValidationWithMonitoringResult(RaoResult raoResult, boolean isSecure, RaoSuccessResponse raoResponse, CounterTradingValues counterTradingValues,
                                                                                MonitoringResult flowMonitoringResult, MonitoringResult angleMonitoringResult, MonitoringResult voltageMonitoringResult) {
        return new DichotomyStepResult(raoResult, isSecure, raoResponse, counterTradingValues, flowMonitoringResult, angleMonitoringResult, voltageMonitoringResult);
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

    public MonitoringResult getFlowMonitoringResult() {
        if (flowMonitoringResult == null) {
            throw new CsaInternalException(MDC.get("gridcapaTaskId"), "Flow monitoring has not been performed. Flow monitoring results are not available.");
        }
        return flowMonitoringResult;
    }

    public boolean isFlowSecure() {
        return this.getFlowMonitoringResult().getStatus().equals(Cnec.SecurityStatus.SECURE);
    }
}

