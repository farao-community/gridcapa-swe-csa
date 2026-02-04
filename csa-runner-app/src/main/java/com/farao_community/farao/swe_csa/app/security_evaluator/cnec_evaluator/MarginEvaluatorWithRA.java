package com.farao_community.farao.swe_csa.app.security_evaluator.cnec_evaluator;

import com.farao_community.farao.swe_csa.app.security_evaluator.AppliedNetworkActionsResult;
import com.farao_community.farao.swe_csa.app.security_evaluator.Border;
import com.farao_community.farao.swe_csa.app.security_evaluator.ParallelRaoMonitoringInput;
import com.powsybl.glsk.commons.ZonalData;
import com.powsybl.iidm.modification.scalable.Scalable;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openrao.commons.PhysicalParameter;
import com.powsybl.openrao.commons.Unit;
import com.powsybl.openrao.data.crac.api.State;
import com.powsybl.openrao.data.crac.api.cnec.Cnec;
import com.powsybl.openrao.data.crac.api.networkaction.NetworkAction;
import com.powsybl.openrao.monitoring.MonitoringInput;
import com.powsybl.openrao.monitoring.results.CnecResult;
import com.powsybl.openrao.monitoring.results.MonitoringResult;
import org.slf4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

import static com.farao_community.farao.swe_csa.app.security_evaluator.ResultValidatorHelper.*;

public class MarginEvaluatorWithRA implements CnecEvaluator {
    private final ParallelRaoMonitoringInput parallelInput;
    private final Logger businessLogger;
    private final String loadFlowProvider;
    private final LoadFlowParameters loadFlowParameters;

    public MarginEvaluatorWithRA(ParallelRaoMonitoringInput parallelInput, Logger businessLogger, String loadFlowProvider, LoadFlowParameters loadFlowParameters) {
        this.parallelInput = parallelInput;
        this.businessLogger = businessLogger;
        this.loadFlowProvider = loadFlowProvider;
        this.loadFlowParameters = loadFlowParameters;
    }

    @Override
    public Map<Border, MonitoringResult> evaluate(State state, Map<Border, Set<Cnec>> impactedCnecMap, Network network) {
        PhysicalParameter physicalParameter = parallelInput.getPhysicalParameter();
        Set<Border> impactedBorders = impactedCnecMap.keySet();
        Unit unit = parallelInput.getUnit();

        businessLogger.info("-- '{}' Monitoring at state '{}' for two borders [start]", physicalParameter, state);
        boolean lfSuccess = computeLoadFlow(network, loadFlowProvider, loadFlowParameters);
        if (!lfSuccess) {
            return makeFailedMonitoringResultForStateWithNaNCnecResults(parallelInput, state, impactedBorders, "Load-flow computation failed at state " + state, businessLogger);
        }

        Map<Border, Set<CnecResult>> cnecResultsMap = impactedBorders.stream().collect(Collectors.toMap(border -> border, border -> new HashSet<>()));
        Map<Border, List<AppliedNetworkActionsResult>> appliedActionsMap = impactedBorders.stream().collect(Collectors.toMap(border -> border, border -> new ArrayList<>()));


        impactedBorders.forEach(border -> {
            MonitoringInput input = parallelInput.getMonitoringInputForBorder(border);
            processMonitoringCnecs(impactedCnecMap.get(border), state, input, cnecResultsMap.get(border), appliedActionsMap.get(border), network, unit, physicalParameter);
        });

        // Redispatch for ANGLE
        if (physicalParameter == PhysicalParameter.ANGLE) {
            ZonalData<Scalable> scalable = parallelInput.getZonalScalableData();
            appliedActionsMap.values().forEach(list -> redispatchNetworkActions(network, list, scalable, businessLogger));
        }


        boolean anyActionsApplied = appliedActionsMap.values().stream().flatMap(List::stream).anyMatch(r -> !r.getAppliedNetworkActions().isEmpty());
        if (anyActionsApplied) {
            lfSuccess = computeLoadFlow(network, loadFlowProvider, loadFlowParameters);
            if (!lfSuccess) {
                businessLogger.warn("Load-flow computation failed at state {} after applying RAs. Skipping this state.", state);
                return impactedBorders.stream().collect(Collectors.toMap(
                        border -> border,
                        border -> new MonitoringResult(physicalParameter, cnecResultsMap.get(border), Map.of(state, Collections.emptySet()), Cnec.SecurityStatus.FAILURE)));
            }
            // Recompute CNEC results
            cnecResultsMap.values().forEach(Set::clear);
            impactedCnecMap.forEach((border, cnecs) ->
                    cnecs.forEach(cnec -> {
                        CnecResult result = new CnecResult(cnec, unit, cnec.computeValue(network, unit), cnec.computeMargin(network, unit), cnec.computeSecurityStatus(network, unit));
                        cnecResultsMap.get(border).add(result);
                    })
            );
        }

        Map<Border, Cnec.SecurityStatus> statusMap = new HashMap<>();
        cnecResultsMap.forEach((border, results) -> {
            boolean anyUnsecure = results.stream().anyMatch(r -> r.getMargin() < 0);
            Cnec.SecurityStatus status = anyUnsecure ?
                    MonitoringResult.combineStatuses(results.stream().map(CnecResult::getCnecSecurityStatus).toArray(Cnec.SecurityStatus[]::new)) : Cnec.SecurityStatus.SECURE;
            statusMap.put(border, status);
        });

        businessLogger.info("-- '{}' Monitoring at state '{}' [end]", physicalParameter, state);

        return impactedBorders.stream().collect(Collectors.toMap(
                border -> border,
                border -> new MonitoringResult(physicalParameter, cnecResultsMap.get(border), Map.of(state, appliedActionsMap.get(border).stream().flatMap(r -> r.getAppliedNetworkActions().stream()).collect(Collectors.toSet())), statusMap.get(border))
        ));
    }

    private void processMonitoringCnecs(Set<Cnec> cnecs, State state, MonitoringInput monitoringInput, Set<CnecResult> cnecResults, List<AppliedNetworkActionsResult> appliedActions, Network network, Unit unit, PhysicalParameter physicalParameter) {
        cnecs.forEach(cnec -> {
            if (cnec.computeMargin(network, unit) < 0) {
                Set<NetworkAction> availableNetworkActions = getNetworkActionsAssociatedToCnec(state, monitoringInput.getCrac(), cnec, physicalParameter);

                if (!availableNetworkActions.isEmpty()) {
                    AppliedNetworkActionsResult result = applyNetworkActions(network, availableNetworkActions, cnec.getId(), monitoringInput);
                    if (!result.getAppliedNetworkActions().isEmpty()) {
                        appliedActions.add(result);
                    }
                }
            }
            CnecResult cnecResult = new CnecResult(cnec, unit, cnec.computeValue(network, unit), cnec.computeMargin(network, unit), cnec.computeSecurityStatus(network, unit));
            cnecResults.add(cnecResult);
        });
    }
}
