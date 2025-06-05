package com.farao_community.farao.swe_csa.api.results;

import java.util.List;

public class CounterTradeRangeActionResult {
    private final String ctRangeActionId;
    private final double setPoint;
    private final List<String> involvedCnecs;

    public CounterTradeRangeActionResult(String ctRangeActionId, double setPoint, List<String> involvedCnecs) {
        this.ctRangeActionId = ctRangeActionId;
        this.setPoint = setPoint;
        this.involvedCnecs = involvedCnecs;
    }

    public String getCtRangeActionId() {
        return ctRangeActionId;
    }

    public double getSetPoint() {
        return setPoint;
    }

    public List<String> getInvolvedCnecs() {
        return involvedCnecs;
    }
}
