package com.farao_community.farao.swe_csa.app.dichotomy;

import com.farao_community.farao.swe_csa.api.resource.Status;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import kotlin.Pair;

public class ParallelDichotomyResult {

    private Pair<RaoResult, Status> ptEsResult;
    private Pair<RaoResult, Status> frEsResult;
    private CounterTradingValues counterTradingValues;

    public ParallelDichotomyResult(Pair<RaoResult, Status> ptEsResult, Pair<RaoResult, Status> frEsResult, CounterTradingValues counterTradingValues) {
        this.ptEsResult = ptEsResult;
        this.frEsResult = frEsResult;
        this.counterTradingValues = counterTradingValues;
    }

    public Pair<RaoResult, Status> getPtEsResult() {
        return ptEsResult;
    }

    public void setPtEsResult(Pair<RaoResult, Status> ptEsResult) {
        this.ptEsResult = ptEsResult;
    }

    public Pair<RaoResult, Status> getFrEsResult() {
        return frEsResult;
    }

    public void setFrEsResult(Pair<RaoResult, Status> frEsResult) {
        this.frEsResult = frEsResult;
    }

    public CounterTradingValues getCounterTradingValues() {
        return counterTradingValues;
    }

    public void setCounterTradingValues(CounterTradingValues counterTradingValues) {
        this.counterTradingValues = counterTradingValues;
    }
}
