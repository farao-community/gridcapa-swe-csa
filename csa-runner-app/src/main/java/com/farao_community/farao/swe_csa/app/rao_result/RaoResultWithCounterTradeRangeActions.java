package com.farao_community.farao.swe_csa.app.rao_result;

import com.farao_community.farao.data.crac_api.RemedialAction;
import com.farao_community.farao.data.crac_api.State;
import com.farao_community.farao.data.crac_api.cnec.Cnec;
import com.farao_community.farao.data.crac_api.range_action.CounterTradeRangeAction;
import com.farao_community.farao.data.crac_api.range_action.RangeAction;
import com.farao_community.farao.data.rao_result_api.RaoResult;
import com.farao_community.farao.data.rao_result_api.RaoResultClone;
import com.farao_community.farao.swe_csa.api.results.CounterTradingResult;
import com.powsybl.iidm.network.Country;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class RaoResultWithCounterTradeRangeActions extends RaoResultClone {

    private final RaoResult raoResult;

    private final CounterTradingResult counterTradingResult;

    public RaoResultWithCounterTradeRangeActions(RaoResult raoResult, CounterTradingResult counterTradingResult) {
        super(raoResult);
        this.raoResult = raoResult;
        this.counterTradingResult = counterTradingResult;
    }

    @Override
    public boolean isActivatedDuringState(State state, RemedialAction<?> remedialAction) {
        return raoResult.isActivatedDuringState(state, remedialAction)
            || counterTradingResult.isActivatedDuringState(state, remedialAction);
    }

    @Override
    public boolean isActivatedDuringState(State state, RangeAction<?> rangeAction) {
        return raoResult.isActivatedDuringState(state, rangeAction)
            || counterTradingResult.isActivatedDuringState(state, rangeAction);
    }

    @Override
    public double getPreOptimizationSetPointOnState(State state, RangeAction<?> rangeAction) {
        if (rangeAction instanceof CounterTradeRangeAction) {
            return counterTradingResult.getPreOptimizationSetPointOnState(state, (CounterTradeRangeAction) rangeAction);
        }
        return raoResult.getPreOptimizationSetPointOnState(state, rangeAction);
    }

    @Override
    public double getOptimizedSetPointOnState(State state, RangeAction<?> rangeAction) {
        if (rangeAction instanceof CounterTradeRangeAction) {
            return counterTradingResult.getOptimizedSetPointOnState(state, (CounterTradeRangeAction) rangeAction);
        }
        return raoResult.getOptimizedSetPointOnState(state, rangeAction);
    }

    @Override
    public Set<RangeAction<?>> getActivatedRangeActionsDuringState(State state) {
        Set<RangeAction<?>> overallActivatedRangeActions = new HashSet<>();
        overallActivatedRangeActions.addAll(counterTradingResult.getActivatedRangeActionsDuringState(state));
        overallActivatedRangeActions.addAll(raoResult.getActivatedRangeActionsDuringState(state));
        return overallActivatedRangeActions;
    }

    @Override
    public Map<RangeAction<?>, Double> getOptimizedSetPointsOnState(State state) {
        // TODO to be tested when we have counter trade actions, we are not sure that range action keyset contains CT range actions , otherwise we have to fetch them from CounterTradeResult
        return raoResult.getOptimizedSetPointsOnState(state);
    }

    public Set<Cnec> getCnecsOnConstraintForCountry(Country country) {
        Set<Cnec> cnecs = new HashSet<>();
        return cnecs;
    }

}
