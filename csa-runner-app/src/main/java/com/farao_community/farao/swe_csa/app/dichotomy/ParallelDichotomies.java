package com.farao_community.farao.swe_csa.app.dichotomy;

import com.powsybl.openrao.data.raoresult.api.RaoResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public final class ParallelDichotomies {

    private ParallelDichotomies() {
        // shouldn't be constructed
    }

    public static void runParallel(Supplier<DichotomyStepResult> supplierPtEs, Supplier<DichotomyStepResult> supplierFrEs) {
        ExecutorService executor = Executors.newFixedThreadPool(2);

        CompletableFuture<DichotomyStepResult> futurePtEs = CompletableFuture.supplyAsync(supplierPtEs, executor);
        CompletableFuture<DichotomyStepResult> futureFrEs = CompletableFuture.supplyAsync(supplierFrEs, executor);

        RaoResult resultPtEs = futurePtEs.join().getRaoResult();
        RaoResult resultFrEs = futureFrEs.join().getRaoResult();

        executor.shutdown();

    }
}
