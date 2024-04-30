package com.farao_community.farao.swe_csa.app;

public class CounterTradingValues {

    double ptEsCt;
    double frEsCt;

    public CounterTradingValues(double ptEsCt, double frEsCt) {
        this.ptEsCt = ptEsCt;
        this.frEsCt = frEsCt;
    }

    public double getPtEsCt() {
        return ptEsCt;
    }

    public double getFrEsCt() {
        return frEsCt;
    }

    public String print() {
        return String.format("PT-ES-scaled-by-%s_and_FR-ES-scaled-by-%s", Math.round(ptEsCt), Math.round(frEsCt));
    }
}
