package com.farao_community.farao.swe_csa.app.dichotomy.variable;

/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

/**
 * @author Jean-Pierre Arnould {@literal <jean-pierre.arnould at rte-france.com>}
 */

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class MultipleDichotomyVariables implements DichotomyVariable<MultipleDichotomyVariables> {
    private final Map<String, Double> values; // values with keys

    public MultipleDichotomyVariables(Map<String, Double> values) {
        this.values = values;
    }

    public Map<String, Double> values() {
        return new HashMap<>(values);
    }

    @Override
    public boolean isGreaterThan(MultipleDichotomyVariables other) {
        if (!values.keySet().equals(other.values.keySet())) {
            // TODO : throw
        }
        return values.entrySet().stream().anyMatch(
            e -> e.getValue() > other.values.get(e.getKey())
        );
    }

    @Override
    public double distanceTo(MultipleDichotomyVariables other) {
        if (!values.keySet().equals(other.values.keySet())) {
            // TODO : throw
        }
        return values.entrySet().stream().mapToDouble(
            e -> Math.abs(e.getValue() - other.values.get(e.getKey()))
        ).max().orElse(0.);
    }

    @Override
    public MultipleDichotomyVariables halfRangeWith(MultipleDichotomyVariables other) {
        return new MultipleDichotomyVariables(
            values.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> (e.getValue() + other.values.get(e.getKey())) / 2
            )));
    }

    @Override
    public String print() {
        return values.entrySet().stream().map(e -> String.format("%s : %.0f", e.getKey(), e.getValue())).collect(Collectors.joining(", "));
    }
}
