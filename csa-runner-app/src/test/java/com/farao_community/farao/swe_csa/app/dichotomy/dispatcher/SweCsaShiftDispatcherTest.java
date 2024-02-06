package com.farao_community.farao.swe_csa.app.dichotomy.dispatcher;

/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

/**
 * @author Jean-Pierre Arnould {@literal <jean-pierre.arnould at rte-france.com>}
 */

import com.farao_community.farao.commons.EICode;
import com.farao_community.farao.swe_csa.app.dichotomy.CounterTradingDirection;
import com.farao_community.farao.swe_csa.app.dichotomy.variable.MultipleDichotomyVariables;
import com.powsybl.iidm.network.Country;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SweCsaShiftDispatcherTest {

    @Test
    void testDispatch() {
        MultipleDichotomyVariables mdv1 = new MultipleDichotomyVariables(Map.of("k1", 1., "k2", 0.1));
        MultipleDichotomyVariables mdv2 = new MultipleDichotomyVariables(Map.of(CounterTradingDirection.FR_ES.getName(), 765.25, CounterTradingDirection.PT_ES.getName(), -600.25));

        SweCsaShiftDispatcher sweCsaDispatcher = new SweCsaShiftDispatcher(Map.of(new EICode(Country.ES).getAreaCode(), -1., new EICode(Country.FR).getAreaCode(), 1., new EICode(Country.PT).getAreaCode(), 2.));
        Map<String, Double> dispatchingResult = sweCsaDispatcher.dispatch(mdv2);

        assertThrows(NullPointerException.class, () -> sweCsaDispatcher.dispatch(mdv1));
        assertEquals(166.0, dispatchingResult.get(new EICode(Country.ES).getAreaCode()));
        assertEquals(-766.25, dispatchingResult.get(new EICode(Country.FR).getAreaCode()));
        assertEquals(598.25, dispatchingResult.get(new EICode(Country.PT).getAreaCode()));
    }
}
