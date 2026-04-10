package com.farao_community.farao.swe_csa.app.multi_border_monitoring.cnec_evaluator;

import com.farao_community.farao.swe_csa.app.multi_border_monitoring.AppliedNetworkActionsResult;
import com.farao_community.farao.swe_csa.app.multi_border_monitoring.Border;
import com.farao_community.farao.swe_csa.app.multi_border_monitoring.MultiBorderMonitoringInput;
import com.farao_community.farao.swe_csa.app.multi_border_monitoring.MultiBorderMonitoringResult;
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

import static com.farao_community.farao.swe_csa.app.multi_border_monitoring.MonitoringUtils.*;

public class MarginEvaluatorWithRA implements CnecEvaluator {
    private final MultiBorderMonitoringInput multiBorderMonitoringInput;
    private final String loadFlowProvider;
    private final LoadFlowParameters loadFlowParameters;
    private final Logger businessLogger;

    public MarginEvaluatorWithRA(MultiBorderMonitoringInput multiBorderMonitoringInput, Logger businessLogger) {
        this.multiBorderMonitoringInput = multiBorderMonitoringInput;
        this.loadFlowProvider = multiBorderMonitoringInput.getLoadFlowProvider();
        this.loadFlowParameters = multiBorderMonitoringInput.getLoadFlowParameters();
        this.businessLogger = businessLogger;
    }

    @Override
    public MultiBorderMonitoringResult evaluate(Network network, State state, Map<Border, Set<Cnec>> cnecsToEvaluatePerBorder) {
        PhysicalParameter physicalParameter = multiBorderMonitoringInput.getPhysicalParameter();
        Set<Border> borders = cnecsToEvaluatePerBorder.keySet();
        Unit unit = multiBorderMonitoringInput.getUnit();

        // If state is null -> all borders secure
        if (state == null) {
            Map<Border, MonitoringResult> resultPerBorder = new EnumMap<>(Border.class);
            cnecsToEvaluatePerBorder.keySet().forEach(border -> resultPerBorder.put(border, new MonitoringResult(physicalParameter, Collections.emptySet(), Collections.emptyMap(), Cnec.SecurityStatus.SECURE)));
            return new MultiBorderMonitoringResult(resultPerBorder);
        }
        // Load-flow
        if (!computeLoadFlow(network, loadFlowProvider, loadFlowParameters)) {
            String failureReason = String.format("Load-flow computation failed during %s monitoring at state %s. Skipping this state.", physicalParameter, state);
            Map<Border, MonitoringResult> failedResults = makeFailedMonitoringResultForStateWithNaNCnecResults(multiBorderMonitoringInput, state, borders, failureReason, businessLogger);
            return new MultiBorderMonitoringResult(failedResults);
        }

        Map<Border, Set<CnecResult>> cnecResultsPerBorder = borders.stream().collect(Collectors.toMap(border -> border, border -> new HashSet<>()));
        Map<Border, List<AppliedNetworkActionsResult>> appliedActionsPerBorder = borders.stream().collect(Collectors.toMap(border -> border, border -> new ArrayList<>()));
        Set<String> alreadyAppliedActionIds = new HashSet<>();

        // process cnecs for each border
        for (Border border : borders) {
            MonitoringInput input = multiBorderMonitoringInput.getMonitoringInputForBorder(border);
            processMonitoringCnecs(cnecsToEvaluatePerBorder.get(border), state, input, cnecResultsPerBorder.get(border), appliedActionsPerBorder.get(border), network, unit, physicalParameter, alreadyAppliedActionIds);
        }

        // Redispatch for ANGLE
        if (physicalParameter == PhysicalParameter.ANGLE) {
            ZonalData<Scalable> scalable = multiBorderMonitoringInput.getZonalScalableData();
            List<AppliedNetworkActionsResult> allAppliedRas = appliedActionsPerBorder.values().stream()
                    .flatMap(List::stream)
                    .collect(Collectors.toList());
            redispatchNetworkActions(network, allAppliedRas, scalable, businessLogger);
        }

        boolean anyActionsApplied = appliedActionsPerBorder.values().stream().flatMap(List::stream).anyMatch(r -> !r.getAppliedNetworkActions().isEmpty());
        if (anyActionsApplied) {
            if (!computeLoadFlow(network, loadFlowProvider, loadFlowParameters)) {
                String failureReason = String.format("Load-flow computation failed during %s monitoring at state %s after applying RAs. Skipping this state.", physicalParameter, state);
                Map<Border, MonitoringResult> failed = makeFailedMonitoringResultForStateWithNaNCnecResults(multiBorderMonitoringInput, state, borders, failureReason, businessLogger);
                return new MultiBorderMonitoringResult(failed);
            }
            // Recompute CNEC result for each border
            cnecResultsPerBorder.values().forEach(Set::clear);
            for (Map.Entry<Border, Set<Cnec>> entry : cnecsToEvaluatePerBorder.entrySet()) {
                Border border = entry.getKey();
                Set<Cnec> cnecs = entry.getValue();
                for (Cnec cnec : cnecs) {
                    CnecResult result = new CnecResult(cnec, unit, cnec.computeValue(network, unit), cnec.computeMargin(network, unit), cnec.computeSecurityStatus(network, unit));
                    cnecResultsPerBorder.get(border).add(result);
                }
            }
        }

        Map<Border, Cnec.SecurityStatus> securityStatusPerBorder = new HashMap<>();
        cnecResultsPerBorder.forEach((border, results) -> {
            Cnec.SecurityStatus status = computeStatus(results);
            securityStatusPerBorder.put(border, status);
            businessLogger.info("Border [{}] – {} margins at state '{}' -> security status: {}", border, physicalParameter, state, status);
        });
        Map<Border, MonitoringResult> resultPerBorder = borders.stream().collect(Collectors.toMap(
                border -> border,
                border -> new MonitoringResult(physicalParameter, cnecResultsPerBorder.get(border), Map.of(state, appliedActionsPerBorder.get(border).stream().flatMap(r -> r.getAppliedNetworkActions().stream()).collect(Collectors.toSet())), securityStatusPerBorder.get(border))
        ));
        return new MultiBorderMonitoringResult(resultPerBorder);
    }

