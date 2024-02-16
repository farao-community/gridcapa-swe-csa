package com.farao_community.farao.swe_csa.app.dichotomy;

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
