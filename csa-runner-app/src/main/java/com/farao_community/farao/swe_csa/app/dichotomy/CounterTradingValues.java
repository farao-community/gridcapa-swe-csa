package com.farao_community.farao.swe_csa.app.dichotomy;

public record CounterTradingValues(double ptEsCt, double frEsCt) {

    public String print() {
        return String.format("PT-ES-scaled-by-%s_and_FR-ES-scaled-by-%s", Math.round(ptEsCt), Math.round(frEsCt));
    }
}
