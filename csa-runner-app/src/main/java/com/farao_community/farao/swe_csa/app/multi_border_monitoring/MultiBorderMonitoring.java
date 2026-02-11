package com.farao_community.farao.swe_csa.app.multi_border_monitoring;

import com.farao_community.farao.swe_csa.app.multi_border_monitoring.cnec_evaluator.CnecEvaluator;
import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.commons.OpenRaoException;
import com.powsybl.openrao.commons.PhysicalParameter;
import com.powsybl.openrao.data.crac.api.State;
import com.powsybl.openrao.data.crac.api.cnec.Cnec;
import com.powsybl.openrao.monitoring.results.CnecResult;
import com.powsybl.openrao.monitoring.results.MonitoringResult;
import com.powsybl.openrao.util.AbstractNetworkPool;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.farao_community.farao.swe_csa.app.multi_border_monitoring.MonitoringUtils.applyOptimalRemedialActions;
import static com.farao_community.farao.swe_csa.app.multi_border_monitoring.MonitoringUtils.applyOptimalRemedialActionsOnContingencyState;

public class MultiBorderMonitoring {
    private final MultiBorderMonitoringInput monitoringInput;
    private final int numberOfLoadFlowsInParallel;
    private final Logger businessLogger;
    private final CnecEvaluator cnecEvaluator;

    public MultiBorderMonitoring(MultiBorderMonitoringInput monitoringInput, int parallelism, Logger logger) {
        this.monitoringInput = monitoringInput;
        this.numberOfLoadFlowsInParallel = parallelism;
        this.businessLogger = logger;
        this.cnecEvaluator = CnecEvaluator.getEvaluator(monitoringInput, businessLogger);
    }

    public MultiBorderMonitoringResult run() {

        PhysicalParameter physicalParameter = monitoringInput.getPhysicalParameter();
        Network network = monitoringInput.getNetwork();
        Set<Border> borders = monitoringInput.getBorders();
        MultiBorderMonitoringResult monitoringResult = new MultiBorderMonitoringResult(borders.stream().collect(Collectors.toMap(
                border -> border, border -> new MonitoringResult(physicalParameter, Collections.emptySet(), Collections.emptyMap(), Cnec.SecurityStatus.SECURE))));
        businessLogger.info("{} monitoring for borders {} [start]", physicalParameter, borders);

        Map<Border, Set<Cnec>> cnecsMap = borders.stream()
                .collect(Collectors.toMap(border -> border, border -> monitoringInput.getCracForBorder(border).getCnecs(physicalParameter)));

        if (cnecsMap.values().stream().allMatch(Set::isEmpty)) {
            businessLogger.warn("No Cnecs of type '{}' defined.", physicalParameter);
            businessLogger.info("{} monitoring for borders {} [end]", physicalParameter, borders);
            return monitoringResult;
        }

        // Preventive states
        Map<Border, State> preventiveStateMap = borders.stream()
                .collect(Collectors.toMap(border -> border, border -> monitoringInput.getCracForBorder(border).getPreventiveState()));

        if (preventiveStateMap.values().stream().anyMatch(Objects::nonNull)) {
            borders.forEach(border -> applyOptimalRemedialActions(preventiveStateMap.get(border), network, monitoringInput.getRaoResultForBorder(border)));
            Map<Border, Set<Cnec>> preventiveCnecsMap = borders.stream()
                    .collect(Collectors.toMap(border -> border,
                            border -> monitoringInput.getCracForBorder(border).getCnecs(physicalParameter, preventiveStateMap.get(border))));
            State anyPreventiveState = preventiveStateMap.values().stream()
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            MultiBorderMonitoringResult preventiveResults = cnecEvaluator.evaluate(network, anyPreventiveState, preventiveCnecsMap);
            preventiveResults.getAllResults().forEach(monitoringResult::combine);
        }

        // Contingency states
        Map<State, EnumSet<Border>> contingencyStates = MonitoringUtils.mapContingencyStates(monitoringInput);
        if (monitoringResult.allFailed()) {
            return monitoringResult;
        }

        if (!contingencyStates.isEmpty()) {
            try (AbstractNetworkPool networkPool = AbstractNetworkPool.create(network, network.getVariantManager().getWorkingVariantId(),
                    Math.min(numberOfLoadFlowsInParallel, contingencyStates.size()), true)) {
                List<ForkJoinTask<Void>> tasks = contingencyStates.entrySet().stream()
                        .map(entry -> submitParallelMonitoring(networkPool, entry.getKey(), entry.getValue(), monitoringResult))
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
                monitoringResult.getAllResults().values().forEach(MonitoringResult::setStatusToFailure);
            }
        }

        businessLogger.info("{} monitoring for borders {} [end]", physicalParameter, borders);

        monitoringResult.getAllResults().forEach((border, result) -> {
            if (physicalParameter == PhysicalParameter.VOLTAGE || physicalParameter == PhysicalParameter.ANGLE) {
                result.printConstraints().forEach(msg -> businessLogger.info("Border [{}] {}", border, msg));
            } else {
                printFlowConstraints(border, result);
            }
        });
        return monitoringResult;
    }

