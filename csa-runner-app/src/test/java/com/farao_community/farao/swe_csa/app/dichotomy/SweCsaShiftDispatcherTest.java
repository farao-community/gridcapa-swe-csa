package com.farao_community.farao.swe_csa.app.dichotomy;

import com.powsybl.glsk.commons.CountryEICode;
import com.powsybl.iidm.network.Country;
import com.powsybl.openrao.commons.EICode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SweCsaShiftDispatcherTest {

   /* @Test
    void testDispatch() {
        CounterTradingValues counterTradingValues = new CounterTradingValues(-600.25, 765.25);

        Map<Country, Double> initialNetPositions = Map.of(new CountryEICode(Country.ES.getName()), 500., Country.FR, -300., Country.PT, 200.);
        ShiftDispatcher sweCsaDispatcher = new ShiftDispatcher(initialNetPositions);
        Map<String, Double> dispatchingResult = sweCsaDispatcher.dispatch(counterTradingValues);

        assertEquals(765.25, dispatchingResult.get(new EICode(Country.FR).getAreaCode()));
        assertEquals(600.25, dispatchingResult.get(new EICode(Country.PT).getAreaCode()));
        assertEquals(-1365.5, dispatchingResult.get(new EICode(Country.ES).getAreaCode()));

    }*/
}
