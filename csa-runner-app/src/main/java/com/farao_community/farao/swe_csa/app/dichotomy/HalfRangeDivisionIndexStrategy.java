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

/**
 * Implementation of IndexStrategy that consists of a basic dichotomy between minimum index value and maximum one.
 * First, it will validate minimum or maximum value (depending on startWitnMin value) and then validate recursively
 * the middle of the dichotomy interval.
 * I.E. if startWithMin is true then we start with min value then (min + max) / 2 value
 * if startWithMin is false then we start with max value then (min + max) / 2 value
 *
 * @author Marc Schwitzguebel {@literal <marc.schwitzguebel at rte-france.com>}
 */
public class HalfRangeDivisionIndexStrategy<U extends DichotomyVariable<U>> implements IndexStrategy<U> {

    private final boolean startWithMin;

    public HalfRangeDivisionIndexStrategy(boolean startWithMin) {
        this.startWithMin = startWithMin;
    }

    @Override
    public U nextValue(Index<?, U> index) {
        if (precisionReached(index)) {
            throw new AssertionError("Dichotomy engine should not ask for next value if precision is reached");
        }
        if (startWithMin) {
            if (index.highestValidStep() == null) {
                return index.minValue();
            }
            if (index.lowestInvalidStep() == null) {
                return index.maxValue().halfRangeWith(index.highestValidStep().getLeft());
            }
        } else {
            if (index.lowestInvalidStep() == null) {
                return index.maxValue();
            }
            if (index.highestValidStep() == null) {
                return index.lowestInvalidStep().getLeft().halfRangeWith(index.minValue());
            }
        }
        return index.lowestInvalidStep().getLeft().halfRangeWith(index.highestValidStep().getLeft());
    }

    @Override
    public boolean precisionReached(Index<?, U> index) {
        if (index.lowestInvalidStep() != null && index.lowestInvalidStep().getLeft().distanceTo(index.minValue()) < EPSILON) {
            return true;
        }
        if (index.highestValidStep() != null && index.highestValidStep().getLeft().distanceTo(index.maxValue()) < EPSILON) {
            return true;
        }
        if (index.lowestInvalidStep() != null && index.highestValidStep() == null) {
            return index.lowestInvalidStep().getLeft().distanceTo(index.minValue()) <= index.precision();
        }
        if (index.lowestInvalidStep() == null) {
            return false;
        }
        return index.highestValidStep().getLeft().distanceTo(index.lowestInvalidStep().getLeft()) <= index.precision();
    }
}