package com.farao_community.farao.swe_csa.app.dichotomy;

import com.powsybl.iidm.network.Country;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SweCsaShiftDispatcherTest {

    @Test
    void testDispatch() {
        CounterTradingValues counterTradingValues = new CounterTradingValues(-600.25, 765.25);

        Map<Country, Double> initialNetPositions = Map.of(Country.ES, -1., Country.FR, 1., Country.PT, 2.);
        ShiftDispatcher sweCsaDispatcher = new ShiftDispatcher(initialNetPositions);
        Map<Country, Double> dispatchingResult = sweCsaDispatcher.dispatch(counterTradingValues);

        assertEquals(166.0, dispatchingResult.get(Country.ES));
        assertEquals(-766.25, dispatchingResult.get(Country.FR));
        assertEquals(598.25, dispatchingResult.get(Country.PT));
    }
}
