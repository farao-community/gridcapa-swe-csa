package com.farao_community.farao.swe_csa.app.rao_result;

import com.powsybl.openrao.commons.Unit;
import com.powsybl.openrao.data.crac.api.Instant;
import com.powsybl.openrao.data.crac.api.RemedialAction;
import com.powsybl.openrao.data.crac.api.State;
import com.powsybl.openrao.data.crac.api.cnec.AngleCnec;
import com.powsybl.openrao.data.crac.api.cnec.VoltageCnec;
import com.powsybl.openrao.data.crac.api.rangeaction.CounterTradeRangeAction;
import com.powsybl.openrao.data.crac.api.rangeaction.RangeAction;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import com.powsybl.openrao.data.raoresult.api.RaoResultClone;
import com.farao_community.farao.swe_csa.api.results.CounterTradingResult;

import java.util.HashMap;
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
        if (rangeAction instanceof CounterTradeRangeAction counterTradeRangeAction) {
            return counterTradingResult.getPreOptimizationSetPointOnState(state, counterTradeRangeAction);
        }
        return raoResult.getPreOptimizationSetPointOnState(state, rangeAction);
    }

    @Override
    public double getOptimizedSetPointOnState(State state, RangeAction<?> rangeAction) {
        if (rangeAction instanceof CounterTradeRangeAction counterTradeRangeAction) {
            return counterTradingResult.getOptimizedSetPointOnState(counterTradeRangeAction);
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
        Map<RangeAction<?>, Double> optimizedSetPointsOnState = new HashMap<>(raoResult.getOptimizedSetPointsOnState(state));
        counterTradingResult.counterTradeRangeActionResults().keySet().forEach(counterTradeRangeAction -> optimizedSetPointsOnState.put(counterTradeRangeAction, counterTradingResult.getOptimizedSetPointOnState(counterTradeRangeAction)));
        return optimizedSetPointsOnState;
    }

    // RaoResultClone does not delegate voltage/angle cnec results to the wrapped RaoResult:
    // it inherits RaoResult's default methods, which throw. Without these overrides, voltage
    // monitoring results computed upstream (see ResultHelper#updateRaoResultWithVoltageMonitoring)
    // would be lost when wrapped here, causing empty results (OpenRAO 7.1.0) or a serialization
    // exception (OpenRAO 7.3.0+, since VoltageCnecResultArraySerializer no longer swallows it).
    @Override
    public double getMinVoltage(Instant optimizedInstant, VoltageCnec voltageCnec, Unit unit) {
        return raoResult.getMinVoltage(optimizedInstant, voltageCnec, unit);
    }

    @Override
    public double getMaxVoltage(Instant optimizedInstant, VoltageCnec voltageCnec, Unit unit) {
        return raoResult.getMaxVoltage(optimizedInstant, voltageCnec, unit);
    }

    @Override
    public double getMargin(Instant optimizedInstant, VoltageCnec voltageCnec, Unit unit) {
        return raoResult.getMargin(optimizedInstant, voltageCnec, unit);
    }

    @Override
    public double getAngle(Instant optimizedInstant, AngleCnec angleCnec, Unit unit) {
        return raoResult.getAngle(optimizedInstant, angleCnec, unit);
    }

    @Override
    public double getMargin(Instant optimizedInstant, AngleCnec angleCnec, Unit unit) {
        return raoResult.getMargin(optimizedInstant, angleCnec, unit);
    }

}
