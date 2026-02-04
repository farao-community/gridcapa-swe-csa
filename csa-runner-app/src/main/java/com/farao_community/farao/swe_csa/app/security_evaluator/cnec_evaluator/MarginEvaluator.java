package com.farao_community.farao.swe_csa.app.security_evaluator.cnec_evaluator;

import com.farao_community.farao.swe_csa.app.security_evaluator.Border;
import com.farao_community.farao.swe_csa.app.security_evaluator.ParallelRaoMonitoringInput;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openrao.commons.PhysicalParameter;
import com.powsybl.openrao.commons.Unit;
import com.powsybl.openrao.data.crac.api.State;
import com.powsybl.openrao.data.crac.api.cnec.Cnec;
import com.powsybl.openrao.monitoring.results.MonitoringResult;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static com.farao_community.farao.swe_csa.app.security_evaluator.ResultValidatorHelper.computeLoadFlow;

public class MarginEvaluator implements CnecEvaluator{
    private final ParallelRaoMonitoringInput parallelInput;
    private final Logger businessLogger;
    private final String loadFlowProvider;
    private final LoadFlowParameters loadFlowParameters;

    public MarginEvaluator(ParallelRaoMonitoringInput parallelInput, Logger businessLogger,
                           String loadFlowProvider, LoadFlowParameters loadFlowParameters) {
        this.parallelInput = parallelInput;
        this.businessLogger = businessLogger;
        this.loadFlowProvider = loadFlowProvider;
        this.loadFlowParameters = loadFlowParameters;
    }


    @Override
    public Map<Border, MonitoringResult> evaluate(State state, Map<Border, Set<Cnec>> impactedCnecMap, Network network) {
        businessLogger.info("-- '{}' Monitoring at state '{}' [end]", parallelInput.getPhysicalParameter(), state);
        Map<Border, MonitoringResult> result = new EnumMap<>(Border.class);
        Unit unit = parallelInput.getUnit();

        // If state is null -> all borders secure?
        if (state == null) {
            impactedCnecMap.keySet().forEach(border ->
                    result.put(border, new MonitoringResult(
                            PhysicalParameter.FLOW,
                            Collections.emptySet(),
                            Collections.emptyMap(),
                            Cnec.SecurityStatus.SECURE
                    ))
            );
            return result;
        }

        // Load-flow
        if (!computeLoadFlow(network, loadFlowProvider, loadFlowParameters)) {
            businessLogger.warn("Load-flow computation failed during security evaluation.");
            impactedCnecMap.keySet().forEach(border ->
                    result.put(border, new MonitoringResult(
                            PhysicalParameter.FLOW,
                            Collections.emptySet(),
                            Collections.emptyMap(),
                            Cnec.SecurityStatus.FAILURE
                    ))
            );
            return result;
        }

        // Evaluate margins per border
        for (Map.Entry<Border, Set<Cnec>> entry : impactedCnecMap.entrySet()) {
            Border border = entry.getKey();
            Set<Cnec> cnecs = entry.getValue();

            boolean anyUnsecure = cnecs.stream()
                    .anyMatch(cnec -> cnec.computeMargin(network, unit) < 0.0);

            Cnec.SecurityStatus status = anyUnsecure
                    ? Cnec.SecurityStatus.FAILURE
                    : Cnec.SecurityStatus.SECURE;

            result.put(border, new MonitoringResult(
                    PhysicalParameter.FLOW,
                    Collections.emptySet(),
                    Collections.emptyMap(),
                    status
            ));
        }

        return result;
    }
}
