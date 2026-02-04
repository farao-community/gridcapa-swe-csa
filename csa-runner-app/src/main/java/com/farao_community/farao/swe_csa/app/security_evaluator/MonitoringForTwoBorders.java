package com.farao_community.farao.swe_csa.app.security_evaluator;

import com.farao_community.farao.swe_csa.app.security_evaluator.cnec_evaluator.CnecEvaluator;
import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openrao.commons.OpenRaoException;
import com.powsybl.openrao.commons.PhysicalParameter;
import com.powsybl.openrao.data.crac.api.State;
import com.powsybl.openrao.data.crac.api.cnec.Cnec;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
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

import static com.farao_community.farao.swe_csa.app.security_evaluator.ResultValidatorHelper.applyOptimalRemedialActions;
import static com.farao_community.farao.swe_csa.app.security_evaluator.ResultValidatorHelper.applyOptimalRemedialActionsOnContingencyState;

public class MonitoringForTwoBorders {
    private final ParallelRaoMonitoringInput monitoringInput;
    private final int numberOfLoadFlowsInParallel;
    private final Logger businessLogger;
    private final CnecEvaluator cnecEvaluator;

    public MonitoringForTwoBorders(ParallelRaoMonitoringInput monitoringInput,
                                   int parallelism,
                                   Logger logger,
                                   String loadFlowProvider,
                                   LoadFlowParameters loadFlowParams) {
        this.monitoringInput = monitoringInput;
        this.numberOfLoadFlowsInParallel = parallelism;
        this.businessLogger = logger;
        this.cnecEvaluator = CnecEvaluator.getEvaluator(monitoringInput, businessLogger, loadFlowProvider, loadFlowParams);
    }

    public static Map<Border, RaoResult> updateRaoResultsWithAngleMonitoringForTwoBorders(ParallelRaoMonitoringInput parallelInput, String loadFlowProvider, LoadFlowParameters loadFlowParameters, Logger businessLogger) {
        MonitoringForTwoBorders monitoring = new MonitoringForTwoBorders(parallelInput, Runtime.getRuntime().availableProcessors(), businessLogger, loadFlowProvider, loadFlowParameters);
        Map<Border, MonitoringResult> angleMonitoringResults = monitoring.runMonitoringForTwoBorders();
        return parallelInput.getBorders().stream().collect(Collectors.toMap(border -> border, border -> new RaoResultWithAngleMonitoring(parallelInput.getRaoResultForBorder(border), angleMonitoringResults.get(border))));
    }

    public static Map<Border, RaoResult> updateRaoResultsWithVoltageMonitoringForTwoBorders(ParallelRaoMonitoringInput parallelInput, String loadFlowProvider, LoadFlowParameters loadFlowParameters, Logger businessLogger) {
        MonitoringForTwoBorders monitoring = new MonitoringForTwoBorders(parallelInput, Runtime.getRuntime().availableProcessors(), businessLogger, loadFlowProvider, loadFlowParameters);
        Map<Border, MonitoringResult> voltageMonitoringResults = monitoring.runMonitoringForTwoBorders();
        return parallelInput.getBorders().stream().collect(Collectors.toMap(border -> border, border -> new RaoResultWithVoltageMonitoring(parallelInput.getRaoResultForBorder(border), voltageMonitoringResults.get(border))));
    }

