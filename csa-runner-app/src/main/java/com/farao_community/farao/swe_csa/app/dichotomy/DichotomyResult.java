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

import com.farao_community.farao.dichotomy.api.results.DichotomyStepResult;
import com.farao_community.farao.dichotomy.api.results.LimitingCause;
import com.farao_community.farao.dichotomy.api.results.ReasonInvalid;
import com.farao_community.farao.swe_csa.app.dichotomy.index.Index;
import com.farao_community.farao.swe_csa.app.dichotomy.variable.DichotomyVariable;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.tuple.Pair;

public final class DichotomyResult<I, U extends DichotomyVariable<U>> {
    private final Pair<U, DichotomyStepResult<I>> lowestValidStep;
    private final Pair<U, DichotomyStepResult<I>> highestInvalidStep;
    private final LimitingCause limitingCause;
    private final String limitingFailureMessage;

    private DichotomyResult(Pair<U, DichotomyStepResult<I>> lowestValidStep,
                            Pair<U, DichotomyStepResult<I>> highestInvalidStep,
                            LimitingCause limitingCause,
                            String limitingFailureMessage) {
        this.lowestValidStep = lowestValidStep;
        this.highestInvalidStep = highestInvalidStep;
        this.limitingCause = limitingCause;
        this.limitingFailureMessage = limitingFailureMessage;
    }

    public static <J, V extends DichotomyVariable<V>> DichotomyResult<J, V> buildFromIndex(Index<J, V> index) {
        // If one of the steps are null it means that it stops due to index evaluation otherwise it could have continued.
        // If both are present, it is the expected case we just have to differentiate if the invalid step failed or if
        // it is just unsecure.
        LimitingCause limitingCause = LimitingCause.INDEX_EVALUATION_OR_MAX_ITERATION;
        String failureMessage = "None";
        if (index.lowestValidStep() != null && index.highestInvalidStep() != null) {
            if (index.highestInvalidStep().getRight().isFailed()) {
                limitingCause = index.highestInvalidStep().getRight().getReasonInvalid() == ReasonInvalid.GLSK_LIMITATION ?
                    LimitingCause.GLSK_LIMITATION : LimitingCause.COMPUTATION_FAILURE;
                failureMessage = index.highestInvalidStep().getRight().getFailureMessage();
            } else {
                limitingCause = LimitingCause.CRITICAL_BRANCH;
            }
        }

        Pair<V, DichotomyStepResult<J>> highestInvalidStepResponse = index.highestInvalidStep();
        Pair<V, DichotomyStepResult<J>> lowestValidStepResponse = index.lowestValidStep();
        return new DichotomyResult<>(lowestValidStepResponse, highestInvalidStepResponse, limitingCause, failureMessage);
    }

    public DichotomyStepResult<I> getHighestInvalidStep() {
        return highestInvalidStep.getRight();
    }

    public DichotomyStepResult<I> getLowestValidStep() {
        return lowestValidStep.getRight();
    }

    public LimitingCause getLimitingCause() {
        return limitingCause;
    }

    public String getLimitingFailureMessage() {
        return limitingFailureMessage;
    }

    @JsonIgnore
    public boolean hasValidStep() {
        return lowestValidStep != null;
    }

    @JsonIgnore
    public U getHighestInvalidStepValue() {
        return highestInvalidStep != null ? highestInvalidStep.getLeft() : null;
    }

    @JsonIgnore
    public U getLowestValidStepValue() {
        return lowestValidStep != null ? lowestValidStep.getLeft() : null;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
