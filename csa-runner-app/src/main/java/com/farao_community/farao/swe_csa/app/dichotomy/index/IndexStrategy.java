package com.farao_community.farao.swe_csa.app.dichotomy.index;

/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import com.farao_community.farao.swe_csa.app.dichotomy.variable.DichotomyVariable;

/**
 * @author Jean-Pierre Arnould {@literal <jean-pierre.arnould at rte-france.com>}
 */

public interface IndexStrategy<U extends DichotomyVariable<U>> {
    double EPSILON = 1e-3;

    U nextValue(Index<?, U> index);

    default boolean precisionReached(Index<?, U> index) {
        if (index.lowestInvalidStep() != null && index.lowestInvalidStep().getLeft() .distanceTo(index.minValue()) < EPSILON) {
            return true;
        }
        if (index.highestValidStep() != null && index.highestValidStep().getLeft().distanceTo(index.maxValue()) < EPSILON) {
            return true;
        }
        if (index.lowestInvalidStep() == null || index.highestValidStep() == null) {
            return false;
        }
        return index.highestValidStep().getLeft().distanceTo(index.lowestInvalidStep().getLeft()) <= index.precision();
    }

}
