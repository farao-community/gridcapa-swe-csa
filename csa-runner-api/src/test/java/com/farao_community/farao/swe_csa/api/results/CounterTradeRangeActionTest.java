package com.farao_community.farao.swe_csa.api.results;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CounterTradeRangeActionTest {

    @Test
    public void testGetters() {
        // Arrange
        String ctRangeActionId = "CT123";
        double setPoint = 10.5;
        List<String> concernedCnecs = Arrays.asList("CNEC1", "CNEC2");
        CounterTradeRangeActionResult result = new CounterTradeRangeActionResult(ctRangeActionId, setPoint, concernedCnecs);

        assertEquals(ctRangeActionId, result.getCtRangeActionId());
        assertEquals(setPoint, result.getSetPoint(), 0.001);
        assertEquals(concernedCnecs, result.getConcernedCnecs());
    }

}
