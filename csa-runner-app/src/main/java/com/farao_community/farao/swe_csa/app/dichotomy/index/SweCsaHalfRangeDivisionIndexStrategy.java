package com.farao_community.farao.swe_csa.app.dichotomy.index;
/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import com.farao_community.farao.commons.Unit;
import com.farao_community.farao.data.crac_api.Crac;
import com.farao_community.farao.data.crac_api.cnec.FlowCnec;
import com.farao_community.farao.dichotomy.api.results.DichotomyStepResult;
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
 */

public class SweCsaHalfRangeDivisionIndexStrategy extends HalfRangeDivisionIndexStrategy<MultipleDichotomyVariables> {
    private final Set<FlowCnec> frEsCnecs;
    private final Set<FlowCnec> ptEsCnecs;

    public SweCsaHalfRangeDivisionIndexStrategy(Crac crac, Network network) {
        super(false);
        this.frEsCnecs = getCnecsBorder(crac, network, Country.FR);
        this.ptEsCnecs = getCnecsBorder(crac, network, Country.PT);
    }

    private Set<FlowCnec> getCnecsBorder(Crac crac, Network network, Country country) {
        return crac.getFlowCnecs().stream()
            .filter(flowCnec -> flowCnec.getLocation(network).contains(Optional.of(country)))
            .collect(Collectors.toSet());
    }

    @Override
    public MultipleDichotomyVariables nextValue(Index<?, MultipleDichotomyVariables> index) {
        if (precisionReached(index)) {
            throw new AssertionError("Dichotomy engine should not ask for next value if precision is reached");
        }

        Map<String, Double> newValues = Map.of(
            CounterTradingDirection.FR_ES.getName(), computeNextValue(index, CounterTradingDirection.FR_ES.getName()),
            CounterTradingDirection.PT_ES.getName(), computeNextValue(index, CounterTradingDirection.PT_ES.getName())
        );

        return new MultipleDichotomyVariables(newValues);
    }

    public Set<FlowCnec> getFrEsCnecs() {
        return this.frEsCnecs;
    }

    public Set<FlowCnec> getPtEsCnecs() {
        return this.ptEsCnecs;
    }

    double computeNextValue(Index<?, MultipleDichotomyVariables> index, String key) {
        double maxSafeValue = Double.MIN_VALUE;
        double minUnsafeValue = Double.MAX_VALUE;

        for (Pair<MultipleDichotomyVariables, ? extends DichotomyStepResult<?>> step : index.testedSteps()) {
            boolean isSafe = isSafeForBorder((RaoResultWithCounterTradeRangeActions) step.getRight().getRaoResult(), key);
            Double value = Double.valueOf(step.getLeft().values().get(key));
            if (isSafe && value > maxSafeValue) {
                maxSafeValue = value;
            } else if (!isSafe && value < minUnsafeValue) {
                minUnsafeValue = value;
            }
        }

        if (minUnsafeValue == Double.MAX_VALUE) { // if there's no unsafe value
            return index.maxValue().values().get(key);
        }

        if (maxSafeValue == Double.MIN_VALUE) { // if there's no safe value
            return index.minValue().values().get(key);
        }

        // If precision is reached, keep max value
        if (Math.abs(maxSafeValue - minUnsafeValue) < index.precision()) {
            return maxSafeValue;
        }
        // Else, return average
        return (maxSafeValue + minUnsafeValue) / 2;
    }

    boolean isSafeForBorder(RaoResultWithCounterTradeRangeActions raoResult, String key) {
        if (key.equals(CounterTradingDirection.FR_ES.getName())) {
            return !hasCnecNegativeMargin(raoResult, this.frEsCnecs);
        }
        if (key.equals(CounterTradingDirection.PT_ES.getName())) {
            return !hasCnecNegativeMargin(raoResult, this.ptEsCnecs);
        }
        return false; // TODO : throw
    }

    boolean hasCnecNegativeMargin(RaoResultWithCounterTradeRangeActions raoResult, Set<FlowCnec> cnecs) {
        for (FlowCnec cnec : cnecs) {
            if (raoResult.getMargin(cnec.getState().getInstant(), cnec, Unit.MEGAWATT) < 0) {
                return true;
            }
        }
        return false;
    }

}
