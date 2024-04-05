package com.farao_community.farao.swe_csa.app.dichotomy;

import com.powsybl.iidm.network.Country;

import java.util.Map;

public class ShiftDispatcher {

    private final Map<Country, Double> initialNetPositions;

    public ShiftDispatcher(Map<Country, Double> initialNetPositions) {
        this.initialNetPositions = initialNetPositions;
    }

    public Map<Country, Double> dispatch(CounterTradingValues counterTradingValues) {
        return Map.of(Country.ES, counterTradingValues.getFrEsCt() + counterTradingValues.getPtEsCt() - initialNetPositions.get(Country.ES),
            Country.FR, -counterTradingValues.getFrEsCt() - initialNetPositions.get(Country.FR),
            Country.PT, -counterTradingValues.getPtEsCt() - initialNetPositions.get(Country.PT));
    }

}
