package com.farao_community.farao.swe_csa.app.dichotomy;

import org.apache.commons.lang3.tuple.Pair;

import java.util.Map;

import static com.farao_community.farao.swe_csa.app.dichotomy.DichotomyRunner.ES_FR;
import static com.farao_community.farao.swe_csa.app.dichotomy.DichotomyRunner.ES_PT;
import static java.lang.Math.signum;

public class Index {
    private final double ptEsMinValue;
    private final double frEsMinValue;
    private final double precision;

    private final double maxDichotomiesByBorder;
    private Pair<Double, DichotomyStepResult> ptEsHighestUnsecureStep;
    private Pair<Double, DichotomyStepResult> ptEsLowestSecureStep;
    private Pair<Double, DichotomyStepResult> frEsHighestUnsecureStep;
    private Pair<Double, DichotomyStepResult> frEsLowestSecureStep;
    private ParallelDichotomiesResult bestValidDichotomyStepResult;
    private int frEsDichotomyCount = 0;
    private int ptEsDichotomyCount = 0;
    private final Map<String, Double> initialExchanges;

    public Index(double ptEsMinValue, double frEsMinValue, double precision, double maxDichotomiesByBorder, Map<String, Double> initialExchanges) {
        this.ptEsMinValue = ptEsMinValue;
        this.frEsMinValue = frEsMinValue;
        this.precision = precision;
        this.maxDichotomiesByBorder = maxDichotomiesByBorder;
        this.initialExchanges = initialExchanges;
    }

    public Pair<Double, DichotomyStepResult> getFrEsHighestUnsecureStep() {
        return frEsHighestUnsecureStep;
    }

    public Pair<Double, DichotomyStepResult> getPtEsHighestUnsecureStep() {
        return ptEsHighestUnsecureStep;
    }

    public Pair<Double, DichotomyStepResult> getFrEsLowestSecureStep() {
        return frEsLowestSecureStep;
    }

    public Double getSignedFrEsLowestSecureStepValue() {
        return -frEsLowestSecureStep.getLeft() * signum(initialExchanges.get(ES_FR));
    }

    public Pair<Double, DichotomyStepResult> getPtEsLowestSecureStep() {
        return ptEsLowestSecureStep;
    }

    public Double getSignedPtEsLowestSecureStepValue() {
        return -ptEsLowestSecureStep.getLeft() * signum(initialExchanges.get(ES_PT));
    }

    public boolean addPtEsDichotomyStepResult(double ptEsCtStepValue, DichotomyStepResult stepResult) {
        ptEsDichotomyCount++;
        if (stepResult.isSecure()) {
            ptEsLowestSecureStep = Pair.of(ptEsCtStepValue, stepResult);
            return true;
        } else {
            ptEsHighestUnsecureStep = Pair.of(ptEsCtStepValue, stepResult);
            return false;
        }
    }

    public boolean addFrEsDichotomyStepResult(double frEsCtStepValue, DichotomyStepResult stepResult) {
        frEsDichotomyCount++;
        if (stepResult.isSecure()) {
            frEsLowestSecureStep = Pair.of(frEsCtStepValue, stepResult);
            return true;
        } else {
            frEsHighestUnsecureStep = Pair.of(frEsCtStepValue, stepResult);
            return false;
        }
    }

    public boolean exitConditionIsNotMetForPtEs() {
        return ptEsLowestSecureStep.getLeft() != ptEsMinValue && ptEsDichotomyCount < maxDichotomiesByBorder && ptEsLowestSecureStep.getLeft() - ptEsHighestUnsecureStep.getLeft() > precision;
    }

    public boolean exitConditionIsNotMetForFrEs() {
        return frEsLowestSecureStep.getLeft() != frEsMinValue && frEsDichotomyCount < maxDichotomiesByBorder && frEsLowestSecureStep.getLeft() - frEsHighestUnsecureStep.getLeft() > precision;
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

    public void setBestValidDichotomyStepResult(ParallelDichotomiesResult bestValidDichotomyStepResult) {
        this.bestValidDichotomyStepResult = bestValidDichotomyStepResult;
    }

    public ParallelDichotomiesResult getBestValidDichotomyStepResult() {
        return bestValidDichotomyStepResult;
    }
}
