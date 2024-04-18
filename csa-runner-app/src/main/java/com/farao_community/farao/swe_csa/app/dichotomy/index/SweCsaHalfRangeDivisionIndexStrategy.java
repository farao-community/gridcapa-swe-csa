package com.farao_community.farao.swe_csa.app.dichotomy.index;
/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import com.powsybl.openrao.commons.Unit;
import com.powsybl.openrao.data.cracapi.Crac;
import com.powsybl.openrao.data.cracapi.cnec.AngleCnec;
import com.powsybl.openrao.data.cracapi.cnec.FlowCnec;
import com.farao_community.farao.dichotomy.api.results.DichotomyStepResult;
import com.farao_community.farao.rao_runner.api.resource.RaoResponse;
import com.farao_community.farao.swe_csa.app.dichotomy.CounterTradingDirection;
import com.farao_community.farao.swe_csa.app.dichotomy.variable.MultipleDichotomyVariables;
import com.farao_community.farao.swe_csa.app.rao_result.RaoResultWithCounterTradeRangeActions;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of IndexStrategy that consists of a basic dichotomy between minimum index value and maximum one, on
 * FR-ES and PT-ES borders.
 * When one of the borders is secured with a good precision, dichotomy continues on the other border until that one
 * is secured too.
 *
 * @author Peter Mitri {@literal <peter.mitri at rte-france.com>}
 * @author Jean-Pierre Arnould {@literal <jean-pierre.arnould at rte-france.com>}
 */

public class SweCsaHalfRangeDivisionIndexStrategy extends HalfRangeDivisionIndexStrategy<MultipleDichotomyVariables> {
    private Set<FlowCnec> frEsFlowCnecs;
    private Set<FlowCnec> ptEsFlowCnecs;
    private Set<AngleCnec> frEsAngleCnecs;
    private Set<AngleCnec> ptEsAngleCnecs;

    public SweCsaHalfRangeDivisionIndexStrategy(Crac crac, Network network) {
        super(false);
        getCnecsBorder(crac, network, Country.FR);
        getCnecsBorder(crac, network, Country.PT);
    }

    private void getCnecsBorder(Crac crac, Network network, Country country) {
        //TODO : implement association between cnecs and borders (CSA-67)
        this.frEsFlowCnecs = crac.getFlowCnecs().stream()
            .filter(flowCnec -> flowCnec.isOptimized() && flowCnec.getLocation(network).contains(Optional.of(Country.FR)))
            .collect(Collectors.toSet());
        this.ptEsFlowCnecs = crac.getFlowCnecs().stream()
            .filter(flowCnec -> flowCnec.isOptimized() && flowCnec.getLocation(network).contains(Optional.of(Country.PT)))
            .collect(Collectors.toSet());
        this.frEsAngleCnecs = crac.getAngleCnecs().stream()
            .filter(angleCnec -> angleCnec.isOptimized() && angleCnec.getLocation(network).contains(Optional.of(Country.FR)))
            .collect(Collectors.toSet());
        this.ptEsAngleCnecs = crac.getAngleCnecs().stream()
            .filter(angleCnec -> angleCnec.isOptimized() && angleCnec.getLocation(network).contains(Optional.of(Country.PT)))
            .collect(Collectors.toSet());
    }

    @Override
    public MultipleDichotomyVariables nextValue(Index<?, MultipleDichotomyVariables> index) {
        if (precisionReached(index)) {
            throw new AssertionError("Dichotomy engine should not ask for next value if precision is reached");
        }

        Map<String, Double> newValues = Map.of(
            CounterTradingDirection.FR_ES.getName(), computeNextValue((Index<RaoResponse, MultipleDichotomyVariables>) index, CounterTradingDirection.FR_ES.getName()),
            CounterTradingDirection.PT_ES.getName(), computeNextValue((Index<RaoResponse, MultipleDichotomyVariables>) index, CounterTradingDirection.PT_ES.getName())
        );

        return new MultipleDichotomyVariables(newValues);
    }

    public Set<FlowCnec> getFrEsFlowCnecs() {
        return this.frEsFlowCnecs;
    }

    public Set<FlowCnec> getPtEsFlowCnecs() {
        return this.ptEsFlowCnecs;
    }

    public Set<AngleCnec> getFrEsAngleCnecs() {
        return this.frEsAngleCnecs;
    }

    public Set<AngleCnec> getPtEsAngleCnecs() {
        return this.ptEsAngleCnecs;
    }

    double computeNextValue(Index<RaoResponse, MultipleDichotomyVariables> index, String border) {
        double maxUnsafeValue = -Double.MAX_VALUE;
        double minSafeValue = Double.MAX_VALUE;

        for (Pair<MultipleDichotomyVariables, DichotomyStepResult<RaoResponse>> step : index.testedSteps()) {
            boolean isSafe = isSafeForBorder((RaoResultWithCounterTradeRangeActions) step.getRight().getRaoResult(), border);
            Double value = step.getLeft().values().get(border);
            if (isSafe && value < minSafeValue) {
                minSafeValue = value;
            } else if (!isSafe && value > maxUnsafeValue) {
                maxUnsafeValue = value;
            }
        }

        if (maxUnsafeValue == -Double.MAX_VALUE) { // if there's no unsafe value
            return index.minValue().values().get(border);
        }

        if (minSafeValue == Double.MAX_VALUE) { // if there's no safe value
            return index.maxValue().values().get(border);
        }

        // If precision is reached, keep max value
        if (Math.abs(minSafeValue - maxUnsafeValue) < index.precision()) {
            return minSafeValue;
        }
        // Else, return average
        return (minSafeValue + maxUnsafeValue) / 2;
    }

    boolean isSafeForBorder(RaoResultWithCounterTradeRangeActions raoResult, String key) {
        if (key.equals(CounterTradingDirection.FR_ES.getName())) {
            return !hasFlowCnecNegativeMargin(raoResult, this.frEsFlowCnecs);
        }
        if (key.equals(CounterTradingDirection.PT_ES.getName())) {
            return !hasFlowCnecNegativeMargin(raoResult, this.ptEsFlowCnecs);
        }
        return false; // TODO : throw
    }

    boolean hasFlowCnecNegativeMargin(RaoResultWithCounterTradeRangeActions raoResult, Set<FlowCnec> flowCnecs) {
        for (FlowCnec flowCnec : flowCnecs) {
            if (raoResult.getMargin(flowCnec.getState().getInstant(), flowCnec, Unit.AMPERE) < 0) {
                return true;
            }
        }
        return false;
    }

}
