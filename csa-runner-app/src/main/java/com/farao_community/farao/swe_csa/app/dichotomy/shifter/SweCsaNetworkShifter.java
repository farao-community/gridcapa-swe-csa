package com.farao_community.farao.swe_csa.app.dichotomy.shifter;

/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import com.farao_community.farao.dichotomy.api.exceptions.GlskLimitationException;
import com.farao_community.farao.dichotomy.api.exceptions.ShiftingException;
import com.farao_community.farao.gridcapa_swe_commons.shift.CountryBalanceComputation;
import com.farao_community.farao.gridcapa_swe_commons.shift.ScalableGeneratorConnector;
import com.farao_community.farao.swe_csa.app.dichotomy.dispatcher.ShiftDispatcher;
import com.farao_community.farao.swe_csa.app.dichotomy.variable.MultipleDichotomyVariables;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.glsk.commons.ZonalData;
import com.powsybl.iidm.modification.scalable.Scalable;
import com.powsybl.iidm.modification.scalable.ScalingParameters;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import static com.powsybl.openrao.commons.logs.OpenRaoLoggerProvider.BUSINESS_LOGS;
import static com.powsybl.openrao.commons.logs.OpenRaoLoggerProvider.BUSINESS_WARNS;

/**
 * @author Jean-Pierre Arnould {@literal <jean-pierre.arnould at rte-france.com>}
 */

public final class SweCsaNetworkShifter implements NetworkShifter<MultipleDichotomyVariables> {
    private static final double DEFAULT_EPSILON = 1e-3;
    private static final double DEFAULT_TOLERANCE_ES_PT = 1;
    private static final double DEFAULT_TOLERANCE_ES_FR = 1;
    private static final double DEFAULT_MAX_SHIFT_ITERATION = 10;
    public static final String ES_PT = "ES_PT";
    public static final String ES_FR = "ES_FR";

    private final ZonalData<Scalable> zonalScalable;
    private final ShiftDispatcher<MultipleDichotomyVariables>  shiftDispatcher;
    private final double shiftEpsilon;

    public SweCsaNetworkShifter(ZonalData<Scalable> zonalScalable, ShiftDispatcher<MultipleDichotomyVariables> shiftDispatcher) {
        this(zonalScalable, shiftDispatcher, DEFAULT_EPSILON);
    }

    public SweCsaNetworkShifter(ZonalData<Scalable> zonalScalable, ShiftDispatcher<MultipleDichotomyVariables> shiftDispatcher, double shiftEpsilon) {
        this.zonalScalable = zonalScalable;
        this.shiftDispatcher = shiftDispatcher;
        this.shiftEpsilon = shiftEpsilon;
    }

