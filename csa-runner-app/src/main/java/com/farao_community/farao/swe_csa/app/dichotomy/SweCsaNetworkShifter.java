package com.farao_community.farao.swe_csa.app.dichotomy;

/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

import com.farao_community.farao.dichotomy.api.exceptions.GlskLimitationException;
import com.farao_community.farao.dichotomy.api.exceptions.ShiftingException;
import com.farao_community.farao.gridcapa_swe_commons.shift.CountryBalanceComputation;
import com.farao_community.farao.gridcapa_swe_commons.shift.SweGeneratorsShiftHelper;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.glsk.commons.ZonalData;
import com.powsybl.iidm.modification.scalable.Scalable;
import com.powsybl.iidm.modification.scalable.ScalingParameters;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;

import java.util.*;

import static com.powsybl.openrao.commons.logs.OpenRaoLoggerProvider.BUSINESS_LOGS;
import static com.powsybl.openrao.commons.logs.OpenRaoLoggerProvider.BUSINESS_WARNS;

/**
 * @author Jean-Pierre Arnould {@literal <jean-pierre.arnould at rte-france.com>}
 */

public final class SweCsaNetworkShifter {
    private static final double DEFAULT_EPSILON = 1e-3;
    private static final double DEFAULT_TOLERANCE_ES_PT = 1;
    private static final double DEFAULT_TOLERANCE_ES_FR = 1;
    private static final double MAX_ITERATIONS_BY_SHIFT = 10;
    public static final String ES_PT = "ES_PT";
    public static final String ES_FR = "ES_FR";
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
        SweGeneratorsShiftHelper sweGeneratorsShiftHelper = new SweGeneratorsShiftHelper(zonalScalable);

        BUSINESS_LOGS.info("Starting shift on network {}", network.getVariantManager().getWorkingVariantId());
        Map<Country, Double> scalingValuesByCountry = shiftDispatcher.dispatch(counterTradingValues);

        try {
            String logTargetCountriesShift = String.format("Target shifts by country: [ES = %.2f, FR = %.2f, PT = %.2f]",
                scalingValuesByCountry.get(Country.ES), scalingValuesByCountry.get(Country.FR), scalingValuesByCountry.get(Country.PT));
            BUSINESS_LOGS.info(logTargetCountriesShift);
            int iterationCounter = 1;
            boolean shiftSucceed = false;

            String initialVariantId = network.getVariantManager().getWorkingVariantId();
            String processedVariantId = initialVariantId + " PROCESSED COPY";
            String workingVariantCopyId = initialVariantId + " WORKING COPY";
            sweGeneratorsShiftHelper.preProcessNetwork(network, initialVariantId, processedVariantId, workingVariantCopyId);
            List<String> limitingCountries = new ArrayList<>();
            Map<String, Double> bordersExchanges;

            do {
                // Step 1: Perform the scaling
                BUSINESS_LOGS.info("Applying shift iteration {} ", iterationCounter);
                shiftIterations(network, scalingValuesByCountry, limitingCountries, SweGeneratorsShiftHelper.getScalingParameters());
                sweGeneratorsShiftHelper.connectGeneratorsTransformers(network);

                // Step 2: Compute exchanges mismatch
                LoadFlowResult result = LoadFlow.run(network, workingVariantCopyId, LocalComputationManager.getDefault(), LoadFlowParameters.load());
                if (result.isFailed()) {
                    String message = String.format("Loadflow computation diverged on network '%s' during balancing adjustment", network.getId());
                    throw new ShiftingException(message);
                }
                bordersExchanges = CountryBalanceComputation.computeSweBordersExchanges(network);
                double mismatchEsPt = -counterTradingValues.getPtEsCt() - bordersExchanges.get(ES_PT);
                double mismatchEsFr = -counterTradingValues.getFrEsCt() - bordersExchanges.get(ES_FR);

                // Step 3: Checks balance adjustment results
                if (Math.abs(mismatchEsPt) < DEFAULT_TOLERANCE_ES_PT && Math.abs(mismatchEsFr) < DEFAULT_TOLERANCE_ES_FR) {
                    String logShiftSucceded = String.format("Shift succeed after %s iteration ", ++iterationCounter);
                    BUSINESS_LOGS.info(logShiftSucceded);
                    String logBordersExchange = String.format("Exchange ES-PT = %.2f , Exchange ES-FR =  %.2f", bordersExchanges.get(ES_PT), bordersExchanges.get(ES_FR));
                    BUSINESS_LOGS.info(logBordersExchange);
                    network.getVariantManager().cloneVariant(workingVariantCopyId, initialVariantId, true);
                    shiftSucceed = true;
                } else {
                    // Reset current variant with initial state for each iteration (keeping pre-processing)
                    network.getVariantManager().cloneVariant(processedVariantId, workingVariantCopyId, true);
                    ++iterationCounter;
                }

            } while (iterationCounter < MAX_ITERATIONS_BY_SHIFT && !shiftSucceed);

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
            sweGeneratorsShiftHelper.resetInitialPminPmax(network);
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

}
