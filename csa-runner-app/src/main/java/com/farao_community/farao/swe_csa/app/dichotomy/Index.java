package com.farao_community.farao.swe_csa.app.dichotomy;

import org.apache.commons.lang3.tuple.Pair;

public class Index {
    private final double ptEsMinValue;
    private final double frEsMinValue;
    private final double precision;

    private final double maxDichotomiesForPtEsBorder;
    private final double maxDichotomiesForFrEsBorder;

    private Pair<Double, DichotomyStepResult> ptEsHighestUnsecureStep;
    private Pair<Double, DichotomyStepResult> ptEsLowestSecureStep;
    private Pair<Double, DichotomyStepResult> frEsHighestUnsecureStep;
    private Pair<Double, DichotomyStepResult> frEsLowestSecureStep;
    private DichotomyStepResult bestValidDichotomyStepResult;
    private int frEsDichotomyCount = 0;
    private int ptEsDichotomyCount = 0;

    public Index(double ptEsMinValue, double frEsMinValue, double precision, double maxDichotomiesForPtEsBorder, double maxDichotomiesForFrEsBorder) {
        this.ptEsMinValue = ptEsMinValue;
        this.frEsMinValue = frEsMinValue;
        this.precision = precision;
        this.maxDichotomiesForPtEsBorder = maxDichotomiesForPtEsBorder;
        this.maxDichotomiesForFrEsBorder = maxDichotomiesForFrEsBorder;
    }

    public Pair<Double, DichotomyStepResult> getFrEsLowestSecureStep() {
        return frEsLowestSecureStep;
    }

    public Pair<Double, DichotomyStepResult> getPtEsLowestSecureStep() {
        return ptEsLowestSecureStep;
    }

    public boolean addPtEsDichotomyStepResult(double ptEsCtStepValue, DichotomyStepResult stepResult) {
        ptEsDichotomyCount++;
        if (stepResult.isPtEsCnecsSecure()) {
            ptEsLowestSecureStep = Pair.of(ptEsCtStepValue, stepResult);
            return true;
        } else {
            ptEsHighestUnsecureStep = Pair.of(ptEsCtStepValue, stepResult);
        }
        return false;
    }

    public boolean addFrEsDichotomyStepResult(double frEsCtStepValue, DichotomyStepResult stepResult) {
        frEsDichotomyCount++;
        if (stepResult.isFrEsCnecsSecure()) {
            frEsLowestSecureStep = Pair.of(frEsCtStepValue, stepResult);
            return true;
        } else {
            frEsHighestUnsecureStep = Pair.of(frEsCtStepValue, stepResult);
        }
        return false;
    }

    public boolean exitConditionIsNotMetForPtEs() {
        return ptEsLowestSecureStep.getLeft() != ptEsMinValue && !(ptEsDichotomyCount >= maxDichotomiesForPtEsBorder) && !(ptEsLowestSecureStep.getLeft() - ptEsHighestUnsecureStep.getLeft() <= precision);
    }

    public boolean exitConditionIsNotMetForFrEs() {
        return frEsLowestSecureStep.getLeft() != frEsMinValue && !(frEsDichotomyCount >= maxDichotomiesForFrEsBorder) && !(frEsLowestSecureStep.getLeft() - frEsHighestUnsecureStep.getLeft() <= precision);
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

    public void setBestValidDichotomyStepResult(DichotomyStepResult bestValidDichotomyStepResult) {
        this.bestValidDichotomyStepResult = bestValidDichotomyStepResult;
    }

    public DichotomyStepResult getBestValidDichotomyStepResult() {
        return bestValidDichotomyStepResult;
    }
}
