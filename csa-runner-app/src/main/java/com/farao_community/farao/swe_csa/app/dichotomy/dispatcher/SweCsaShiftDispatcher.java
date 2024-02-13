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

import com.farao_community.farao.swe_csa.app.dichotomy.CounterTradingDirection;
import com.farao_community.farao.swe_csa.app.dichotomy.variable.MultipleDichotomyVariables;
import com.powsybl.iidm.network.Country;

import java.util.Map;

public class SweCsaShiftDispatcher implements ShiftDispatcher<MultipleDichotomyVariables> {
    private final Map<String, Double> initialNetPositions;

    public SweCsaShiftDispatcher(Map<String, Double> initialNetPositions) {
        this.initialNetPositions = initialNetPositions;
    }

    @Override
    public Map<String, Double> dispatch(MultipleDichotomyVariables variable) {
        return Map.of(Country.ES.getName(), variable.values().get(CounterTradingDirection.FR_ES.getName())
                + variable.values().get(CounterTradingDirection.PT_ES.getName()) - initialNetPositions.get(Country.ES.getName()),
            Country.FR.getName(), -variable.values().get(CounterTradingDirection.FR_ES.getName()) - initialNetPositions.get(Country.FR.getName()),
            Country.PT.getName(), -variable.values().get(CounterTradingDirection.PT_ES.getName()) - initialNetPositions.get(Country.PT.getName()));
    }

}
