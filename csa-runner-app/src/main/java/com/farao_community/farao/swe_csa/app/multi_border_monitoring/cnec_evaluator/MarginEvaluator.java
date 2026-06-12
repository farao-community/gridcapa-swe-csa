package com.farao_community.farao.swe_csa.app.multi_border_monitoring.cnec_evaluator;

import com.farao_community.farao.swe_csa.app.multi_border_monitoring.Border;
import com.farao_community.farao.swe_csa.app.multi_border_monitoring.MultiBorderMonitoringInput;
import com.farao_community.farao.swe_csa.app.multi_border_monitoring.MultiBorderMonitoringResult;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openrao.commons.PhysicalParameter;
import com.powsybl.openrao.commons.Unit;
import com.powsybl.openrao.data.crac.api.State;
import com.powsybl.openrao.data.crac.api.cnec.Cnec;
import com.powsybl.openrao.data.crac.api.cnec.CnecValue;
import com.powsybl.openrao.monitoring.results.CnecResult;
import com.powsybl.openrao.monitoring.results.MonitoringResult;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

import static com.farao_community.farao.swe_csa.app.multi_border_monitoring.MonitoringUtils.computeLoadFlow;
import static com.farao_community.farao.swe_csa.app.multi_border_monitoring.MonitoringUtils.makeFailedMonitoringResultForStateWithNaNCnecResults;

public class MarginEvaluator implements CnecEvaluator {
    private final MultiBorderMonitoringInput multiBorderMonitoringInput;
    private final String loadFlowProvider;
    private final LoadFlowParameters loadFlowParameters;
    private final Logger businessLogger;

    public MarginEvaluator(MultiBorderMonitoringInput multiBorderMonitoringInput, Logger businessLogger) {
        this.multiBorderMonitoringInput = multiBorderMonitoringInput;
        this.loadFlowProvider = multiBorderMonitoringInput.getLoadFlowProvider();
        this.loadFlowParameters = multiBorderMonitoringInput.getLoadFlowParameters();
        this.businessLogger = businessLogger;
    }

    @Override
    public MultiBorderMonitoringResult evaluate(Network network, State state, Map<Border, Set<Cnec>> cnecsToEvaluatePerBorder) {
        PhysicalParameter physicalParameter = multiBorderMonitoringInput.getPhysicalParameter();
        Set<Border> borders = cnecsToEvaluatePerBorder.keySet();
        Map<Border, MonitoringResult> resultPerBorder = new EnumMap<>(Border.class);
        Unit unit = multiBorderMonitoringInput.getUnit();
        Map<Border, Set<CnecResult>> cnecResultsPerBorder = borders.stream().collect(Collectors.toMap(border -> border, border -> new HashSet<>()));
        // If state is null -> all borders secure
        if (state == null) {
            cnecsToEvaluatePerBorder.keySet().forEach(border -> resultPerBorder.put(border, new MonitoringResult(physicalParameter, Collections.emptySet(), Collections.emptyMap(), Cnec.SecurityStatus.SECURE)));
            return new MultiBorderMonitoringResult(resultPerBorder);
        }
        // Load-flow
        if (!computeLoadFlow(network, loadFlowProvider, loadFlowParameters)) {
            String failureReason = String.format("Load-flow computation failed during %s monitoring at state %s. Skipping this state.", physicalParameter, state);
            Map<Border, MonitoringResult> failedResults = makeFailedMonitoringResultForStateWithNaNCnecResults(multiBorderMonitoringInput, state, borders, failureReason, businessLogger);
            return new MultiBorderMonitoringResult(failedResults);
        }
        // Evaluate margins per border
        for (Map.Entry<Border, Set<Cnec>> entry : cnecsToEvaluatePerBorder.entrySet()) {
            Border border = entry.getKey();
            Set<Cnec> cnecs = entry.getValue();
            boolean anyUnsecure = false;
            Set<CnecResult> results = cnecResultsPerBorder.get(border);
            for (Cnec cnec : cnecs) {
                CnecValue value = cnec.computeValue(network, unit);
                double margin = cnec.computeMargin(network, unit);
                Cnec.SecurityStatus cnecStatus = Cnec.SecurityStatus.SECURE;
                if (margin < 0.0) {
                    anyUnsecure = true;
                    cnecStatus = Cnec.SecurityStatus.HIGH_CONSTRAINT;
                }
                results.add(new CnecResult(cnec, unit, value, margin, cnecStatus));
            }
            Cnec.SecurityStatus borderStatus = anyUnsecure ? Cnec.SecurityStatus.HIGH_CONSTRAINT : Cnec.SecurityStatus.SECURE;
            resultPerBorder.put(border, new MonitoringResult(physicalParameter, results, Collections.emptyMap(), borderStatus));
            //businessLogger.info("Border [{}] – {} margins at state '{}' -> security status: {}",border, physicalParameter, state, borderStatus);
        }
        return new MultiBorderMonitoringResult(resultPerBorder);
    }
}