    private void printFlowConstraints(Border border, MonitoringResult monitoringResult) {
        if (Objects.equals(monitoringResult.getStatus(), Cnec.SecurityStatus.FAILURE)) {
            businessLogger.info("Border [{}] {} monitoring failed due to a load flow divergence or an inconsistency in the crac or in the parameters.",
                    border, monitoringResult.getPhysicalParameter());
            return;
        }

        List<CnecResult> unsecureCnecs = monitoringResult.getCnecResults().stream()
                .filter(r -> r.getMargin() < 0)
                .sorted(Comparator.comparing(CnecResult::getId))
                .toList();

        if (unsecureCnecs.isEmpty()) {
            businessLogger.info("Border [{}] All {} CNECs are secure.", border, monitoringResult.getPhysicalParameter());
            return;
        }
        businessLogger.info("Border [{}] Some {} CNECs are not secure:", border, monitoringResult.getPhysicalParameter());
        for (CnecResult cnec : unsecureCnecs) {
            businessLogger.info("Border [{}] CNEC {} margin={} status={}", border, cnec.getId(), cnec.getMargin(), cnec.getCnecSecurityStatus());
        }
    }

    private ForkJoinTask<Void> submitParallelMonitoring(AbstractNetworkPool networkPool, State state, Set<Border> impactedBorders, MultiBorderMonitoringResult monitoringResult) {
        return networkPool.submit(() -> monitorContingencyState(networkPool, state, impactedBorders, monitoringResult));
    }


    private Void monitorContingencyState(AbstractNetworkPool networkPool, State state, Set<Border> impactedBorders, MultiBorderMonitoringResult monitoringResult) throws InterruptedException {
        Network networkClone = networkPool.getAvailableNetwork();
        try {
            Contingency contingency = state.getContingency().orElseThrow();
            if (!MonitoringUtils.applyContingency(state, networkClone)) {
                businessLogger.warn("Unable to apply contingency {}", contingency.getId());
                Map<Border, MonitoringResult> failed = MonitoringUtils.makeFailedMonitoringResultForStateWithNaNCnecResults(monitoringInput, state, impactedBorders, "Unable to apply contingency " + contingency.getId(), businessLogger);
                failed.forEach(monitoringResult::combine);
                return null;
            }
            impactedBorders.forEach(border ->
                    applyOptimalRemedialActionsOnContingencyState(state, networkClone, monitoringInput.getCracForBorder(border), monitoringInput.getRaoResultForBorder(border)));

            Map<Border, Set<Cnec>> impactedCnecMap = impactedBorders.stream()
                    .collect(Collectors.toMap(border -> border,
                            border -> new HashSet<>(monitoringInput.getCracForBorder(border).getCnecs(monitoringInput.getPhysicalParameter(), state))));
            MultiBorderMonitoringResult currentStateResults = cnecEvaluator.evaluate(networkClone, state, impactedCnecMap);
            currentStateResults.getAllResults().forEach(monitoringResult::combine);
            return null;
        } finally {
            networkPool.releaseUsedNetwork(networkClone);
        }
    }
}
