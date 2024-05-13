package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.swe_csa.app.dichotomy.CounterTradingValues;
import com.powsybl.iidm.network.Country;
import com.powsybl.openrao.commons.EICode;

import java.util.HashMap;
import java.util.Map;

import static java.lang.Math.signum;

public class ShiftDispatcher {

    private final Map<String, Double> initialNetPositions;

    public ShiftDispatcher(Map<String, Double> initialNetPositions) {
        this.initialNetPositions = initialNetPositions;
    }

    public Map<String, Double> dispatch(CounterTradingValues counterTradingValues) {
        Map<String, Double> dispatching = new HashMap<>();
        dispatching.put(new EICode(Country.FR).getAreaCode(),
            -counterTradingValues.getFrEsCt()
                * signum(initialNetPositions.get(Country.FR.getName())));
        dispatching.put(new EICode(Country.PT).getAreaCode(),
            -counterTradingValues.getPtEsCt()
                * signum(initialNetPositions.get(Country.PT.getName())));
        dispatching.put(new EICode(Country.ES).getAreaCode(),
            -(dispatching.get(new EICode(Country.FR).getAreaCode()) + dispatching.get(new EICode(Country.PT).getAreaCode())));

        return dispatching;
    }

}
