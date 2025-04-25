package com.farao_community.farao.swe_csa.app.dichotomy;

public class ParallelDichotomiesResult {

    private DichotomyStepResult ptEsResult;
    private DichotomyStepResult frEsResult;
    private CounterTradingValues counterTradingValues;

    public ParallelDichotomiesResult(DichotomyStepResult ptEsResult, DichotomyStepResult frEsResult, CounterTradingValues counterTradingValues) {
        this.ptEsResult = ptEsResult;
        this.frEsResult = frEsResult;
        this.counterTradingValues = counterTradingValues;
    }

    public DichotomyStepResult getPtEsResult() {
        return ptEsResult;
    }

    public void setPtEsResult(DichotomyStepResult ptEsResult) {
        this.ptEsResult = ptEsResult;
    }

    public DichotomyStepResult getFrEsResult() {
        return frEsResult;
    }

    public void setFrEsResult(DichotomyStepResult frEsResult) {
        this.frEsResult = frEsResult;
    }

    public CounterTradingValues getCounterTradingValues() {
        return counterTradingValues;
    }

    public void setCounterTradingValues(CounterTradingValues counterTradingValues) {
        this.counterTradingValues = counterTradingValues;
    }
}
