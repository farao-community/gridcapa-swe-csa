package com.farao_community.farao.swe_csa.app.dichotomy.dispatcher;

/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

/**
 * @author Jean-Pierre Arnould {@literal <jean-pierre.arnould at rte-france.com>}
 */

import com.farao_community.farao.dichotomy.api.exceptions.ShiftingException;
import com.farao_community.farao.swe_csa.app.dichotomy.variable.DichotomyVariable;

import java.util.Map;

public interface ShiftDispatcher<U extends DichotomyVariable<U>> {
    Map<String, Double> dispatch(U var1) throws ShiftingException;
}
