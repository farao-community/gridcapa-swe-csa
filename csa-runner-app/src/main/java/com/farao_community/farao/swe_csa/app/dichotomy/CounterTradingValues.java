package com.farao_community.farao.swe_csa.app.dichotomy;

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

    public Object print() {
        return String.format("PT-ES: %s, FR-ES: %s", ptEsCt, frEsCt);
    }
}
