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
        Network network = monitoringInput.getNetwork();
        Set<Border> borders = monitoringInput.getBorders();
        PhysicalParameter physicalParameter = monitoringInput.getPhysicalParameter();

        // Start with secure results for all borders
        MultiBorderMonitoringResult monitoringResult = MultiBorderMonitoringResult.createSecureResults(borders, physicalParameter);

        // If no CNECs are present for the given physical parameter then end monitoring
        businessLogger.info("{} monitoring for borders {} [start]", physicalParameter, borders);
        if (!monitoringInput.hasAnyCnecs()) {
            businessLogger.warn("No Cnecs of type '{}' defined.", physicalParameter);
            businessLogger.info("{} monitoring for borders {} [end]", physicalParameter, borders);
            return monitoringResult;
        }

        // Preventive evaluation
        Map<Border, State> preventiveStatesPerBorder = monitoringInput.getPreventiveStates();
        if (monitoringInput.hasAnyPreventiveState()) {
            // Apply optimized RAs
            borders.forEach(border -> applyOptimalRemedialActions(preventiveStatesPerBorder.get(border), network, monitoringInput.getRaoResultForBorder(border)));
            // Monitor preventive CNECs
            Map<Border, Set<Cnec>> preventiveCnecsPerBorder = monitoringInput.getPreventiveCnecs(preventiveStatesPerBorder);
            MultiBorderMonitoringResult preventiveResults = cnecEvaluator.evaluate(network, monitoringInput.getAnyPreventiveState(), preventiveCnecsPerBorder);
            // Update monitoring results
            preventiveResults.getResultsForAllBorders().forEach(monitoringResult::combine);
        }

        // End monitoring if preventive monitoring failed for all borders
        if (monitoringResult.allFailed()) {
            MonitoringUtils.printResults(monitoringResult, physicalParameter, businessLogger);
            return monitoringResult;
        }

        // Contingency evaluation
        Map<State, EnumSet<Border>> contingencyStatesPerBorder = MonitoringUtils.mapContingencyStates(monitoringInput);
        if (!contingencyStatesPerBorder.isEmpty()) {
            try (AbstractNetworkPool networkPool = AbstractNetworkPool.create(network, network.getVariantManager().getWorkingVariantId(), Math.min(numberOfLoadFlowsInParallel, contingencyStatesPerBorder.size()), true)) {
                List<ForkJoinTask<Void>> tasks = contingencyStatesPerBorder.entrySet().stream()
                        .map(entry -> submitParallelMonitoring(networkPool, entry.getKey(), entry.getValue(), monitoringResult))
                        .toList();
                tasks.forEach(this::waitFor);
                networkPool.shutdownAndAwaitTermination(24, TimeUnit.HOURS);
            } catch (Exception e) {
                Thread.currentThread().interrupt();
                monitoringResult.getResultsForAllBorders().values().forEach(MonitoringResult::setStatusToFailure);
            }
        }
        businessLogger.info("{} monitoring for borders {} [end]", physicalParameter, borders);

        // log monitoring results
        MonitoringUtils.printResults(monitoringResult, physicalParameter, businessLogger);
        return monitoringResult;
    }

    private ForkJoinTask<Void> submitParallelMonitoring(AbstractNetworkPool networkPool, State state, Set<Border> impactedBorders, MultiBorderMonitoringResult monitoringResult) {
        return networkPool.submit(() -> monitorContingencyState(networkPool, state, impactedBorders, monitoringResult));
    }

    private Void monitorContingencyState(AbstractNetworkPool networkPool, State state, Set<Border> impactedBorders, MultiBorderMonitoringResult monitoringResult) throws InterruptedException {
        Network networkClone = networkPool.getAvailableNetwork();
        try {
            // Apply contingency
            Contingency contingency = state.getContingency().orElseThrow();
            if (!MonitoringUtils.applyContingency(state, networkClone)) {
                Map<Border, MonitoringResult> failedResults = MonitoringUtils.makeFailedMonitoringResultForStateWithNaNCnecResults(monitoringInput, state, impactedBorders, "Unable to apply contingency " + contingency.getId(), businessLogger);
                failedResults.forEach(monitoringResult::combine);
                return null;
            }
            // Apply optimized RAs
            impactedBorders.forEach(border -> applyOptimalRemedialActionsOnContingencyState(state, networkClone, monitoringInput.getCracForBorder(border), monitoringInput.getRaoResultForBorder(border)));
            // Monitors CNECs
            Map<Border, Set<Cnec>> cnecsToEvaluatePerBorder = monitoringInput.getCnecsForBorders(impactedBorders, state);
            MultiBorderMonitoringResult currentStateResults = cnecEvaluator.evaluate(networkClone, state, cnecsToEvaluatePerBorder);
            // Update monitoring results
            currentStateResults.getResultsForAllBorders().forEach(monitoringResult::combine);
            return null;
        } finally {
            networkPool.releaseUsedNetwork(networkClone);
        }
    }

    private void waitFor(ForkJoinTask<Void> task) {
        try {
            task.get();
        } catch (ExecutionException e) {
            throw new OpenRaoException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OpenRaoException(e);
        }
    }

}
