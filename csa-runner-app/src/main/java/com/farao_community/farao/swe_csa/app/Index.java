package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.dichotomy.api.results.ReasonInvalid;
import com.farao_community.farao.swe_csa.api.exception.CsaInternalException;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.List;

public class Index {
    private final double ptEsMinValue;
    private final double ptEsMaxValue;
    private final double frEsMinValue;
    private final double frEsMaxValue;
    private final double precision;

    private final double maxDichotomiesByBorder;
    private final List<Pair<Double, DichotomyStepResult>> ptEsStepResults = new ArrayList<>();
    private Pair<Double, DichotomyStepResult> ptEsHighestUnsecureStep;
    private Pair<Double, DichotomyStepResult> ptEsLowestSecureStep;
    private final List<Pair<Double, DichotomyStepResult>> frEsStepResults = new ArrayList<>();
    private Pair<Double, DichotomyStepResult> frEsHighestUnsecureStep;
    private Pair<Double, DichotomyStepResult> frEsLowestSecureStep;
    private int frEsDichotomyCount = 0;
    private int ptEsDichotomyCount = 0;

    public Index(double ptEsMinValue, double ptEsMaxValue, double frEsMinValue, double frEsMaxValue, double precision, double maxDichotomiesByBorder) {
        if (ptEsMinValue > ptEsMaxValue || frEsMinValue > frEsMaxValue) {
            throw new CsaInternalException("Index creation impossible, minValue is supposed to be lower than maxValue.");
        }
        this.ptEsMinValue = ptEsMinValue;
        this.ptEsMaxValue = ptEsMaxValue;
        this.frEsMinValue = frEsMinValue;
        this.frEsMaxValue = frEsMaxValue;
        this.precision = precision;
        this.maxDichotomiesByBorder = maxDichotomiesByBorder;
    }

    public Pair<Double, DichotomyStepResult> getFrEsLowestSecureStep() {
        return frEsLowestSecureStep;
    }

    public Pair<Double, DichotomyStepResult> getPtEsLowestSecureStep() {
        return ptEsLowestSecureStep;
    }

    public void addPtEsDichotomyStepResult(double ptEsCtStepValue, DichotomyStepResult stepResult) {
        ptEsDichotomyCount++;
        if (stepResult.isValid()) {
            if (ptEsLowestSecureStep != null && ptEsLowestSecureStep.getLeft() < ptEsCtStepValue) {
                throw new AssertionError("Step result tested is secure but its value is higher than lowest secure step one. Should not happen");
            }
            ptEsLowestSecureStep = Pair.of(ptEsCtStepValue, stepResult);
            ptEsStepResults.add(ptEsLowestSecureStep);
        } else {
            if (ptEsHighestUnsecureStep != null && ptEsHighestUnsecureStep.getRight().getReasonInvalid().equals(ReasonInvalid.UNSECURE_AFTER_VALIDATION)
                && ptEsHighestUnsecureStep.getLeft() > ptEsCtStepValue) {
                throw new AssertionError("Step result tested is unsecure but its value is lower than highest unsecure step one. Should not happen");
            }
            ptEsHighestUnsecureStep = Pair.of(ptEsCtStepValue, stepResult);
            ptEsStepResults.add(ptEsHighestUnsecureStep);
        }
    }

    public void addFrEsDichotomyStepResult(double frEsCtStepValue, DichotomyStepResult stepResult) {
        frEsDichotomyCount++;
        if (stepResult.isValid()) {
            if (frEsLowestSecureStep != null && frEsLowestSecureStep.getLeft() < frEsCtStepValue) {
                throw new AssertionError("Step result tested is secure but its value is higher than lowest secure step one. Should not happen");
            }
            frEsLowestSecureStep = Pair.of(frEsCtStepValue, stepResult);
            frEsStepResults.add(frEsLowestSecureStep);
        } else {
            if (frEsHighestUnsecureStep != null && frEsHighestUnsecureStep.getRight().getReasonInvalid().equals(ReasonInvalid.UNSECURE_AFTER_VALIDATION)
                && frEsHighestUnsecureStep.getLeft() > frEsCtStepValue) {
                throw new AssertionError("Step result tested is unsecure but its value is lower than highest unsecure step one. Should not happen");
            }
            frEsHighestUnsecureStep = Pair.of(frEsCtStepValue, stepResult);
            frEsStepResults.add(frEsHighestUnsecureStep);
        }
    }

    public boolean exitConditionIsNotMetForPtEs() {
        return !(ptEsDichotomyCount >= maxDichotomiesByBorder) && !(ptEsLowestSecureStep.getLeft() - ptEsHighestUnsecureStep.getLeft() <= precision) && ptEsLowestSecureStep.getLeft() != ptEsMinValue;
    }

    public boolean exitConditionIsNotMetForFrEs() {
        return !(frEsDichotomyCount >= maxDichotomiesByBorder) && !(frEsLowestSecureStep.getLeft() - frEsHighestUnsecureStep.getLeft() <= precision) && frEsLowestSecureStep.getLeft() != frEsMinValue;
    }

    public CounterTradingValues nextValues() {
        if (!exitConditionIsNotMetForFrEs() && exitConditionIsNotMetForPtEs()) {
            return new CounterTradingValues((ptEsLowestSecureStep.getLeft() + ptEsHighestUnsecureStep.getLeft()) / 2, frEsLowestSecureStep.getLeft());
        } else if (exitConditionIsNotMetForFrEs() && !exitConditionIsNotMetForPtEs()) {
            return new CounterTradingValues(ptEsLowestSecureStep.getLeft(), (frEsLowestSecureStep.getLeft() + frEsHighestUnsecureStep.getLeft()) / 2);
        } else {
            return new CounterTradingValues((ptEsLowestSecureStep.getLeft() + ptEsHighestUnsecureStep.getLeft()) / 2, (frEsLowestSecureStep.getLeft() + frEsHighestUnsecureStep.getLeft()) / 2);
        }
    }
}
