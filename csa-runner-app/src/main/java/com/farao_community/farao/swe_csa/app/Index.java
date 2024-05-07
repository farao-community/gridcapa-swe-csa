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
    private DichotomyStepResult bestValidDichotomyStepResult;
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

    public boolean addPtEsDichotomyStepResult(double ptEsCtStepValue, DichotomyStepResult stepResult) {
        ptEsDichotomyCount++;
        if (stepResult.isPtEsCnecsSecure()) {
            ptEsLowestSecureStep = Pair.of(ptEsCtStepValue, stepResult);
            ptEsStepResults.add(ptEsLowestSecureStep);
            return true;
        } else {
            ptEsHighestUnsecureStep = Pair.of(ptEsCtStepValue, stepResult);
            ptEsStepResults.add(ptEsHighestUnsecureStep);
        }
        return false;
    }

    public boolean addFrEsDichotomyStepResult(double frEsCtStepValue, DichotomyStepResult stepResult) {
        frEsDichotomyCount++;
        if (stepResult.isFrEsCnecsSecure()) {
            frEsLowestSecureStep = Pair.of(frEsCtStepValue, stepResult);
            frEsStepResults.add(frEsLowestSecureStep);
            return true;
        } else {
            frEsHighestUnsecureStep = Pair.of(frEsCtStepValue, stepResult);
            frEsStepResults.add(frEsHighestUnsecureStep);
        }
        return false;
    }

    public boolean exitConditionIsNotMetForPtEs() {
        return ptEsLowestSecureStep.getLeft() != ptEsMinValue && !(ptEsDichotomyCount >= maxDichotomiesByBorder) && !(ptEsLowestSecureStep.getLeft() - ptEsHighestUnsecureStep.getLeft() <= precision);
    }

    public boolean exitConditionIsNotMetForFrEs() {
        return frEsLowestSecureStep.getLeft() != frEsMinValue && !(frEsDichotomyCount >= maxDichotomiesByBorder) && !(frEsLowestSecureStep.getLeft() - frEsHighestUnsecureStep.getLeft() <= precision);
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
