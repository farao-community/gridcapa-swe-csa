package com.farao_community.farao.swe_csa.app.security_evaluator;

import com.farao_community.farao.swe_csa.app.security_evaluator.ParallelRaoMonitoringInput.CracRaoResultPair;
import com.farao_community.farao.swe_csa.app.security_evaluator.cnec_evaluator.CnecEvaluator;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openrao.commons.OpenRaoException;
import com.powsybl.openrao.commons.PhysicalParameter;
import com.powsybl.openrao.commons.Unit;
import com.powsybl.openrao.data.crac.api.State;
import com.powsybl.openrao.data.crac.api.cnec.Cnec;
import com.powsybl.openrao.monitoring.results.MonitoringResult;
import com.powsybl.openrao.util.AbstractNetworkPool;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.farao_community.farao.swe_csa.app.security_evaluator.ResultValidatorHelper.applyOptimalRemedialActions;
import static com.farao_community.farao.swe_csa.app.security_evaluator.ResultValidatorHelper.applyOptimalRemedialActionsOnContingencyState;

public class TwoBordersFlowCnecSecurityChecker {
    private final ParallelRaoMonitoringInput monitoringInput;
    private final int numberOfLoadFlowsInParallel;
    private final Logger businessLogger;
    private final CnecEvaluator cnecEvaluator;

    public TwoBordersFlowCnecSecurityChecker(ParallelRaoMonitoringInput monitoringInput,
                                             int parallelism,
                                             Logger logger,
                                             String loadFlowProvider,
                                             LoadFlowParameters loadFlowParams) {
        this.monitoringInput = monitoringInput;
        this.numberOfLoadFlowsInParallel = parallelism;
        this.businessLogger = logger;
        this.cnecEvaluator = CnecEvaluator.getEvaluator(monitoringInput, businessLogger, loadFlowProvider, loadFlowParams);
    }

    /**
     * Check if all the flowCnecs is secure when considering the application of
     * RAs from 2 borders
     *
     * @return True if all flowCnecs are secure and False if there is at least
     * one flowCnec is not secure
     */
    public Map<Border, MonitoringResult>  check() {
        businessLogger.info("----- Monitoring flow CNECs for borders [start]");
        PhysicalParameter parameter = monitoringInput.getPhysicalParameter();
        Unit unit = monitoringInput.getUnit();
        Set<Border> borders = monitoringInput.getBorders();

        // Initialize all borders start as secure
        Map<Border, MonitoringResult> securityResultPerBorder = new EnumMap<>(Border.class);
        borders.forEach(border ->
                securityResultPerBorder.put(border,
                        new MonitoringResult(parameter, Collections.emptySet(), Collections.emptyMap(), Cnec.SecurityStatus.SECURE)
                )
        );

        // if no flow cnecs then return true
        boolean noFlowCnecs = monitoringInput.getBorders().stream()
                .allMatch(border -> monitoringInput.getCracForBorder(border).getCnecs(parameter).isEmpty());
        if (noFlowCnecs) {
            businessLogger.warn("No Flow CNECs defined for any border.");
            businessLogger.info("----- Monitoring flow CNECs for borders [end]");
            return securityResultPerBorder;
        }

        // Preventive evaluation
        Map<Border, State> preventiveStateMap = borders.stream()
                .collect(Collectors.toMap(
                        border -> border,
                        border -> monitoringInput.getCracForBorder(border).getPreventiveState()
                ));

        if (preventiveStateMap.values().stream().anyMatch(Objects::nonNull)) {
            borders.forEach(border -> applyOptimalRemedialActions(preventiveStateMap.get(border),
                    monitoringInput.getNetwork(), monitoringInput.getRaoResultForBorder(border)));

            Map<Border, Set<Cnec>> preventiveCnecsMap = borders.stream()
                    .collect(Collectors.toMap(
                            border -> border,
                            border -> monitoringInput.getCracForBorder(border).getCnecs(parameter,
                                    preventiveStateMap.get(border))
                    ));

            State anyPreventiveState = preventiveStateMap.values().stream()
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);

            Map<Border, MonitoringResult> preventiveResults = cnecEvaluator.evaluate(anyPreventiveState, preventiveCnecsMap, monitoringInput.getNetwork());

            preventiveResults.forEach((border, result) ->
                    securityResultPerBorder.get(border).combine(result)
            );
        }

