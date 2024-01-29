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

import com.farao_community.farao.commons.EICode;
import com.farao_community.farao.data.crac_api.Crac;
import com.farao_community.farao.data.crac_api.range_action.CounterTradeRangeAction;
import com.farao_community.farao.rao_runner.api.resource.RaoResponse;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Map;

public class DichotomyRunner {

    public RaoResponse launchDichotomy(Network network, Crac crac, String timestamp, SweCsaRaoValidator validator) {

        Pair<MultipleDichotomyVariables, MultipleDichotomyVariables> initialDichotomyVariable = getInitialDichotomyIndex(crac);
        DichotomyEngine<RaoResponse, MultipleDichotomyVariables> engine = new DichotomyEngine<>(
            new Index<>(initialDichotomyVariable.getLeft(), initialDichotomyVariable.getRight(), 10),
            new SweCsaHalfRangeDivisionIndexStrategy("CT_FRES", "CT_PTES"),
            new LinearScaler(SweCsaZonalData.getZonalData(network), new SweCsaShiftDispatcher(getInitialPositions(crac))),
            validator);
        DichotomyResult<RaoResponse, MultipleDichotomyVariables> result = engine.run(network);
        return result.getHighestValidStep().getValidationData();
    }

    private Map<String, Double>  getInitialPositions(Crac crac) {
        CounterTradeRangeAction ctRaFrEs = crac.getCounterTradeRangeAction("CT_RA_FRES");
        CounterTradeRangeAction ctRaPtEs = crac.getCounterTradeRangeAction("CT_RA_PTES");

        double expFrEs = ctRaFrEs.getInitialSetpoint();
        double expPtEs = ctRaPtEs.getInitialSetpoint();

        return Map.of(
            new EICode(Country.ES).getAreaCode(), expFrEs + expPtEs,
            new EICode(Country.FR).getAreaCode(), -expFrEs,
            new EICode(Country.PT).getAreaCode(), -expPtEs);
    }

    private Pair<MultipleDichotomyVariables, MultipleDichotomyVariables> getInitialDichotomyIndex(Crac crac) {
        CounterTradeRangeAction ctRaFrEs = crac.getCounterTradeRangeAction("CT_RA_FRES");
        CounterTradeRangeAction ctRaEsFr = crac.getCounterTradeRangeAction("CT_RA_ESFR");
        CounterTradeRangeAction ctRaPtEs = crac.getCounterTradeRangeAction("CT_RA_PTES");
        CounterTradeRangeAction ctRaEsPt = crac.getCounterTradeRangeAction("CT_RA_ESPT");

        double expFrEs = ctRaFrEs.getInitialSetpoint();
        double expPtEs = ctRaPtEs.getInitialSetpoint();
        double expEsFr = ctRaEsFr.getInitialSetpoint();
        double expEsPt = ctRaEsPt.getInitialSetpoint();

        double ctFrEsMax = expFrEs >= 0 ? Math.min(Math.min(-ctRaFrEs.getMinAdmissibleSetpoint(expFrEs), ctRaEsFr.getMaxAdmissibleSetpoint(expEsFr)), expFrEs)
            : Math.min(Math.min(ctRaFrEs.getMaxAdmissibleSetpoint(expFrEs), -ctRaEsFr.getMinAdmissibleSetpoint(expEsFr)), -expFrEs);
        double ctPtEsMax = expPtEs >= 0 ? Math.min(Math.min(-ctRaPtEs.getMinAdmissibleSetpoint(expPtEs), ctRaEsPt.getMaxAdmissibleSetpoint(expEsPt)), expPtEs)
            : Math.min(Math.min(ctRaPtEs.getMaxAdmissibleSetpoint(expPtEs), -ctRaEsPt.getMinAdmissibleSetpoint(expEsPt)), -expPtEs);

        MultipleDichotomyVariables initMinIndex = new MultipleDichotomyVariables(Map.of("CT_FRES", 0.0, "CT_PTES", 0.0));
        MultipleDichotomyVariables initMaxIndex = new MultipleDichotomyVariables(Map.of("CT_FRES", ctFrEsMax, "CT_PTES", ctPtEsMax));

        return Pair.of(initMinIndex, initMaxIndex);
    }

}
