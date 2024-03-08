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

public enum CounterTradeRangeActionDirection {
    FR_ES("CT_RA_FRES"),
    ES_FR("CT_RA_ESFR"),
    ES_PT("CT_RA_ESPT"),
    PT_ES("CT_RA_PTES");

    private final String name;

    CounterTradeRangeActionDirection(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
