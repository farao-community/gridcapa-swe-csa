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
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import static com.farao_community.farao.commons.logs.FaraoLoggerProvider.BUSINESS_LOGS;
import static com.farao_community.farao.commons.logs.FaraoLoggerProvider.BUSINESS_WARNS;

/**
 * @author Jean-Pierre Arnould {@literal <jean-pierre.arnould at rte-france.com>}
 */

public final class SweCsaNetworkShifter implements NetworkShifter<MultipleDichotomyVariables> {
    private static final double DEFAULT_EPSILON = 1e-3;

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

    @Override
    public void shiftNetwork(MultipleDichotomyVariables stepValue, Network network) throws GlskLimitationException, ShiftingException {

        BUSINESS_LOGS.info("Starting shift on network {}", network.getVariantManager().getWorkingVariantId());
        Map<String, Double> scalingValuesByCountry = shiftDispatcher.dispatch(stepValue);
        // here set working variant generators pmin and pmax values to default values
        // so that glsk generator pmin and pmax values are used
        final Set<String> zoneIds = scalingValuesByCountry.keySet();
        Map<String, InitGenerator> initGenerators = setPminPmaxToDefaultValue(network, zonalScalable, zoneIds);
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

            int maxIterationNumber = processConfiguration.getShiftMaxIterationNumber();
            do {
                // Step 1: Perform the scaling
                LOGGER.info("[{}] : Applying shift iteration {} ", direction, iterationCounter);
                for (Map.Entry<String, Double> entry : scalingValuesByCountry.entrySet()) {
                    String zoneId = entry.getKey();
                    double asked = entry.getValue();
                    String logApplyingVariationOnZone = String.format("[%s] : Applying variation on zone %s (target: %.2f)", direction, zoneId, asked);
                    LOGGER.info(logApplyingVariationOnZone);
                    ScalingParameters scalingParameters = new ScalingParameters();
                    scalingParameters.setIterative(true);
                    scalingParameters.setReconnect(true);
                    double done = zonalScalable.getData(zoneId).scale(network, asked, scalingParameters);
                    if (Math.abs(done - asked) > DEFAULT_SHIFT_EPSILON) {
                        String logWarnIncompleteVariation = String.format("[%s] : Incomplete variation on zone %s (target: %.2f, done: %.2f)",
                            direction, zoneId, asked, done);
                        LOGGER.warn(logWarnIncompleteVariation);
                        limitingCountries.add(zoneId);
                    }
                }
                if (!limitingCountries.isEmpty()) {
                    StringJoiner sj = new StringJoiner(", ", "There are Glsk limitation(s) in ", ".");
                    limitingCountries.forEach(sj::add);
                    LOGGER.error("[{}] : {}", direction, sj);
                    throw new GlskLimitationException(sj.toString());
                }

                // Step 2: Compute exchanges mismatch
                LoadFlowResult result = LoadFlow.run(network, workingVariantCopyId, LocalComputationManager.getDefault(), LoadFlowParameters.load());
                if (!result.isOk()) {
                    LOGGER.error("Loadflow computation diverged on network '{}' for direction {}", network.getId(), direction.getDashName());
                    businessLogger.error("Loadflow computation diverged on network during balancing adjustment");
                    throw new ShiftingException("Loadflow computation diverged during balancing adjustment");
                }
                bordersExchanges = CountryBalanceComputation.computeSweBordersExchanges(network);
                double mismatchEsPt = targetExchanges.get(ES_PT) - bordersExchanges.get(ES_PT);
                double mismatchEsFr = targetExchanges.get(ES_FR) - bordersExchanges.get(ES_FR);

                // Step 3: Checks balance adjustment results
                if (Math.abs(mismatchEsPt) < toleranceEsPt && Math.abs(mismatchEsFr) < toleranceEsFr) {
                    String logShiftSucceded = String.format("[%s] : Shift succeed after %s iteration ", direction, ++iterationCounter);
                    LOGGER.info(logShiftSucceded);
                    businessLogger.info("Shift succeed after {} iteration ", ++iterationCounter);
                    String msg = String.format("Exchange ES-PT = %.2f , Exchange ES-FR =  %.2f", bordersExchanges.get(ES_PT), bordersExchanges.get(ES_FR));
                    businessLogger.info(msg);
                    network.getVariantManager().cloneVariant(workingVariantCopyId, initialVariantId, true);
                    shiftSucceed = true;
                } else {
                    // Reset current variant with initial state for each iteration (keeping pre-processing)
                    network.getVariantManager().cloneVariant(processedVariantId, workingVariantCopyId, true);
                    updateScalingValuesWithMismatch(scalingValuesByCountry, mismatchEsPt, mismatchEsFr);
                    ++iterationCounter;
                }

            } while (iterationCounter < maxIterationNumber && !shiftSucceed);

            // Step 4 : check after iteration max and out of tolerance
            if (!shiftSucceed) {
                String message = String.format("Balancing adjustment out of tolerances : Exchange ES-PT = %.2f , Exchange ES-FR =  %.2f", bordersExchanges.get(ES_PT), bordersExchanges.get(ES_FR));
                businessLogger.error(message);
                throw new ShiftingException(message);
            }

            // Step 5: Reset current variant with initial state
            network.getVariantManager().setWorkingVariant(initialVariantId);
            network.getVariantManager().removeVariant(processedVariantId);
            network.getVariantManager().removeVariant(workingVariantCopyId);
        } finally {
            // revert connections of TWT on generators that were not used by the scaling
            scalableGeneratorConnector.revertUnnecessaryChanges(network);
            // here set working variant generators pmin and pmax values to initial values
            resetInitialPminPmax(network, zonalScalable, zoneIds, initGenerators);
        }
    }

    private Map<String, InitGenerator> setPminPmaxToDefaultValue(Network network, ZonalData<Scalable> scalableZonalData, Set<String> zonesIds) {
        Map<String, InitGenerator> initGenerators = new HashMap<>();
        zonesIds.stream()
            //filter out FRANCE because it is always in proportional and not absolute values
            .filter(zoneId -> !zoneId.equals(toEic("FR")))
            .map(scalableZonalData::getData)
            .filter(Objects::nonNull)
            .map(scalable -> scalable.filterInjections(network).stream()
                .filter(Generator.class::isInstance)
                .map(Generator.class::cast)
                .collect(Collectors.toList())).forEach(generators -> generators.forEach(generator -> {
                if (Double.isNaN(generator.getTargetP())) {
                    generator.setTargetP(0.);
                }
                InitGenerator initGenerator = new InitGenerator();
                initGenerator.setpMin(generator.getMinP());
                initGenerator.setpMax(generator.getMaxP());
                String genId = generator.getId();
                if (!initGenerators.containsKey(genId)) {
                    initGenerators.put(genId, initGenerator);
                }
                generator.setMinP(DEFAULT_PMIN);
                generator.setMaxP(DEFAULT_PMAX);
            }));
        LOGGER.info("Pmax and Pmin are set to default values for network {}", network.getNameOrId());
        return initGenerators;
    }

    public ShiftDispatcher<MultipleDichotomyVariables> getShiftDispatcher() {
        return shiftDispatcher;
    }

    private static class InitGenerator {
        double pMin;
        double pMax;

        public double getpMin() {
            return pMin;
        }

        public void setpMin(double pMin) {
            this.pMin = pMin;
        }

        public double getpMax() {
            return pMax;
        }

        public void setpMax(double pMax) {
            this.pMax = pMax;
        }
    }
}
