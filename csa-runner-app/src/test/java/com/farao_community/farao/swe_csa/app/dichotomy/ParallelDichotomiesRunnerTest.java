package com.farao_community.farao.swe_csa.app.dichotomy;

import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ParallelDichotomiesRunnerTest {

    @Test
    void testRun() {
        String csaTaskId = "task1";
        CounterTradingValues counterTradingValues = mock(CounterTradingValues.class);
        Supplier<DichotomyStepResult> supplierPtEs = mock(Supplier.class);
        Supplier<DichotomyStepResult> supplierFrEs = mock(Supplier.class);
        DichotomyStepResult resultPtEs = mock(DichotomyStepResult.class);
        DichotomyStepResult resultFrEs = mock(DichotomyStepResult.class);
        when(supplierPtEs.get()).thenReturn(resultPtEs);
        when(supplierFrEs.get()).thenReturn(resultFrEs);

        ParallelDichotomiesRunner runner = new ParallelDichotomiesRunner();
        ParallelDichotomiesResult result = runner.run(csaTaskId, counterTradingValues, supplierPtEs, supplierFrEs);

        assertNotNull(result);
        assertEquals(resultPtEs, result.getPtEsResult());
        assertEquals(resultFrEs, result.getFrEsResult());
        assertEquals(counterTradingValues, result.getCounterTradingValues());
    }
}
