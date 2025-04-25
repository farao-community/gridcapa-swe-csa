package com.farao_community.farao.swe_csa.app.dichotomy;

import com.farao_community.farao.swe_csa.api.exception.CsaInternalException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public final class ParallelDichotomies {

    private ParallelDichotomies() {
        // shouldn't be constructed
    }

    public static ParallelDichotomiesResult runParallel(String csaTaskId, CounterTradingValues counterTradingValues, Supplier<DichotomyStepResult> supplierPtEs, Supplier<DichotomyStepResult> supplierFrEs) {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<DichotomyStepResult> futurePtEs = CompletableFuture.supplyAsync(supplierPtEs, executor);
            CompletableFuture<DichotomyStepResult> futureFrEs = CompletableFuture.supplyAsync(supplierFrEs, executor);

            try {
                DichotomyStepResult ptEsDichotomyStepResult = futurePtEs.join();
                DichotomyStepResult frEsDichotomyStepResult = futureFrEs.join();
                return new ParallelDichotomiesResult(ptEsDichotomyStepResult, frEsDichotomyStepResult, counterTradingValues);

            } catch (CompletionException e) {
                throw new CsaInternalException(csaTaskId, e.getMessage());
            }
        } finally {
            executor.shutdown();
        }
    }
}
