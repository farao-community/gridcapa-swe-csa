package com.farao_community.farao.swe_csa.api.results;

import com.farao_community.farao.data.crac_api.RemedialAction;
import com.farao_community.farao.data.crac_api.State;
import com.farao_community.farao.data.crac_api.range_action.CounterTradeRangeAction;
import com.farao_community.farao.data.crac_api.range_action.RangeAction;

import java.util.*;

public class CounterTradingResult {
    private final Map<CounterTradeRangeAction, CounterTradeRangeActionResult> counterTradeRangeActionResults;

    public CounterTradingResult(Map<CounterTradeRangeAction, CounterTradeRangeActionResult> counterTradeRangeActionResults) {
        this.counterTradeRangeActionResults = counterTradeRangeActionResults;
    }

    public Map<CounterTradeRangeAction, CounterTradeRangeActionResult> getCounterTradeRangeActionResults() {
        return counterTradeRangeActionResults;
    }

    public boolean isActivatedDuringState(State state, RangeAction<?> rangeAction) {
        return isActivatedDuringState(state, (RemedialAction<?>) rangeAction);
    }

    public boolean isActivatedDuringState(State state, RemedialAction<?> remedialAction) {
        if (state.isPreventive() && counterTradeRangeActionResults.containsKey(remedialAction)) {
            return counterTradeRangeActionResults.get(remedialAction).getSetPoint() != 0;
        }
        return false;
    }

    public double getPreOptimizationSetPointOnState(State state, CounterTradeRangeAction counterTradeRangeAction) {
        if (state.isPreventive()) {
            return counterTradeRangeAction.getInitialSetpoint();
        } else {
            return counterTradeRangeActionResults.get(counterTradeRangeAction).getSetPoint();
        }
    }

    public double getOptimizedSetPointOnState(State state, CounterTradeRangeAction counterTradeRangeAction) {
        return counterTradeRangeActionResults.get(counterTradeRangeAction).getSetPoint();
        // TODO add curative results when curative CT is implemented
    }

    public Set<RangeAction<?>> getActivatedRangeActionsDuringState(State state) {
        Set<RangeAction<?>> activatedCounterTradeActions = new HashSet<>();
        if (state.isPreventive()) {
            counterTradeRangeActionResults.forEach((k, v) -> {
                if (v.getSetPoint() != 0.) {
                    activatedCounterTradeActions.add(k);
                }
            });
            return activatedCounterTradeActions;
        } else {
            return activatedCounterTradeActions;
            // TODO add curative results when curative CT is implemented
        }
    }

}


