package com.farao_community.farao.swe_csa.app.dichotomy.index;
/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import com.farao_community.farao.data.crac_api.Instant;
import com.farao_community.farao.data.crac_api.cnec.Cnec;
import com.farao_community.farao.data.crac_api.cnec.FlowCnec;
import com.farao_community.farao.data.rao_result_api.RaoResult;
import com.farao_community.farao.dichotomy.api.results.DichotomyStepResult;
import com.farao_community.farao.swe_csa.app.dichotomy.index.HalfRangeDivisionIndexStrategy;
import com.farao_community.farao.swe_csa.app.dichotomy.index.Index;
import com.farao_community.farao.swe_csa.app.dichotomy.variable.MultipleDichotomyVariables;
import com.farao_community.farao.swe_csa.app.rao_result.RaoResultWithCounterTradeRangeActions;
import com.powsybl.iidm.network.Country;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Map;
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
    private final String frEsIndexName;
    private final String ptEsIndexName;

    public SweCsaHalfRangeDivisionIndexStrategy(String frEsIndexName, String ptEsIndexName) {
        super(false);
        this.frEsIndexName = frEsIndexName;
        this.ptEsIndexName = ptEsIndexName;
    }

    @Override
    public MultipleDichotomyVariables nextValue(Index<?, MultipleDichotomyVariables> index) {
        if (precisionReached(index)) {
            throw new AssertionError("Dichotomy engine should not ask for next value if precision is reached");
        }
        if (index.lowestInvalidStep() == null) {
            return index.maxValue(); // minimum counter-trading, maximum exchange
        }
        if (index.highestValidStep() == null) {
            return index.minValue(); // maximum counter-trading, minimum exchange
        }

        Map<String, Double> newValues = Map.of(
            frEsIndexName, computeNextValue(index, frEsIndexName),
            ptEsIndexName, computeNextValue(index, ptEsIndexName)
        );

        return new MultipleDichotomyVariables(newValues);
    }

    double computeNextValue(Index<?, MultipleDichotomyVariables> index, String key) {
        double maxSafeValue = Double.MIN_VALUE;
        double minUnsafeValue = Double.MAX_VALUE;

        for(Pair<MultipleDichotomyVariables, ? extends DichotomyStepResult<?>> step : index.testedSteps()) {
            boolean isSafe = isSafeForBorder((RaoResultWithCounterTradeRangeActions)step.getRight().getRaoResult(), key);
            Double value = Double.valueOf(step.getLeft().values().get(key));
            if(isSafe && value>maxSafeValue) {
                maxSafeValue = value;
            } else if(!isSafe && value<minUnsafeValue) {
                minUnsafeValue = value;
            }
        }
        // If precision is reached, keep max value
        if (Math.abs(maxSafeValue - minUnsafeValue) < index.precision()) {
            return maxSafeValue;
        }
        // Else, return average
        return (maxSafeValue + minUnsafeValue) / 2;
    }

    boolean isSafeForBorder(RaoResultWithCounterTradeRangeActions raoResult, String key) {
        if (key.equals(frEsIndexName)) {
            return raoResult.getCnecsOnConstraintForCountry(Country.FR).isEmpty();
        }
        if (key.equals(ptEsIndexName)) {
            return raoResult.getCnecsOnConstraintForCountry(Country.PT).isEmpty();
        }
        return false; // TODO : throw
    }

}
