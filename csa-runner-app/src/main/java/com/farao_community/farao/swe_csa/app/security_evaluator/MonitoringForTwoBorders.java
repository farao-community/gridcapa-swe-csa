package com.farao_community.farao.swe_csa.app.security_evaluator;

import com.powsybl.computation.ComputationManager;
import com.powsybl.contingency.Contingency;
import com.powsybl.glsk.commons.ZonalData;
import com.powsybl.iidm.modification.scalable.Scalable;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openrao.commons.OpenRaoException;
import com.powsybl.openrao.commons.PhysicalParameter;
import com.powsybl.openrao.commons.Unit;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.State;
import com.powsybl.openrao.data.crac.api.cnec.Cnec;
import com.powsybl.openrao.data.crac.api.cnec.CnecValue;
import com.powsybl.openrao.data.crac.api.networkaction.NetworkAction;
import com.powsybl.openrao.data.crac.impl.AngleCnecValue;
import com.powsybl.openrao.data.crac.impl.VoltageCnecValue;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import com.powsybl.openrao.monitoring.MonitoringInput;
import com.powsybl.openrao.monitoring.results.CnecResult;
import com.powsybl.openrao.monitoring.results.MonitoringResult;
import com.powsybl.openrao.monitoring.results.RaoResultWithAngleMonitoring;
import com.powsybl.openrao.monitoring.results.RaoResultWithVoltageMonitoring;
import com.powsybl.openrao.util.AbstractNetworkPool;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.farao_community.farao.swe_csa.app.security_evaluator.ResultValidatorHelper.*;
import static com.farao_community.farao.swe_csa.app.security_evaluator.ResultValidatorHelper.applyNetworkActions;
import static com.farao_community.farao.swe_csa.app.security_evaluator.ResultValidatorHelper.computeLoadFlow;
import static com.farao_community.farao.swe_csa.app.security_evaluator.ResultValidatorHelper.getNetworkActionsAssociatedToCnec;
import static com.farao_community.farao.swe_csa.app.security_evaluator.ResultValidatorHelper.redispatchNetworkActions;

public class MonitoringForTwoBorders {
    private final String loadFlowProvider;
    private final LoadFlowParameters loadFlowParameters;
    Map<PhysicalParameter, Unit> parameterToUnitMap = new HashMap<>();
    private final Logger businessLogger;

    public MonitoringForTwoBorders(String loadFlowProvider, LoadFlowParameters loadFlowParameters, Logger businessLogger) {
        this.loadFlowProvider = loadFlowProvider;
        this.loadFlowParameters = loadFlowParameters;
        parameterToUnitMap.put(PhysicalParameter.ANGLE, Unit.DEGREE);
        parameterToUnitMap.put(PhysicalParameter.VOLTAGE, Unit.KILOVOLT);
        this.businessLogger = businessLogger;
    }

    public static Map<Border, RaoResult> updateRaoResultsWithAngleMonitoringForTwoBorders(Network network, List<BorderContext> borderContexts, ZonalData<Scalable> scalableZonalDataFilteredForSweCountries, String loadFlowProvider, LoadFlowParameters loadFlowParameters, Logger businessLogger) {
        Map<Border, MonitoringInput> monitoringInputMap = borderContexts.stream()
                .collect(Collectors.toMap(BorderContext::border, bc -> MonitoringInput.buildWithAngle(network, bc.crac(), bc.raoResult(), scalableZonalDataFilteredForSweCountries).build()));

        Map<Border, MonitoringResult> angleMonitoringResultMap = new MonitoringForTwoBorders(loadFlowProvider, loadFlowParameters, businessLogger).runMonitoringForTwoBorders(monitoringInputMap, Runtime.getRuntime().availableProcessors());
        return borderContexts.stream().collect(Collectors.toMap(BorderContext::border, bc -> new RaoResultWithAngleMonitoring(bc.raoResult(), angleMonitoringResultMap.get(bc.border()))));
    }

    public static Map<Border, RaoResult> updateRaoResultsWithVoltageMonitoringForTwoBorders(Network network, List<BorderContext> borderContexts, String loadFlowProvider, LoadFlowParameters loadFlowParameters, Logger businessLogger) {
        Map<Border, MonitoringInput> monitoringInputMap = borderContexts.stream()
                .collect(Collectors.toMap(BorderContext::border, bc -> MonitoringInput.buildWithVoltage(network, bc.crac(), bc.raoResult()).build()));
        Map<Border, MonitoringResult> voltageMonitoringResultMap = new MonitoringForTwoBorders(loadFlowProvider, loadFlowParameters, businessLogger).runMonitoringForTwoBorders(monitoringInputMap, Runtime.getRuntime().availableProcessors());
        return borderContexts.stream().collect(Collectors.toMap(BorderContext::border, bc -> new RaoResultWithVoltageMonitoring(bc.raoResult(), voltageMonitoringResultMap.get(bc.border()))));
    }

