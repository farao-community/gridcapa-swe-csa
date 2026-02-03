package com.farao_community.farao.swe_csa.app.security_evaluator;

import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openrao.commons.OpenRaoException;
import com.powsybl.openrao.commons.PhysicalParameter;
import com.powsybl.openrao.commons.Unit;
import com.powsybl.openrao.data.crac.api.Crac;
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

import static com.farao_community.farao.swe_csa.app.security_evaluator.ResultValidatorHelper.*;

public class TwoBordersFlowCnecSecurityChecker {
    private final Network network;
    private final List<BorderContext> borders;
    private final Integer numberOfLoadFlowsInParallel;
    private final Logger businessLogger;
    private final String loadFlowProvider;
    private final LoadFlowParameters loadFlowParameters;

    public TwoBordersFlowCnecSecurityChecker(Network network, List<BorderContext> borders, int parallelism, Logger logger, String loadFlowProvider, LoadFlowParameters loadFlowParams
    ) {
        this.network = network;
        this.borders = borders;
        this.numberOfLoadFlowsInParallel = parallelism;
        this.businessLogger = logger;
        this.loadFlowProvider = loadFlowProvider;
        this.loadFlowParameters = loadFlowParams;
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
        PhysicalParameter parameter = PhysicalParameter.FLOW;
        Unit unit = Unit.AMPERE;

        // all borders start as secure
        Map<Border, MonitoringResult> securityResultPerBorder = new EnumMap<>(Border.class);
        borders.forEach(ctx -> securityResultPerBorder.put(ctx.border(),
                new MonitoringResult(parameter, Collections.emptySet(), Collections.emptyMap(), Cnec.SecurityStatus.SECURE)));

        // if no flow cnecs then return true
        boolean noFlowCnecs = borders.stream().allMatch(ctx -> ctx.crac().getCnecs(parameter).isEmpty());
        if (noFlowCnecs) {
            businessLogger.warn("No Flow CNECs defined for any border.");
            businessLogger.info("----- Monitoring flow CNECs for borders [end]");
            return securityResultPerBorder;
        }

        // Preventive evaluation
        Map<Border, State> preventiveStateMap = borders.stream().collect(Collectors.toMap(BorderContext::border, ctx -> ctx.crac().getPreventiveState()));
        if (preventiveStateMap.values().stream().anyMatch(Objects::nonNull)) {
            borders.forEach(ctx -> applyOptimalRemedialActions(preventiveStateMap.get(ctx.border()), network, ctx.raoResult()));

            Map<Border, Set<Cnec>> preventiveCnecsMap = borders.stream().collect(Collectors.toMap(BorderContext::border, ctx -> ctx.crac().getCnecs(parameter, preventiveStateMap.get(ctx.border()))));
            Map<Border, MonitoringResult> preventiveMonitoringResultMap = checkMargins(preventiveStateMap.values().stream().toList().getFirst(), preventiveCnecsMap, network);
            preventiveMonitoringResultMap.forEach((border, monitoringResult) -> {
                if (monitoringResult != null) {
                    securityResultPerBorder.get(border).combine(monitoringResult);
                }
            });
        }

        // If all borders failed already, stop
        if (securityResultPerBorder.values().stream().allMatch(v -> Objects.equals(v.getStatus(), Cnec.SecurityStatus.FAILURE))) {
            return securityResultPerBorder;
        }

        // Contingency states evaluation
        Map<State, EnumSet<Border>> contingencyStates = BorderStateMapper.mapContingencyStates(borders, parameter);
        if (contingencyStates.isEmpty()) {
            businessLogger.info("No contingency states present for network security evaluation.");
            businessLogger.info("----- Monitoring flow CNECs for borders [end]");
            return securityResultPerBorder;
        }
        try (AbstractNetworkPool networkPool = AbstractNetworkPool.create(network, network.getVariantManager().getWorkingVariantId(), Math.min(numberOfLoadFlowsInParallel, contingencyStates.size()), true)) {
            List<ForkJoinTask<Map<Border, Void>>> tasks = contingencyStates.entrySet().stream()
                    .map(entry -> submitParallelEvaluation(networkPool, entry.getKey(),
                            entry.getValue(), securityResultPerBorder, parameter))
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
            borders.forEach(ctx -> securityResultPerBorder.get(ctx.border()).combine(new MonitoringResult(parameter, Collections.emptySet(),
                    Collections.emptyMap(), Cnec.SecurityStatus.FAILURE)));
            return securityResultPerBorder;
        }
        businessLogger.info("----- Monitoring flow CNECs for borders [end]");
        return securityResultPerBorder;
    }

    private ForkJoinTask<Map<Border, Void>> submitParallelEvaluation(AbstractNetworkPool networkPool,
                                                                                 State state,
                                                                                 EnumSet<Border> impactedBorders,
                                                                                 Map<Border, MonitoringResult> securityResultPerBorder,
                                                                                 PhysicalParameter parameter) {
        return networkPool.submit(() -> evaluateState(networkPool, state, impactedBorders,
                securityResultPerBorder, parameter));
    }

    private Map<Border, Void> evaluateState(AbstractNetworkPool networkPool,
                                                        State state,
                                                        EnumSet<Border> impactedBorders,
                                                        Map<Border, MonitoringResult> securityResultPerBorder,
                                                        PhysicalParameter parameter) throws InterruptedException {
        Network networkClone = networkPool.getAvailableNetwork();
        try {
            // Apply contingency for the given state
            if (!ResultValidatorHelper.applyContingency(state, networkClone)) {
                Map<Border, MonitoringResult> faileMonitonringResultMap = new EnumMap<>(Border.class);
                borders.forEach(ctx -> securityResultPerBorder.put(ctx.border(),
                        new MonitoringResult(parameter, Collections.emptySet(), Collections.emptyMap(), Cnec.SecurityStatus.FAILURE)));
                securityResultPerBorder.forEach((border, result) -> result.combine(faileMonitonringResultMap.get(border)));
                return null;
            }
            // Apply remedial actions for all impacted borders
            for (Border border : impactedBorders) {
                BorderContext ctx = BorderContext.find(borders, border);
                State borderState = ctx.crac().getState(state.getContingency().orElseThrow(), state.getInstant());
                if (borderState != null) {
                    applyOptimalRemedialActionsOnContingencyState(borderState, networkClone, ctx.crac(), ctx.raoResult());
                }
            }
            // Evaluate security for each impacted border
            Map<Border, Set<Cnec>> impactedCnecMap = impactedBorders.stream()
                    .collect(Collectors.toMap(
                            border -> border,
                            border -> BorderContext.find(borders, border)
                                    .crac()
                                    .getCnecs(parameter, state)
                    ));

            Map<Border, MonitoringResult> monitoringResults =
                    checkMargins(state, impactedCnecMap, networkClone);
            monitoringResults.forEach((border, currentStateMonitoringResult) -> securityResultPerBorder.get(border).combine(currentStateMonitoringResult));
            return null;

        } finally {
            networkPool.releaseUsedNetwork(networkClone);
        }
    }

    public Map<Border, MonitoringResult> checkMargins(
            State state,
            Map<Border, Set<Cnec>> impactedCnecMap,
            Network network) {
        Map<Border, MonitoringResult> result = new EnumMap<>(Border.class);
        Unit unit = Unit.AMPERE;

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

