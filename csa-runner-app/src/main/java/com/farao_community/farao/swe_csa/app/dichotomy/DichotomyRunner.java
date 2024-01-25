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

import com.farao_community.farao.data.crac_api.Crac;
import com.farao_community.farao.data.crac_api.range_action.CounterTradeRangeAction;
import com.farao_community.farao.dichotomy.shift.ShiftDispatcher;
import com.farao_community.farao.dichotomy.shift.SplittingFactors;
import com.farao_community.farao.rao_runner.api.resource.RaoResponse;
import com.powsybl.glsk.commons.ZonalData;
import com.powsybl.glsk.ucte.UcteGlskDocument;
import com.powsybl.iidm.modification.scalable.Scalable;
import com.powsybl.iidm.network.Network;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public class DichotomyRunner {

    public RaoResponse launchDichotomy(Network network, Crac crac, String timestamp, SweCsaRaoValidator validator) throws IOException {

        Pair<MultipleDichotomyVariables,MultipleDichotomyVariables> initialDichotomyVariable = getInitialDichotomyIndex(crac);
        DichotomyEngine<RaoResponse, MultipleDichotomyVariables> engine = new DichotomyEngine<RaoResponse, MultipleDichotomyVariables>(
            new Index<>(initialDichotomyVariable.getLeft(), initialDichotomyVariable.getRight(), 10),
            new HalfRangeDivisionIndexStrategy<MultipleDichotomyVariables>(true),
            new LinearScaler(importGlskFile(timestamp, network), getCsaSweShiftDispatcher()),
            validator);
        DichotomyResult<RaoResponse, MultipleDichotomyVariables> result = engine.run(network);
        return result.getHighestValidStep().getValidationData();
    }

    private Pair<MultipleDichotomyVariables,MultipleDichotomyVariables> getInitialDichotomyIndex(Crac crac) {
        CounterTradeRangeAction ctRaFrEs = crac.getCounterTradeRangeAction("CT_RA_FRES");
        CounterTradeRangeAction ctRaEsFr = crac.getCounterTradeRangeAction("CT_RA_ESFR");
        CounterTradeRangeAction ctRaPtEs = crac.getCounterTradeRangeAction("CT_RA_PTES");
        CounterTradeRangeAction ctRaEsPt = crac.getCounterTradeRangeAction("CT_RA_ESPT");

        double expFrEs = ctRaFrEs.getInitialSetpoint();
        double expPtEs = ctRaPtEs.getInitialSetpoint();
        double expEsFr = ctRaEsFr.getInitialSetpoint();
        double expEsPt = ctRaEsPt.getInitialSetpoint();

        double ctFrEsMax = expFrEs>=0 ? Math.min(Math.min(-ctRaFrEs.getMinAdmissibleSetpoint(expFrEs), ctRaEsFr.getMaxAdmissibleSetpoint(expEsFr)), expFrEs)
            : Math.min(Math.min(ctRaFrEs.getMaxAdmissibleSetpoint(expFrEs), -ctRaEsFr.getMinAdmissibleSetpoint(expEsFr)), -expFrEs);
        double ctPtEsMax = expPtEs>=0 ? Math.min(Math.min(-ctRaPtEs.getMinAdmissibleSetpoint(expPtEs), ctRaEsPt.getMaxAdmissibleSetpoint(expEsPt)), expPtEs)
            : Math.min(Math.min(ctRaPtEs.getMaxAdmissibleSetpoint(expPtEs), -ctRaEsPt.getMinAdmissibleSetpoint(expEsPt)), -expPtEs);

        MultipleDichotomyVariables initMinIndex = new MultipleDichotomyVariables(Map.of("CT_FRES", 0.0, "CT_PTES", 0.0));
        MultipleDichotomyVariables initMaxIndex = new MultipleDichotomyVariables(Map.of("CT_FRES", ctFrEsMax, "CT_PTES", ctPtEsMax));

        return Pair.of(initMinIndex, initMaxIndex);
    }

    //TODO : import real splitting factors parameterization
    private ShiftDispatcher getCsaSweShiftDispatcher() {
        Map<String, Double> factors = Map.of("FR", 0.3, "ES", 0.5, "PT", 0.2);
        ShiftDispatcher csaSweShiftDispatcher = new SplittingFactors(factors);
        return csaSweShiftDispatcher;
    }

    private ZonalData<Scalable> importGlskFile(String timestamp, Network network) throws IOException {
        //TODO : import real glsk file
        File glskFile = new File(getClass().getResource("/10VCORSWEPR-ENDE_18V0000000005KUU_SWE-GLSK-B22-A48-F008_20220617-111.xml").getFile());
        InputStream inputStream = new FileInputStream(glskFile);
        UcteGlskDocument ucteGlskDocument = UcteGlskDocument.importGlsk(inputStream);
        return ucteGlskDocument.getZonalScalable(network, ucteGlskDocument.getGSKTimeInterval().getStart());
    }
}