    public Map<Border, MonitoringResult> runMonitoringForTwoBorders() {
        // Get network and physcialParameter from one representative monitoringInput
        PhysicalParameter physicalParameter = monitoringInput.getPhysicalParameter();
        Network network = monitoringInput.getNetwork();
        Set<Border> borders = monitoringInput.getBorders();

        // Initial two empty monitoringResults
        Map<Border, MonitoringResult> monitoringResultMap = borders.stream().collect(Collectors.toMap(border -> border,
                        border -> new MonitoringResult(physicalParameter, Collections.emptySet(), Collections.emptyMap(), Cnec.SecurityStatus.SECURE)));

        businessLogger.info("----- {} monitoring for two borders [start]", physicalParameter);

        Map<Border, Set<Cnec>> cnecsMap = borders.stream().collect(Collectors.toMap(border ->
                border, border -> monitoringInput.getCracForBorder(border).getCnecs(physicalParameter)
        ));

        if (cnecsMap.values().stream().allMatch(Set::isEmpty)) {
            // Note: is this redundant and incoherent with validateNetworkForTwoBorders?
            businessLogger.warn("No Cnecs of type '{}' defined.", physicalParameter);
            businessLogger.info("----- {} monitoring for two borders [end]", physicalParameter);
            return monitoringResultMap;
        }

        // Preventive states
        Map<Border, State> preventiveStateMap = borders.stream().collect(Collectors.toMap(border -> border,
                border -> monitoringInput.getCracForBorder(border).getPreventiveState()));

        if (preventiveStateMap.values().stream().anyMatch(Objects::nonNull)) {
            borders.forEach(border -> applyOptimalRemedialActions(preventiveStateMap.get(border), network, monitoringInput.getRaoResultForBorder(border)));

            Map<Border, Set<Cnec>> preventiveCnecsMap = borders.stream().collect(Collectors.toMap(border -> border, border -> monitoringInput.getCracForBorder(border).getCnecs(physicalParameter, preventiveStateMap.get(border))));

            State anyPreventiveState = preventiveStateMap.values().stream()
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);

            Map<Border, MonitoringResult> preventiveResults = cnecEvaluator.evaluate(anyPreventiveState, preventiveCnecsMap, network);
            preventiveResults.forEach((border, preventiveMonitoring) -> {
                if (preventiveMonitoring != null) {
                    monitoringResultMap.get(border).combine(preventiveMonitoring);
                }
            });
        }

        // Contingency States
        Map<State, EnumSet<Border>> contingencyStates =
                BorderStateMapper.mapContingencyStates(monitoringInput, physicalParameter);

        // If all borders failed already, stop
        if (monitoringResultMap.values().stream().allMatch(v -> Objects.equals(v.getStatus(), Cnec.SecurityStatus.FAILURE))) {
            return monitoringResultMap;
        }

        if (!contingencyStates.isEmpty()) {
            try (AbstractNetworkPool networkPool = AbstractNetworkPool.create(network, network.getVariantManager().getWorkingVariantId(), Math.min(numberOfLoadFlowsInParallel, contingencyStates.size()), true)) {
                List<ForkJoinTask<Void>> tasks = contingencyStates.entrySet().stream()
                        .map(entry -> submitParallelMonitoring(networkPool, entry.getKey(), entry.getValue(), monitoringResultMap))
                        .toList();
                for (ForkJoinTask<Void> task : tasks) {
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
        monitoringResultMap.forEach((border, monitoringResult) -> monitoringResult.printConstraints().forEach(msg -> businessLogger.info("[{}] {}", border, msg)));
        return monitoringResultMap;
    }

    private ForkJoinTask<Void> submitParallelMonitoring(AbstractNetworkPool networkPool, State state, Set<Border> impactedBorders, Map<Border, MonitoringResult> monitoringResultMap) {
        return networkPool.submit(() ->
                monitorContingencyState(networkPool, state, impactedBorders, monitoringResultMap)
        );
    }

    private Void monitorContingencyState(AbstractNetworkPool networkPool, State state, Set<Border> impactedBorders, Map<Border, MonitoringResult> monitoringResultMap) throws InterruptedException {

        Network networkClone = networkPool.getAvailableNetwork();
        try {
            Contingency contingency = state.getContingency().orElseThrow();
            if (!ResultValidatorHelper.applyContingency(state, networkClone)) {
                businessLogger.warn("Unable to apply contingency {}", contingency.getId());
                Map<Border, MonitoringResult> failed =
                        ResultValidatorHelper.makeFailedMonitoringResultForStateWithNaNCnecResults(monitoringInput, state, impactedBorders, "Unable to apply contingency " + contingency.getId(), businessLogger);
                failed.forEach((border, result) -> monitoringResultMap.get(border).combine(result));
                return null;
            }

            impactedBorders.forEach(border -> applyOptimalRemedialActionsOnContingencyState(state, networkClone, monitoringInput.getCracForBorder(border), monitoringInput.getRaoResultForBorder(border)));
            Map<Border, Set<Cnec>> impactedCnecMap = impactedBorders.stream().collect(Collectors.toMap(border ->
                    border,
                    border -> new HashSet<>(monitoringInput.getCracForBorder(border).getCnecs(monitoringInput.getPhysicalParameter(), state))));
            Map<Border, MonitoringResult> currentStateResults = cnecEvaluator.evaluate(state, impactedCnecMap, networkClone);
            currentStateResults.forEach((border, result) -> monitoringResultMap.get(border).combine(result));
            return null;
        }  finally {
            networkPool.releaseUsedNetwork(networkClone);
        }
    }

}
