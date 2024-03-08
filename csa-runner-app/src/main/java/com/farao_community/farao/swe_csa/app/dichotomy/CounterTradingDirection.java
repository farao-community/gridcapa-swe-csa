package com.farao_community.farao.swe_csa.app.dichotomy;
/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

/**
 * @author Jean-Pierre Arnould {@literal <jean-pierre.arnould at rte-france.com>}
 */
public enum CounterTradingDirection {
    FR_ES("CT_FRES"),
    PT_ES("CT_PTES");

    private final String name;

    CounterTradingDirection(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
