package com.farao_community.farao.swe_csa.api.results;

import java.util.List;

public class CounterTradeRangeActionResult {
    private String ctRangeActionId;
    private double setPoint;
    private List<String> concernedCnecs;

    public CounterTradeRangeActionResult(String ctRangeActionId, double setPoint, List<String> concernedCnecs) {
        this.ctRangeActionId = ctRangeActionId;
        this.setPoint = setPoint;
        this.concernedCnecs = concernedCnecs;
    }

    public String getCtRangeActionId() {
        return ctRangeActionId;
    }

    public double getSetPoint() {
        return setPoint;
    }

    public List<String> getConcernedCnecs() {
        return concernedCnecs;
    }
}
