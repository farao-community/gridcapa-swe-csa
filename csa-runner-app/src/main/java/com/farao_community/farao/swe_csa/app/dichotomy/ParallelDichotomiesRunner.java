package com.farao_community.farao.swe_csa.app.dichotomy;

import com.farao_community.farao.swe_csa.api.exception.CsaInternalException;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

@Service
public class ParallelDichotomiesRunner {

    public ParallelDichotomiesResult run(String csaTaskId, CounterTradingValues counterTradingValues, Supplier<DichotomyStepResult> supplierPtEs, Supplier<DichotomyStepResult> supplierFrEs) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CompletableFuture<DichotomyStepResult> futurePtEs = CompletableFuture.supplyAsync(() -> {
                MDC.setContextMap(contextMap);
                MDC.put("eventPrefix", DichotomyDirection.PT_ES.toString());
                try {
                    return supplierPtEs.get();
                } finally {
                    MDC.clear(); // Clean up after task
                }
                }, executor);
            CompletableFuture<DichotomyStepResult> futureFrEs = CompletableFuture.supplyAsync(() -> {
                MDC.setContextMap(contextMap);
                MDC.put("eventPrefix", DichotomyDirection.FR_ES.toString());
                try {
                    return supplierFrEs.get();
                } finally {
                    MDC.clear(); // Clean up after task
                }
                }, executor);

            // If one fails, cancel the other
            futurePtEs.whenComplete((r, ex) -> {
                if (ex != null) {
                    futureFrEs.cancel(true);
                }
            });
            futureFrEs.whenComplete((r, ex) -> {
                if (ex != null) {
                    futurePtEs.cancel(true);
                }
            });
            try {
                CompletableFuture.allOf(futurePtEs, futureFrEs).join();
                return new ParallelDichotomiesResult(
                    futurePtEs.join(),
                    futureFrEs.join(),
                    counterTradingValues);

            } catch (CompletionException e) {
                Throwable root = e.getCause() != null ? e.getCause() : e;
                throw new CsaInternalException(csaTaskId, root.getMessage(), root);
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
