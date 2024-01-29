package com.farao_community.farao.swe_csa.app.dichotomy.dispatcher;

import com.farao_community.farao.commons.EICode;
import com.farao_community.farao.swe_csa.app.dichotomy.variable.MultipleDichotomyVariables;
import com.powsybl.iidm.network.Country;

import java.util.Map;

public class SweCsaShiftDispatcher implements ShiftDispatcher<MultipleDichotomyVariables> {
    private final Map<String, Double> initialNetPositions;
    private final String eicCodeEs;
    private final String eicCodeFr;
    private final String eicCodePt;

    public SweCsaShiftDispatcher(Map<String, Double> initialNetPositions) {
        this.initialNetPositions = initialNetPositions;
        this.eicCodeEs = new EICode(Country.ES).getAreaCode();
        this.eicCodeFr = new EICode(Country.FR).getAreaCode();
        this.eicCodePt = new EICode(Country.PT).getAreaCode();
    }

    @Override
    public Map<String, Double> dispatch(MultipleDichotomyVariables variable) {
        return Map.of(eicCodeEs, variable.values().get("CT_FRES") + variable.values().get("CT_PTES") - initialNetPositions.get(eicCodeEs),
            eicCodeFr, -variable.values().get("CT_FRES") - initialNetPositions.get(eicCodeFr),
            eicCodePt, -variable.values().get("CT_PTES") - initialNetPositions.get(eicCodePt));
    }

}
