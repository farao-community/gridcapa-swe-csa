package com.farao_community.farao.swe_csa.app.dichotomy;

import com.farao_community.farao.swe_csa.api.exception.CsaInternalException;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Supplier;

@Service
public class ParallelDichotomiesRunner {

    public ParallelDichotomiesResult run(
        String csaTaskId,
        CounterTradingValues counterTradingValues,
        Supplier<DichotomyStepResult> supplierPtEs,
        Supplier<DichotomyStepResult> supplierFrEs) {

        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            CompletableFuture<DichotomyStepResult> futurePtEs =
                runWithMdcAsync(executor, contextMap, DichotomyDirection.PT_ES, supplierPtEs);

            CompletableFuture<DichotomyStepResult> futureFrEs =
                runWithMdcAsync(executor, contextMap, DichotomyDirection.FR_ES, supplierFrEs);

            // Cancel the other task if one fails
            futurePtEs.whenComplete((r, ex) -> { if (ex != null) futureFrEs.cancel(true); });
            futureFrEs.whenComplete((r, ex) -> { if (ex != null) futurePtEs.cancel(true); });

            CompletableFuture.allOf(futurePtEs, futureFrEs).join();
            return new ParallelDichotomiesResult(
                futurePtEs.join(),
                futureFrEs.join(),
                counterTradingValues
            );

        } catch (CompletionException e) {
            Throwable root = e.getCause() != null ? e.getCause() : e;
            throw new CsaInternalException(csaTaskId, root.getMessage(), root);
        } finally {
            executor.shutdownNow();
        }
    }

    private CompletableFuture<DichotomyStepResult> runWithMdcAsync(
        Executor executor,
        Map<String, String> contextMap,
        DichotomyDirection direction,
        Supplier<DichotomyStepResult> supplier) {

        return CompletableFuture.supplyAsync(() -> {
            MDC.setContextMap(contextMap);
            MDC.put("eventPrefix", direction.toString());
            try {
                return supplier.get();
            } finally {
                MDC.clear();
            }
        }, executor);
    }
}