        // If all borders failed already, stop
        if (securityResultPerBorder.values().stream().allMatch(v -> Objects.equals(v.getStatus(), Cnec.SecurityStatus.FAILURE))) {
            return securityResultPerBorder;
        }

        // Contingency states evaluation
        Map<State, EnumSet<Border>> contingencyStates = BorderStateMapper.mapContingencyStates(monitoringInput, parameter);
        if (contingencyStates.isEmpty()) {
            businessLogger.info("No contingency states present for network security evaluation.");
            businessLogger.info("----- Monitoring flow CNECs for borders [end]");
            return securityResultPerBorder;
        }
        try (AbstractNetworkPool networkPool = AbstractNetworkPool.create(monitoringInput.getNetwork(), monitoringInput.getNetwork().getVariantManager().getWorkingVariantId(), Math.min(numberOfLoadFlowsInParallel, contingencyStates.size()), true)) {
            List<ForkJoinTask<Void>> tasks = contingencyStates.entrySet().stream()
                    .map(entry -> submitParallelEvaluation(networkPool, entry.getKey(),
                            entry.getValue(), securityResultPerBorder))
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
            borders.forEach(border -> securityResultPerBorder.get(border).combine(new MonitoringResult(parameter, Collections.emptySet(), Collections.emptyMap(), Cnec.SecurityStatus.FAILURE)));
            return securityResultPerBorder;
        }
        businessLogger.info("----- Monitoring flow CNECs for borders [end]");
        return securityResultPerBorder;
    }

    private ForkJoinTask<Void> submitParallelEvaluation(AbstractNetworkPool networkPool,
                                                        State state,
                                                        Set<Border> impactedBorders,
                                                        Map<Border, MonitoringResult> securityResultPerBorder) {
        return networkPool.submit(() ->
                evaluateState(networkPool, state, impactedBorders, securityResultPerBorder)
        );
    }

    private Void evaluateState(AbstractNetworkPool networkPool,
                               State state,
                               Set<Border> impactedBorders,
                               Map<Border, MonitoringResult> securityResultPerBorder) throws InterruptedException {
        Network networkClone = networkPool.getAvailableNetwork();
        try {
            // Apply contingency for the given state
            if (!ResultValidatorHelper.applyContingency(state, networkClone)) {
                impactedBorders.forEach(border -> securityResultPerBorder.get(border)
                        .combine(new MonitoringResult(monitoringInput.getPhysicalParameter(), Collections.emptySet(),
                                Collections.emptyMap(), Cnec.SecurityStatus.FAILURE)));
                return null;
            }
            // Apply remedial actions for all impacted borders
            for (Border border : impactedBorders) {
                CracRaoResultPair input = monitoringInput.getCracRaoResultPair(border);
                State borderState = input.crac().getState(state.getContingency().orElseThrow(), state.getInstant());
                if (borderState != null) {
                    applyOptimalRemedialActionsOnContingencyState(borderState, networkClone, input.crac(), input.raoResult());
                }
            }
            // Evaluate security for each impacted border
            Map<Border, Set<Cnec>> impactedCnecsMap = impactedBorders.stream().collect(Collectors.toMap(border -> border,
                            border -> monitoringInput.getCracForBorder(border).getCnecs(monitoringInput.getPhysicalParameter(), state)));

            Map<Border, MonitoringResult> monitoringResults = cnecEvaluator.evaluate(state, impactedCnecsMap, networkClone);
            monitoringResults.forEach((border, result) -> securityResultPerBorder.get(border).combine(result));
            return null;

        } finally {
            networkPool.releaseUsedNetwork(networkClone);
        }
    }
}