    public Map<Border, MonitoringResult> runMonitoringForTwoBorders(Map<Border, MonitoringInput> monitoringInputMap, int numberOfLoadFlowsInParallel) {
        // Inspired by the class Monitoring
        // Get network and physcialParameter from one representative monitoringInput
        PhysicalParameter physicalParameter = monitoringInputMap.values().stream().findFirst().orElseThrow().getPhysicalParameter();
        Network inputNetwork = monitoringInputMap.values().stream().findFirst().orElseThrow().getNetwork();
        Set<Border> borders = monitoringInputMap.keySet();
        Map<Border, Crac> cracMap = monitoringInputMap.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getCrac()));
        Map<Border, RaoResult> raoResultsMap = monitoringInputMap.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getRaoResult()));

        // Initial two empty monitoringResults
        Map<Border, MonitoringResult> monitoringResultMap = monitoringInputMap.keySet().stream().collect(Collectors.toMap(border -> border,
                border -> new MonitoringResult(physicalParameter, Collections.emptySet(), Collections.emptyMap(), Cnec.SecurityStatus.SECURE)));

        businessLogger.info("----- {} monitoring for two borders [start]", physicalParameter);
        Map<Border, Set<Cnec>> cnecsMap = cracMap.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                entry -> entry.getValue().getCnecs(physicalParameter)));
        if (cnecsMap.values().stream() .anyMatch(set -> !set.isEmpty())) {
            // Note: is this redundant and incoherent with validateNetworkForTwoBorders?
            businessLogger.warn("No Cnecs of type '{}' defined.", physicalParameter);
            businessLogger.info("----- {} monitoring for two borders [end]", physicalParameter);
            return monitoringResultMap;
        }

        // Preventive states
        Map<Border, State> preventiveStateMap = cracMap.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                entry -> entry.getValue().getPreventiveState()));

        if (preventiveStateMap.values().stream().anyMatch(Objects::nonNull)) {
            borders.forEach(border -> applyOptimalRemedialActions(preventiveStateMap.get(border), inputNetwork, raoResultsMap.get(border)));

            Map<Border, Set<Cnec>> preventiveCnecsMap = borders.stream().collect(Collectors.toMap(border -> border,
                    border -> cracMap.get(border).getCnecs(physicalParameter, preventiveStateMap.get(border))));

            Map<Border, MonitoringResult> preventiveMonitoringResultMap = monitorCnecsForTwoBorders(preventiveStateMap.values().stream().toList().getFirst(), preventiveCnecsMap, inputNetwork, monitoringInputMap);

            preventiveMonitoringResultMap.forEach((border, preventiveMonitoring) -> {
                if (preventiveMonitoring != null) {
                    monitoringResultMap.get(border).combine(preventiveMonitoring);
                }
            });
        }

        // Contingency States
        List<BorderContext> borderContexts = monitoringInputMap.entrySet().stream()
                .map(e -> new BorderContext(e.getKey(), e.getValue().getCrac(), e.getValue().getRaoResult()))
                .toList();
        Map<State, EnumSet<Border>> contingencyStates = BorderStateMapper.mapContingencyStates(borderContexts, physicalParameter);

        if (!contingencyStates.isEmpty()) {
            try (AbstractNetworkPool networkPool = AbstractNetworkPool.create(inputNetwork, inputNetwork.getVariantManager().getWorkingVariantId(), Math.min(numberOfLoadFlowsInParallel, contingencyStates.size()), true)) {
                List<ForkJoinTask<Map<Border, Void>>> tasks = contingencyStates.entrySet().stream()
                        .map(entry -> submitParallelMonitoring(networkPool, entry.getKey(), entry.getValue(), monitoringInputMap, monitoringResultMap, physicalParameter))
                        .toList();
                for (ForkJoinTask<Map<Border, Void>> task : tasks) {
                    try {
                        task.get();
                    } catch (ExecutionException e) {
                        throw new OpenRaoException(e);
                    }

                }
                networkPool.shutdownAndAwaitTermination(24, TimeUnit.HOURS);
            } catch (Exception e) {
                Thread.currentThread().interrupt();
                monitoringResultMap.values().forEach(MonitoringResult::setStatusToFailure);
            }
        }

        businessLogger.info("----- {} monitoring [end]", physicalParameter);
        monitoringResultMap.values().forEach(monitoringResult -> monitoringResult.printConstraints().forEach(businessLogger::info));
        return monitoringResultMap;
    }

    private ForkJoinTask<Map<Border, Void>> submitParallelMonitoring(AbstractNetworkPool networkPool, State state, EnumSet<Border> impactedBorders, Map<Border, MonitoringInput> monitoringInputMap, Map<Border, MonitoringResult> monitoringResultMap, PhysicalParameter parameter) {
        return networkPool.submit(() -> monitorContingencyState(networkPool, state, impactedBorders, monitoringInputMap, monitoringResultMap, parameter));
    }

    private Map<Border, Void> monitorContingencyState(AbstractNetworkPool networkPool, State state, Set<Border> impactedBorders, Map<Border, MonitoringInput> monitoringInputMap, Map<Border, MonitoringResult> monitoringResultMap, PhysicalParameter physicalParameter) throws InterruptedException {
        Network networkClone = networkPool.getAvailableNetwork();
        Contingency contingency = state.getContingency().orElseThrow();
        if (!contingency.isValid(networkClone)) {
            businessLogger.warn("Unable to apply contingency {}", contingency.getId());
            Map<Border, MonitoringResult> faileMonitonringResultMap = makeFailedMonitoringResultForStateWithNaNCnecResults(monitoringInputMap, physicalParameter, state, impactedBorders, "Unable to apply contingency " + contingency.getId());
            monitoringResultMap.forEach((border, result) -> result.combine(faileMonitonringResultMap.get(border)));
            networkPool.releaseUsedNetwork(networkClone);
            return null;
        }
        contingency.toModification().apply(networkClone, (ComputationManager) null);
        monitoringInputMap.entrySet().stream()
                .filter(e -> impactedBorders.contains(e.getKey()))
                .forEach(e -> applyOptimalRemedialActionsOnContingencyState(state, networkClone, e.getValue().getCrac(), e.getValue().getRaoResult()));
        Map<Border, Set<Cnec>> impactedCnecMap = impactedBorders.stream().collect(Collectors.toMap(border -> border, border ->
                new HashSet<>(monitoringInputMap.get(border).getCrac().getCnecs(physicalParameter, state))));
        Map<Border, MonitoringResult> currentStateMonitoringResults = monitorCnecsForTwoBorders(state, impactedCnecMap, networkClone, monitoringInputMap);
        monitoringResultMap.forEach((border, monitoringResult) -> monitoringResult.combine(currentStateMonitoringResults.get(border)));
        networkPool.releaseUsedNetwork(networkClone);
        return null;
    }

    private Map<Border, MonitoringResult> monitorCnecsForTwoBorders(State state, Map<Border, Set<Cnec>> impactedCnecMap, Network network, Map<Border, MonitoringInput> monitoringInputMap) {
        PhysicalParameter physicalParameter = monitoringInputMap.values().stream().findFirst().orElseThrow().getPhysicalParameter();
        Set<Border> impactedBorders = impactedCnecMap.keySet();
        Unit unit = parameterToUnitMap.get(physicalParameter);
        Map<Border, Set<CnecResult>> impactedCnecResultsMap = impactedCnecMap.keySet().stream().collect(Collectors.toMap(
                border -> border,
                border -> new HashSet<>()));

        businessLogger.info("-- '{}' Monitoring at state '{}' for two borders [start]", physicalParameter, state);
        boolean lfSuccess = computeLoadFlow(network, loadFlowProvider, loadFlowParameters);
        if (!lfSuccess) {
            String failureReason = String.format("Load-flow computation failed at state %s. Skipping this state.", state);
            return makeFailedMonitoringResultForStateWithNaNCnecResults(monitoringInputMap, physicalParameter, state, impactedBorders, failureReason);
        } else {
            Map<Border, List<AppliedNetworkActionsResult>> appliedNetworkActionsResultListMap = impactedCnecMap.keySet().stream().collect(Collectors.toMap(border -> border, border -> new ArrayList<>()));

            impactedBorders.forEach(border ->
                    processMonitoringCnecs(impactedCnecMap.get(border), state, monitoringInputMap.get(border), impactedCnecResultsMap.get(border), appliedNetworkActionsResultListMap.get(border),
                            network, unit, physicalParameter));

            ZonalData<Scalable> scalableZonalData = monitoringInputMap.values().stream().findFirst().orElseThrow().getScalableZonalData();
            // Re-dispatch the network (for angleCnecs)
            appliedNetworkActionsResultListMap.values().forEach(appliedNetworkList ->
                    redispatchNetworkActions(network, appliedNetworkList, scalableZonalData, businessLogger));

            boolean hasAnyAppliedActions = appliedNetworkActionsResultListMap.values().stream()
                    .flatMap(List::stream)
                    .map(AppliedNetworkActionsResult::getAppliedNetworkActions)
                    .anyMatch(actions -> !actions.isEmpty());

            if (hasAnyAppliedActions) {
                lfSuccess = computeLoadFlow(network, loadFlowProvider, loadFlowParameters);
                if (!lfSuccess) {
                    businessLogger.warn("Load-flow computation failed at state {} after applying RAs. Skipping this state.", state);
                    return impactedBorders.stream().collect(Collectors.toMap(
                            border -> border,
                            border -> new MonitoringResult(physicalParameter, impactedCnecResultsMap.get(border), Map.of(state, Collections.emptySet()), Cnec.SecurityStatus.FAILURE)));
                }
                impactedCnecResultsMap.values().forEach(Set::clear);
                // Update cnecResult for each impacted border
                impactedCnecMap.forEach((border, cnecs) -> cnecs.forEach(cnec -> {
                    CnecResult cnecResult = new CnecResult(cnec, unit, cnec.computeValue(network, unit), cnec.computeMargin(network, unit), cnec.computeSecurityStatus(network, unit));
                    impactedCnecResultsMap.get(border).add(cnecResult);
                }));
            }

            Map<Border, Cnec.SecurityStatus> monitoringStatusMap = new HashMap<>();

            impactedCnecResultsMap.forEach((border, cnecResults) -> {
                Cnec.SecurityStatus status = Cnec.SecurityStatus.SECURE;
                if (cnecResults.stream().anyMatch(r -> r.getMargin() < 0)) {
                    status = MonitoringResult.combineStatuses(cnecResults.stream().map(CnecResult::getCnecSecurityStatus).toArray(Cnec.SecurityStatus[]::new));
                }
                monitoringStatusMap.put(border, status);
            });

            businessLogger.info("-- '{}' Monitoring at state '{}' [end]", physicalParameter, state);

            return impactedBorders.stream().collect(Collectors.toMap(
                    border -> border,
                    border -> new MonitoringResult(physicalParameter, impactedCnecResultsMap.get(border), Map.of(state, appliedNetworkActionsResultListMap.get(border).stream().flatMap(r -> r.getAppliedNetworkActions().stream()).collect(Collectors.toSet())), monitoringStatusMap.get(border))));
        }
    }

    private Map<Border, MonitoringResult> makeFailedMonitoringResultForStateWithNaNCnecResults(Map<Border, MonitoringInput> monitoringInputMap, PhysicalParameter physicalParameter, State state, Set<Border> impactedBorders, String failureReason) {
        CnecValue cnecValue = (physicalParameter == PhysicalParameter.ANGLE) ? new AngleCnecValue(Double.NaN) : new VoltageCnecValue(Double.NaN, Double.NaN);
        Map<Border, MonitoringResult> monitoringResultMap = new HashMap<>();

        impactedBorders.stream().filter(monitoringInputMap::containsKey)
                .forEach(border -> {
                    MonitoringInput input = monitoringInputMap.get(border);
                    Set<CnecResult> cnecResults = input.getCrac().getCnecs(state).stream().map(cnec -> new CnecResult(cnec, parameterToUnitMap.get(physicalParameter), cnecValue, Double.NaN, Cnec.SecurityStatus.FAILURE)).collect(Collectors.toSet());
                    monitoringResultMap.put(border, new MonitoringResult(physicalParameter, cnecResults, Map.of(state, Collections.emptySet()), Cnec.SecurityStatus.FAILURE));
                });

        businessLogger.warn(failureReason);
        return monitoringResultMap;
    }

    private void processMonitoringCnecs(Set<Cnec> cnecs, State state, MonitoringInput monitoringInput, Set<CnecResult> cnecResults, List<AppliedNetworkActionsResult> appliedNetworkActionsResultList, Network network, Unit unit, PhysicalParameter physicalParameter) {
        cnecs.forEach(cnec -> {
            if (cnec.computeMargin(network, unit) < 0) {
                Set<NetworkAction> availableNetworkActions = getNetworkActionsAssociatedToCnec(state, monitoringInput.getCrac(), cnec, physicalParameter);

                if (!availableNetworkActions.isEmpty()) {
                    AppliedNetworkActionsResult result = applyNetworkActions(network, availableNetworkActions, cnec.getId(), monitoringInput);
                    if (!result.getAppliedNetworkActions().isEmpty()) {
                        appliedNetworkActionsResultList.add(result);
                    }
                }
            }
            CnecResult cnecResult = new CnecResult(cnec, unit, cnec.computeValue(network, unit), cnec.computeMargin(network, unit), cnec.computeSecurityStatus(network, unit));
            cnecResults.add(cnecResult);
        });
    }
}
