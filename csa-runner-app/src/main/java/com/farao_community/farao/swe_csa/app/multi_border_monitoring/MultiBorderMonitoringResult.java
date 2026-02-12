package com.farao_community.farao.swe_csa.app.multi_border_monitoring;

import com.powsybl.openrao.commons.PhysicalParameter;
import com.powsybl.openrao.data.crac.api.cnec.Cnec;
import com.powsybl.openrao.monitoring.results.MonitoringResult;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class MultiBorderMonitoringResult {

    private final Map<Border, MonitoringResult> resultPerBorder;

    public MultiBorderMonitoringResult(Map<Border, MonitoringResult> resultPerBorder) {
        this.resultPerBorder = Objects.requireNonNull(resultPerBorder);
    }

    public MonitoringResult getMonitoringResultForBorder(Border border) {
        return resultPerBorder.get(border);
    }

    public Map<Border, MonitoringResult> getAllResults() {
        return resultPerBorder;
    }

    public void combine(Border border, MonitoringResult result) {
        MonitoringResult existingResult = resultPerBorder.get(border);
        if (existingResult != null && result != null) {
            existingResult.combine(result);
        }
    }

    public boolean allFailed() {
        return resultPerBorder.values().stream()
                .allMatch(v -> Objects.equals(v.getStatus(), Cnec.SecurityStatus.FAILURE));
    }

    public static MultiBorderMonitoringResult createSecureResults(Collection<Border> borders, PhysicalParameter physicalParameter) {
        Map<Border, MonitoringResult> map = borders.stream()
                .collect(Collectors.toMap(border -> border,
                        border -> new MonitoringResult(physicalParameter, Collections.emptySet(), Collections.emptyMap(), Cnec.SecurityStatus.SECURE)));
        return new MultiBorderMonitoringResult(map);
    }

}

