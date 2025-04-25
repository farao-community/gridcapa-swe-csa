package com.farao_community.farao.swe_csa.app.dichotomy;

import com.farao_community.farao.swe_csa.api.resource.Status;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import org.apache.commons.lang3.tuple.Pair;

public class FinalResult {

    Pair<RaoResult, Status> ptEsResult;
    Pair<RaoResult, Status> frEsResult;

    public FinalResult(Pair<RaoResult, Status> ptEsResult, Pair<RaoResult, Status> frEsResult) {
        this.ptEsResult = ptEsResult;
        this.frEsResult = frEsResult;
    }

    public static FinalResult fromDichotomyStepResults(DichotomyStepResult ptEsDichotomyStepResult, DichotomyStepResult frEsDichotomyStepResult) {
        RaoResult raoResultPtEs = ptEsDichotomyStepResult.getRaoResult();
        Status ptEsStatus = ptEsDichotomyStepResult.isSecure() ? Status.FINISHED_SECURE : Status.FINISHED_UNSECURE;
        RaoResult raoResultFrEs = frEsDichotomyStepResult.getRaoResult();
        Status frEsStatus = frEsDichotomyStepResult.isSecure() ? Status.FINISHED_SECURE : Status.FINISHED_UNSECURE;
        return new FinalResult(Pair.of(raoResultPtEs, ptEsStatus), Pair.of(raoResultFrEs, frEsStatus));
    }

    public Pair<RaoResult, Status> getPtEsResult() {
        return ptEsResult;
    }

    public Pair<RaoResult, Status> getFrEsResult() {
        return frEsResult;
    }
}
