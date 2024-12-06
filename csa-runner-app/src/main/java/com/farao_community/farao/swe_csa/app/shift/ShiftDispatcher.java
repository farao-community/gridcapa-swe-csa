package com.farao_community.farao.swe_csa.app.shift;

import com.farao_community.farao.swe_csa.app.dichotomy.CounterTradingValues;
import com.powsybl.iidm.network.Country;
import com.powsybl.openrao.commons.EICode;

import java.util.HashMap;
import java.util.Map;

import static java.lang.Math.signum;

public class ShiftDispatcher {

    private final Map<String, Double> initialNetPositions;
    public static final String EI_CODE_FR = new EICode(Country.FR).getAreaCode();
    public static final String EI_CODE_PT = new EICode(Country.PT).getAreaCode();
    public static final String EI_CODE_ES = new EICode(Country.ES).getAreaCode();

    public ShiftDispatcher(Map<String, Double> initialNetPositions) {
        this.initialNetPositions = initialNetPositions;
    }

    public Map<String, Double> dispatch(CounterTradingValues counterTradingValues) {
        Map<String, Double> dispatching = new HashMap<>();
        dispatching.put(EI_CODE_FR, -counterTradingValues.getFrEsCt() * signum(initialNetPositions.get(Country.FR.getName())));
        dispatching.put(EI_CODE_PT, -counterTradingValues.getPtEsCt() * signum(initialNetPositions.get(Country.PT.getName())));
        dispatching.put(EI_CODE_ES, -dispatching.get(EI_CODE_FR) - dispatching.get(EI_CODE_PT));

        return dispatching;
    }

}