    @Override
    public void shiftNetwork(MultipleDichotomyVariables stepValue, Network network) throws GlskLimitationException, ShiftingException {

        BUSINESS_LOGS.info("Starting shift on network {}", network.getVariantManager().getWorkingVariantId());
        Map<String, Double> scalingValuesByCountry = shiftDispatcher.dispatch(stepValue);
        // here set working variant generators pmin and pmax values to default values
        // so that glsk generator pmin and pmax values are used
        final Set<String> zoneIds = scalingValuesByCountry.keySet();
        ScalableGeneratorConnector scalableGeneratorConnector = new ScalableGeneratorConnector(zonalScalable);

        try {
            String logTargetCountriesShift = String.format("Target countries shift [ES = %.2f, FR = %.2f, PT = %.2f]",
                scalingValuesByCountry.get(Country.ES.getName()), scalingValuesByCountry.get(Country.FR.getName()), scalingValuesByCountry.get(Country.PT.getName()));
            BUSINESS_LOGS.info(logTargetCountriesShift);
            Map<String, Double> targetExchanges = getTargetExchanges(stepValue);
            int iterationCounter = 0;
            boolean shiftSucceed = false;

            String initialVariantId = network.getVariantManager().getWorkingVariantId();
            String processedVariantId = initialVariantId + " PROCESSED COPY";
            String workingVariantCopyId = initialVariantId + " WORKING COPY";
            preProcessNetwork(network, scalableGeneratorConnector, initialVariantId, processedVariantId, workingVariantCopyId);
            List<String> limitingCountries = new ArrayList<>();
            Map<String, Double> bordersExchanges;

            do {
                // Step 1: Perform the scaling
                BUSINESS_LOGS.info("Applying shift iteration {} ", iterationCounter);
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
                    BUSINESS_WARNS.error("{}", sj);
                    throw new GlskLimitationException(sj.toString());
                }

                // Step 2: Compute exchanges mismatch
                LoadFlowResult result = LoadFlow.run(network, workingVariantCopyId, LocalComputationManager.getDefault(), LoadFlowParameters.load());
                if (!result.isOk()) {
                    String message = String.format("Loadflow computation diverged on network '%s' during balancing adjustment", network.getId());
                    throw new ShiftingException(message);
                }
                bordersExchanges = CountryBalanceComputation.computeSweBordersExchanges(network);
                double mismatchEsPt = targetExchanges.get(ES_PT) - bordersExchanges.get(ES_PT);
                double mismatchEsFr = targetExchanges.get(ES_FR) - bordersExchanges.get(ES_FR);

                // Step 3: Checks balance adjustment results
                if (Math.abs(mismatchEsPt) < DEFAULT_TOLERANCE_ES_PT && Math.abs(mismatchEsFr) < DEFAULT_TOLERANCE_ES_FR) {
                    String logShiftSucceded = String.format("Shift succeed after %s iteration ", ++iterationCounter);
                    BUSINESS_LOGS.info(logShiftSucceded);
                    BUSINESS_LOGS.info("Shift succeed after {} iteration ", ++iterationCounter);
                    String msg = String.format("Exchange ES-PT = %.2f , Exchange ES-FR =  %.2f", bordersExchanges.get(ES_PT), bordersExchanges.get(ES_FR));
                    BUSINESS_LOGS.info(msg);
                    network.getVariantManager().cloneVariant(workingVariantCopyId, initialVariantId, true);
                    shiftSucceed = true;
                } else {
                    // Reset current variant with initial state for each iteration (keeping pre-processing)
                    network.getVariantManager().cloneVariant(processedVariantId, workingVariantCopyId, true);
                    updateScalingValuesWithMismatch(scalingValuesByCountry, mismatchEsPt, mismatchEsFr);
                    ++iterationCounter;
                }

            } while (iterationCounter < DEFAULT_MAX_SHIFT_ITERATION && !shiftSucceed);

            // Step 4 : check after iteration max and out of tolerance
            if (!shiftSucceed) {
                String message = String.format("Balancing adjustment out of tolerances : Exchange ES-PT = %.2f , Exchange ES-FR =  %.2f", bordersExchanges.get(ES_PT), bordersExchanges.get(ES_FR));
                BUSINESS_LOGS.error(message);
                throw new ShiftingException(message);
            }

            // Step 5: Reset current variant with initial state
            network.getVariantManager().setWorkingVariant(initialVariantId);
            network.getVariantManager().removeVariant(processedVariantId);
            network.getVariantManager().removeVariant(workingVariantCopyId);
        } finally {
            // revert connections of TWT on generators that were not used by the scaling
            scalableGeneratorConnector.revertUnnecessaryChanges(network);
        }
    }

    public ShiftDispatcher<MultipleDichotomyVariables> getShiftDispatcher() {
        return shiftDispatcher;
    }

    private void preProcessNetwork(Network network, ScalableGeneratorConnector scalableGeneratorConnector, String initialVariantId, String processedVariantId, String workingVariantCopyId) throws ShiftingException {
        network.getVariantManager().cloneVariant(initialVariantId, processedVariantId, true);
        network.getVariantManager().setWorkingVariant(processedVariantId);
        scalableGeneratorConnector.prepareForScaling(network, Set.of(Country.ES, Country.FR, Country.PT));
        network.getVariantManager().cloneVariant(processedVariantId, workingVariantCopyId, true);
        network.getVariantManager().setWorkingVariant(workingVariantCopyId);
    }

    private Map<String, Double> getTargetExchanges(MultipleDichotomyVariables stepValue) {
        Map<String, Double> targetExchanges = new HashMap<>();
        targetExchanges.put(ES_PT, -stepValue.values().get(Country.PT.getName()));
        targetExchanges.put(ES_FR, -stepValue.values().get(Country.FR.getName()));
        return targetExchanges;
    }

    public void updateScalingValuesWithMismatch(Map<String, Double> scalingValuesByCountry, double mismatchEsPt, double mismatchEsFr) {
        scalingValuesByCountry.put(Country.FR.getName(), scalingValuesByCountry.get(Country.FR.getName()) - mismatchEsFr);
        scalingValuesByCountry.put(Country.PT.getName(), scalingValuesByCountry.get(Country.PT.getName()) - mismatchEsPt);
        scalingValuesByCountry.put(Country.ES.getName(), scalingValuesByCountry.get(Country.ES.getName()) + mismatchEsPt + mismatchEsFr);
    }
}
