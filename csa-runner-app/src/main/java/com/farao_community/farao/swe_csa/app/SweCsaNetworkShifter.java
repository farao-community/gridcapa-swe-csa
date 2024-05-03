package com.farao_community.farao.swe_csa.app;

/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import com.farao_community.farao.dichotomy.api.exceptions.GlskLimitationException;
import com.farao_community.farao.dichotomy.api.exceptions.ShiftingException;
import com.farao_community.farao.gridcapa_swe_commons.shift.ScalableGeneratorConnector;
import com.powsybl.glsk.commons.ZonalData;
import com.powsybl.iidm.modification.scalable.Scalable;
import com.powsybl.iidm.modification.scalable.ScalingParameters;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.commons.EICode;

import java.util.*;

import static com.powsybl.openrao.commons.logs.OpenRaoLoggerProvider.BUSINESS_LOGS;
import static com.powsybl.openrao.commons.logs.OpenRaoLoggerProvider.BUSINESS_WARNS;

/**
 * @author Jean-Pierre Arnould {@literal <jean-pierre.arnould at rte-france.com>}
 */

public final class SweCsaNetworkShifter {
    private static final double DEFAULT_EPSILON = 1;
    public static final String EI_CODE_FR = new EICode(Country.FR).getAreaCode();
    public static final String EI_CODE_PT = new EICode(Country.PT).getAreaCode();
    public static final String EI_CODE_ES = new EICode(Country.ES).getAreaCode();
    private final ZonalData<Scalable> zonalScalable;
    private final double shiftEpsilon;

    private final ShiftDispatcher shiftDispatcher;

    public SweCsaNetworkShifter(ZonalData<Scalable> zonalScalable, ShiftDispatcher shiftDispatcher) {
        this(zonalScalable, shiftDispatcher, DEFAULT_EPSILON);
    }

    public SweCsaNetworkShifter(ZonalData<Scalable> zonalScalable, ShiftDispatcher shiftDispatcher, double shiftEpsilon) {
        this.zonalScalable = zonalScalable;
        this.shiftDispatcher = shiftDispatcher;
        this.shiftEpsilon = shiftEpsilon;
    }

    public void shiftNetwork(CounterTradingValues counterTradingValues, Network network) throws GlskLimitationException, ShiftingException {
        BUSINESS_LOGS.info("Starting shift on network {}", network.getVariantManager().getWorkingVariantId());
        Map<String, Double> scalingValuesByCountry = shiftDispatcher.dispatch(counterTradingValues);
        ScalableGeneratorConnector scalableGeneratorConnector = new ScalableGeneratorConnector(zonalScalable);

        try {
            String logTargetCountriesShift = String.format("Target shifts by country: [ES = %.2f, FR = %.2f, PT = %.2f]",
                scalingValuesByCountry.get(EI_CODE_ES), scalingValuesByCountry.get(EI_CODE_FR), scalingValuesByCountry.get(EI_CODE_PT));

            BUSINESS_LOGS.info(logTargetCountriesShift);

            String initialVariantId = network.getVariantManager().getWorkingVariantId();
            String processedVariantId = initialVariantId + " PROCESSED COPY";
            String workingVariantCopyId = initialVariantId + " WORKING COPY";
            preProcessNetwork(network, scalableGeneratorConnector, initialVariantId, processedVariantId, workingVariantCopyId);
            List<String> limitingCountries = new ArrayList<>();

            for (Map.Entry<String, Double> entry : scalingValuesByCountry.entrySet()) {
                String zoneId = entry.getKey();
                double asked = entry.getValue();
                String logApplyingVariationOnZone = String.format("Applying variation on zone %s (target: %.2f)", zoneId, asked);
                BUSINESS_LOGS.info(logApplyingVariationOnZone);
                ScalingParameters scalingParameters = new ScalingParameters();
                scalingParameters.setPriority(ScalingParameters.Priority.RESPECT_OF_VOLUME_ASKED);
                scalingParameters.setReconnect(true);
                double done = zonalScalable.getData(zoneId).scale(network, asked, scalingParameters);
                if (Math.abs(done - asked) > shiftEpsilon) {
                    String logWarnIncompleteVariation = String.format("Incomplete variation on zone %s (target: %.2f, done: %.2f)", zoneId, asked, done);
                    BUSINESS_WARNS.warn(logWarnIncompleteVariation);
                    limitingCountries.add(zoneId);
                }
            }
            if (!limitingCountries.isEmpty()) {
                StringJoiner sj = new StringJoiner(", ", "There are Glsk limitation(s) in ", ".");
                limitingCountries.forEach(sj::add);
                BUSINESS_WARNS.warn("{}", sj.toString());
                throw new GlskLimitationException(sj.toString());
            }
            network.getVariantManager().cloneVariant(workingVariantCopyId, initialVariantId, true);

            // Step 5: Reset current variant with initial state
            network.getVariantManager().setWorkingVariant(initialVariantId);
            network.getVariantManager().removeVariant(processedVariantId);
            network.getVariantManager().removeVariant(workingVariantCopyId);
        } finally {
            // revert connections of TWT on generators that were not used by the scaling
            scalableGeneratorConnector.revertUnnecessaryChanges(network);
        }

    }

    private void shiftIterations(Network network, Map<Country, Double> scalingValuesByCountry, List<String> limitingCountries, ScalingParameters scalingParameters) throws GlskLimitationException {
        for (Map.Entry<Country, Double> entry : scalingValuesByCountry.entrySet()) {
            String zoneId = entry.getKey().toString();
            double asked = entry.getValue();
            String logApplyingVariationOnZone = String.format("Applying variation on zone %s (target: %.2f)", zoneId, asked);
            BUSINESS_LOGS.info(logApplyingVariationOnZone);
            double done = zonalScalable.getData(zoneId).scale(network, asked, scalingParameters);
            if (Math.abs(done - asked) > shiftEpsilon) {
                String logWarnIncompleteVariation = String.format("Incomplete variation on zone %s (target: %.2f, done: %.2f)", zoneId, asked, done);
                BUSINESS_WARNS.warn(logWarnIncompleteVariation);
                limitingCountries.add(zoneId);
            }
        }
        if (!limitingCountries.isEmpty()) {
            StringJoiner sj = new StringJoiner(", ", "There are Glsk limitation(s) in ", ".");
            limitingCountries.forEach(sj::add);
            BUSINESS_WARNS.error("{}", sj);
            throw new GlskLimitationException(sj.toString());
        }
    }

    private void preProcessNetwork(Network network, ScalableGeneratorConnector scalableGeneratorConnector, String initialVariantId, String processedVariantId, String workingVariantCopyId) throws ShiftingException {
        network.getVariantManager().cloneVariant(initialVariantId, processedVariantId, true);
        network.getVariantManager().setWorkingVariant(processedVariantId);
        scalableGeneratorConnector.prepareForScaling(network, Set.of(Country.ES, Country.FR, Country.PT));
        network.getVariantManager().cloneVariant(processedVariantId, workingVariantCopyId, true);
        network.getVariantManager().setWorkingVariant(workingVariantCopyId);
    }
}