    private void processMonitoringCnecs(Set<Cnec> cnecs, State state, MonitoringInput monitoringInput, Set<CnecResult> cnecResults, List<AppliedNetworkActionsResult> appliedActions, Network network, Unit unit, PhysicalParameter physicalParameter, Set<String> alreadyAppliedActionIds) {
        cnecs.forEach(cnec -> {
            if (cnec.computeMargin(network, unit) < 0) {
                Set<NetworkAction> availableNetworkActions = getNetworkActionsAssociatedToCnec(state, monitoringInput.getCrac(), cnec, physicalParameter).stream()
                        .filter(action -> !alreadyAppliedActionIds.contains(action.getId()))
                        .collect(Collectors.toSet());
                if (!availableNetworkActions.isEmpty()) {
                    AppliedNetworkActionsResult result = applyNetworkActions(network, availableNetworkActions, cnec.getId(), monitoringInput);
                    if (!result.getAppliedNetworkActions().isEmpty()) {
                        result.getAppliedNetworkActions().forEach(action -> alreadyAppliedActionIds.add(action.getId()));
                        appliedActions.add(result);
                    }
                }
            }
            CnecResult cnecResult = new CnecResult(cnec, unit, cnec.computeValue(network, unit), cnec.computeMargin(network, unit), cnec.computeSecurityStatus(network, unit));
            cnecResults.add(cnecResult);
        });
    }

    private Cnec.SecurityStatus computeStatus(Set<CnecResult> results) {
        return results.stream().anyMatch(r -> r.getMargin() < 0)
                ? MonitoringResult.combineStatuses(results.stream().map(CnecResult::getCnecSecurityStatus).toArray(Cnec.SecurityStatus[]::new))
                : Cnec.SecurityStatus.SECURE;
    }

}
