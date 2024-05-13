/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.swe_csa.app.dichotomy;

import com.farao_community.farao.swe_csa.app.ShiftDispatcher;
import com.powsybl.iidm.network.Country;
import com.powsybl.openrao.commons.EICode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Jean-Pierre Arnould {@literal <jean-pierre.arnould at rte-france.com>}
 */

class ShiftDispatcherTest {

    @Test
    void testDispatch() {
        CounterTradingValues ctValues = new CounterTradingValues(-600.25, 765.25);

        ShiftDispatcher dispatcher = new ShiftDispatcher(Map.of(Country.ES.getName(), -1., Country.FR.getName(), 1., Country.PT.getName(), 2.));
        Map<String, Double> dispatchingResult = dispatcher.dispatch(ctValues);

        assertEquals(165.0, dispatchingResult.get(new EICode(Country.ES).getAreaCode()));
        assertEquals(-765.25, dispatchingResult.get(new EICode(Country.FR).getAreaCode()));
        assertEquals(600.25, dispatchingResult.get(new EICode(Country.PT).getAreaCode()));
    }
}
