package com.farao_community.farao.swe_csa.app.dichotomy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CounterTradingValuesTest {

    @Test
    void printTest() {
        CounterTradingValues counterTradingValues = new CounterTradingValues(2.5, 9999.004);
        assertEquals(9999.004, counterTradingValues.getFrEsCt());
        assertEquals(2.5, counterTradingValues.getPtEsCt());
        assertEquals("PT-ES-scaled-by-3_and_FR-ES-scaled-by-9999", counterTradingValues.print());
    }
}
