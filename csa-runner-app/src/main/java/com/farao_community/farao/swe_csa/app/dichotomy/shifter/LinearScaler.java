package com.farao_community.farao.swe_csa.app.dichotomy.shifter;

/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import com.farao_community.farao.dichotomy.api.exceptions.GlskLimitationException;
import com.farao_community.farao.dichotomy.api.exceptions.ShiftingException;
import com.farao_community.farao.swe_csa.app.dichotomy.dispatcher.ShiftDispatcher;
import com.farao_community.farao.swe_csa.app.dichotomy.variable.MultipleDichotomyVariables;
import com.powsybl.glsk.commons.ZonalData;
import com.powsybl.iidm.modification.scalable.Scalable;
import com.powsybl.iidm.modification.scalable.ScalingParameters;
import com.powsybl.iidm.network.Network;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import static com.farao_community.farao.commons.logs.FaraoLoggerProvider.BUSINESS_LOGS;
import static com.farao_community.farao.commons.logs.FaraoLoggerProvider.BUSINESS_WARNS;

/**
 * @author Jean-Pierre Arnould {@literal <jean-pierre.arnould at rte-france.com>}
 */

public final class LinearScaler implements NetworkShifter<MultipleDichotomyVariables> {
    private static final double DEFAULT_EPSILON = 1e-3;

    private final ZonalData<Scalable> zonalScalable;
    private final ShiftDispatcher<MultipleDichotomyVariables>  shiftDispatcher;
    private final double shiftEpsilon;

    public LinearScaler(ZonalData<Scalable> zonalScalable, ShiftDispatcher<MultipleDichotomyVariables> shiftDispatcher) {
        this(zonalScalable, shiftDispatcher, DEFAULT_EPSILON);
    }

    public LinearScaler(ZonalData<Scalable> zonalScalable, ShiftDispatcher<MultipleDichotomyVariables> shiftDispatcher, double shiftEpsilon) {
        this.zonalScalable = zonalScalable;
        this.shiftDispatcher = shiftDispatcher;
        this.shiftEpsilon = shiftEpsilon;
    }

    @Override
    public void shiftNetwork(MultipleDichotomyVariables stepValue, Network network) throws GlskLimitationException, ShiftingException {
        BUSINESS_LOGS.info(String.format("Starting linear scaling on network %s with step value %s",
            network.getVariantManager().getWorkingVariantId(), stepValue.print()));
        Map<String, Double> scalingValuesByCountry = shiftDispatcher.dispatch(stepValue);
        List<String> limitingCountries = new ArrayList<>();
        for (Map.Entry<String, Double> entry : scalingValuesByCountry.entrySet()) {
            String zoneId = entry.getKey();
            double asked = entry.getValue();
            BUSINESS_LOGS.info(String.format("Applying variation on zone %s (target: %.2f)", zoneId, asked));
            ScalingParameters scalingParameters = new ScalingParameters();
            scalingParameters.setPriority(ScalingParameters.Priority.RESPECT_OF_VOLUME_ASKED);
            scalingParameters.setReconnect(true);
            double done = zonalScalable.getData(zoneId).scale(network, asked, scalingParameters);
            if (Math.abs(done - asked) > shiftEpsilon) {
                BUSINESS_WARNS.warn(String.format("Incomplete variation on zone %s (target: %.2f, done: %.2f)",
                    zoneId, asked, done));
                limitingCountries.add(zoneId);
            }
        }
        if (!limitingCountries.isEmpty()) {
            StringJoiner sj = new StringJoiner(", ", "There are Glsk limitation(s) in ", ".");
            limitingCountries.forEach(sj::add);
            throw new GlskLimitationException(sj.toString());
        }
    }
}
