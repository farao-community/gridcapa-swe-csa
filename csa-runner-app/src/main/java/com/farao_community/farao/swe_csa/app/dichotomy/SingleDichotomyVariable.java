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

public class SingleDichotomyVariable implements DichotomyVariable<SingleDichotomyVariable> {
    private final double value;

    public SingleDichotomyVariable(double value) {
        this.value = value;
    }

    public double value() {
        return value;
    }

    @Override
    public boolean isGreaterThan(SingleDichotomyVariable other) {
        return this.value > other.value;
    }

    public double distanceTo(double otherValue) {
        return Math.abs(this.value - otherValue);
    }

    @Override
    public double distanceTo(SingleDichotomyVariable other) {
        return distanceTo(other.value);
    }

    @Override
    public SingleDichotomyVariable halfRangeWith(SingleDichotomyVariable other) {
        return new SingleDichotomyVariable((value + other.value) / 2);
    }

    @Override
    public String print() {
        return String.format("%.0f", value);
    }
}
