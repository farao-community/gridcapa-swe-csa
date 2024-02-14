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

import com.farao_community.farao.dichotomy.api.exceptions.DichotomyException;
import com.farao_community.farao.dichotomy.api.exceptions.GlskLimitationException;
import com.farao_community.farao.dichotomy.api.exceptions.ShiftingException;
import com.farao_community.farao.dichotomy.api.exceptions.ValidationException;
import com.farao_community.farao.dichotomy.api.results.DichotomyStepResult;
import com.farao_community.farao.dichotomy.api.results.ReasonInvalid;
import com.farao_community.farao.rao_runner.api.resource.RaoResponse;
import com.farao_community.farao.swe_csa.app.dichotomy.index.Index;
import com.farao_community.farao.swe_csa.app.dichotomy.index.IndexStrategy;
import com.farao_community.farao.swe_csa.app.dichotomy.shifter.NetworkShifter;
import com.farao_community.farao.swe_csa.app.dichotomy.variable.MultipleDichotomyVariables;
import com.powsybl.iidm.network.Network;

import java.util.Objects;

import static com.farao_community.farao.commons.logs.FaraoLoggerProvider.BUSINESS_LOGS;
import static com.farao_community.farao.dichotomy.api.logging.DichotomyLoggerProvider.BUSINESS_WARNS;

public class SweCsaDichotomyEngine {
    private static final int DEFAULT_MAX_ITERATION_NUMBER = 100;
    private final Index<RaoResponse, MultipleDichotomyVariables> index;
    private final IndexStrategy<MultipleDichotomyVariables> indexStrategy;
    private final NetworkShifter<MultipleDichotomyVariables> networkShifter;
    private final SweCsaRaoValidator networkValidator;
    private final int maxIteration;

    public SweCsaDichotomyEngine(Index<RaoResponse, MultipleDichotomyVariables> index, IndexStrategy<MultipleDichotomyVariables> indexStrategy, NetworkShifter<MultipleDichotomyVariables> networkShifter, SweCsaRaoValidator networkValidator) {
        this(index, indexStrategy, networkShifter, networkValidator, DEFAULT_MAX_ITERATION_NUMBER);
    }

    public SweCsaDichotomyEngine(Index<RaoResponse, MultipleDichotomyVariables> index, IndexStrategy<MultipleDichotomyVariables> indexStrategy, NetworkShifter<MultipleDichotomyVariables> networkShifter, SweCsaRaoValidator networkValidator, int maxIteration) {
        if (maxIteration < 3) {
            throw new DichotomyException("Max number of iterations of the dichotomy engine should be at least 3.");
        }
        this.index = Objects.requireNonNull(index);
        this.indexStrategy = Objects.requireNonNull(indexStrategy);
        this.networkShifter = networkShifter;
        this.networkValidator = Objects.requireNonNull(networkValidator);
        this.maxIteration = maxIteration;
    }

    public DichotomyResult<RaoResponse, MultipleDichotomyVariables> run(Network network) {
        int iterationCounter = 0;
        String initialVariant = network.getVariantManager().getWorkingVariantId();
        while (!indexStrategy.precisionReached(index) && iterationCounter < maxIteration) {
            MultipleDichotomyVariables nextValue = indexStrategy.nextValue(index);
            BUSINESS_LOGS.info(String.format("Next dichotomy step: %s", nextValue.print()));
            DichotomyStepResult<RaoResponse> lastDichotomyStepResult = !index.testedSteps().isEmpty() ? index.testedSteps().get(index.testedSteps().size() - 1).getRight() : null;
            DichotomyStepResult<RaoResponse> dichotomyStepResult = validate(nextValue, network, initialVariant, lastDichotomyStepResult);
            if (dichotomyStepResult.isValid()) {
                BUSINESS_LOGS.info(String.format("Network at dichotomy step %s is secure", nextValue.print()));
            } else {
                BUSINESS_LOGS.info(String.format("Network at dichotomy step %s is unsecure", nextValue.print()));
            }
            index.addDichotomyStepResult(nextValue, dichotomyStepResult);
            iterationCounter++;
        }

        if (iterationCounter == maxIteration) {
            BUSINESS_WARNS.warn("Max number of iteration {} reached during dichotomy, research precision has not been reached.", maxIteration);
        }
        return DichotomyResult.buildFromIndex(index);
    }

    private DichotomyStepResult<RaoResponse> validate(MultipleDichotomyVariables stepValue, Network network, String initialVariant, DichotomyStepResult<RaoResponse> lastDichotomyStepResult) {
        String newVariant = variantName(stepValue, initialVariant);
        network.getVariantManager().cloneVariant(initialVariant, newVariant);
        network.getVariantManager().setWorkingVariant(newVariant);
        try {
            networkShifter.shiftNetwork(stepValue, network);
            networkValidator.setCounterTradingValue(stepValue);
            return networkValidator.validateNetwork(network, lastDichotomyStepResult);
        } catch (GlskLimitationException e) {
            BUSINESS_WARNS.warn(String.format("GLSK limits have been reached for step value %s", stepValue.print()));
            return DichotomyStepResult.fromFailure(ReasonInvalid.GLSK_LIMITATION, e.getMessage());
        } catch (ShiftingException | ValidationException e) {
            BUSINESS_WARNS.warn(String.format("Validation failed for step value %s", stepValue.print()));
            return DichotomyStepResult.fromFailure(ReasonInvalid.VALIDATION_FAILED, e.getMessage());
        } finally {
            network.getVariantManager().setWorkingVariant(initialVariant);
            network.getVariantManager().removeVariant(newVariant);
        }
    }

    private String variantName(MultipleDichotomyVariables stepValue, String initialVariant) {
        return String.format("%s-ScaledBy-%s", initialVariant, stepValue.print());
    }
}
