package com.farao_community.farao.swe_csa.app.security_evaluator;

import com.powsybl.computation.ComputationManager;
import com.powsybl.contingency.Contingency;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openrao.commons.OpenRaoException;
import com.powsybl.openrao.commons.PhysicalParameter;
import com.powsybl.openrao.commons.Unit;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.Instant;
import com.powsybl.openrao.data.crac.api.State;
import com.powsybl.openrao.data.crac.api.cnec.Cnec;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import com.powsybl.openrao.util.AbstractNetworkPool;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.farao_community.farao.swe_csa.app.security_evaluator.ResultValidatorHelper.*;

public class TwoBordersFlowCnecSecurityChecker {
    private final Network network;
    private final Crac frEsCrac;
    private final Crac ptEsCrac;
    private final RaoResult frEsRaoResult;
    private final RaoResult ptEsRaoResult;
    private final Integer numberOfLoadFlowsInParallel;
    private final Logger businessLogger;
    private final String loadFlowProvider;
    private final LoadFlowParameters loadFlowParameters;

    public TwoBordersFlowCnecSecurityChecker(Network network, Crac frEsCrac, Crac ptEsCrac, RaoResult frEsRaoResult, RaoResult ptEsRaoResult, int numberOfLoadFlowsInParallel, Logger businessLogger, String loadFlowProvider, LoadFlowParameters loadFlowParameters) {
        this.network = network;
        this.frEsCrac = frEsCrac;
        this.ptEsCrac = ptEsCrac;
        this.frEsRaoResult = frEsRaoResult;
        this.ptEsRaoResult = ptEsRaoResult;
        this.numberOfLoadFlowsInParallel = numberOfLoadFlowsInParallel;
        this.businessLogger = businessLogger;
        this.loadFlowProvider = loadFlowProvider;
        this.loadFlowParameters = loadFlowParameters;

    }

    /**
     * Check if all the flowCnecs is secure when considering the application of
     * RAs from 2 borders
     *
     * @return True if all flowCnecs are secure and False if there is at least
     * one flowCnec is not secure
     */
    public Boolean check() {
        businessLogger.info("----- Monitoring flow cnecs for two borders [start]");

        PhysicalParameter flowPhysicalParameter = PhysicalParameter.FLOW;
        Unit unit = Unit.AMPERE;

        Set<Cnec> frEsFlowCnecs = frEsCrac.getCnecs(flowPhysicalParameter);
        Set<Cnec> ptEsFlowCnecs = ptEsCrac.getCnecs(flowPhysicalParameter);

        if (frEsFlowCnecs.isEmpty() && ptEsFlowCnecs.isEmpty()) {
            businessLogger.warn("No Flow Cnecs defined in two borders.");
            businessLogger.info("----- Monitoring flow cnecs for two borders [end]");
            return Boolean.TRUE;
        }

        // Preventive states
        State frEsPreventiveState = frEsCrac.getPreventiveState();
        State ptEsPreventiveState = ptEsCrac.getPreventiveState();

        if (frEsPreventiveState != null || ptEsPreventiveState != null) {

            // Apply all the optimal RAs proposed by OpenRAO
            applyOptimalRemedialActions(frEsPreventiveState, network, frEsRaoResult);
            applyOptimalRemedialActions(ptEsPreventiveState, network, ptEsRaoResult);

            // Compute the load-flow
            if (!computeLoadFlow(network, loadFlowProvider, loadFlowParameters)) {
                businessLogger.warn("Load-flow computation failed at preventive state when validating network for both borders.");
                return false;
            }

            // Check all the cnecs from two borders
            if (!checkMargins(frEsCrac, frEsPreventiveState, flowPhysicalParameter, network, unit) || !checkMargins(ptEsCrac, ptEsPreventiveState, flowPhysicalParameter, network, unit)) {
                return false;
            }
        }

        // Contingency states
        Set<State> frEsContingencyStates = frEsCrac.getCnecs(flowPhysicalParameter).stream().map(Cnec::getState).filter(state -> !state.isPreventive()).collect(Collectors.toSet());
        Set<State> ptEsContingencyStates = ptEsCrac.getCnecs(flowPhysicalParameter).stream().map(Cnec::getState).filter(state -> !state.isPreventive()).collect(Collectors.toSet());

        if (frEsContingencyStates.isEmpty() && ptEsContingencyStates.isEmpty()) {
            businessLogger.info("----- Monitoring flow cnecs for two borders [end]");
            return true;
        }

        // FR-ES contingency processing
        try (AbstractNetworkPool networkPool = AbstractNetworkPool.create(network, network.getVariantManager().getWorkingVariantId(), Math.min(numberOfLoadFlowsInParallel, frEsContingencyStates.size()), true)) {
            List<ForkJoinTask<Boolean>> frEsTasks = frEsContingencyStates.stream().map(frEsState -> networkPool.submit(() -> {
                Network networkClone = networkPool.getAvailableNetwork();
                Contingency contingency = frEsState.getContingency().orElseThrow();
                if (!contingency.isValid(networkClone)) {
                    networkPool.releaseUsedNetwork(networkClone);
                    return false;
                }
                // Apply the CO to the network
                contingency.toModification().apply(networkClone, (ComputationManager) null);

                // Apply the optimal RA of fr-es border
                applyOptimalRemedialActionsOnContingencyState(frEsState, networkClone, frEsCrac, frEsRaoResult);

                // If the state exist also in the border pt-es, treat the related RAs and remove the state
                Instant instant = frEsState.getInstant();
                State ptEsState = ptEsCrac.getState(contingency, instant);
                boolean ptEsSecurity = true;
                // from the original list ptEsContingencyStates and compute the margin of cnecs
                if (ptEsState != null) {
                    applyOptimalRemedialActionsOnContingencyState(ptEsState, networkClone, ptEsCrac, ptEsRaoResult);

                    ptEsContingencyStates.remove(ptEsState);
                    ptEsSecurity = checkMargins(ptEsCrac, ptEsState, flowPhysicalParameter, network, unit);

                }
                // compute the margin of related cnecs
                boolean frEsSecurity = checkMargins(frEsCrac, frEsState, flowPhysicalParameter, network, unit);
                networkPool.releaseUsedNetwork(networkClone);

                // Network secure when both borders are secure
                return frEsSecurity && ptEsSecurity;
            })).toList();

            // Gather all parallel tasks, return false if there is any task returning false
            boolean allTrue = true;
            try {
                for (ForkJoinTask<Boolean> task : frEsTasks) {
                    try {
                        if (!task.get()) {
                            allTrue = false;
                        }
                    } catch (Exception e) {
                        throw new OpenRaoException(e);
                    }
                }
            } finally {
                networkPool.shutdownAndAwaitTermination(24, TimeUnit.HOURS);
            }
            if (!allTrue) {
                return false;
            }

        } catch (Exception e) {
            Thread.currentThread().interrupt();
            // Return false if an error is thrown
            return false;
        }

        // PT-ES contingency processing
        if (ptEsContingencyStates.isEmpty()) {
            businessLogger.info("----- Monitoring flow cnecs for two borders [end]");
            return true;
        }

        try (AbstractNetworkPool networkPool = AbstractNetworkPool.create(network, network.getVariantManager().getWorkingVariantId(), Math.min(numberOfLoadFlowsInParallel, ptEsContingencyStates.size()), true)) {

            List<ForkJoinTask<Boolean>> ptEsTasks = ptEsContingencyStates.stream().map(ptEsState -> networkPool.submit(() -> {
                Network networkClone = networkPool.getAvailableNetwork();
                Contingency contingency = ptEsState.getContingency().orElseThrow();

                if (!contingency.isValid(networkClone)) {
                    return false;
                }

                contingency.toModification().apply(networkClone, (ComputationManager) null);

                applyOptimalRemedialActionsOnContingencyState(ptEsState, networkClone, ptEsCrac, ptEsRaoResult);

                boolean ptEsSecure = checkMargins(ptEsCrac, ptEsState, flowPhysicalParameter, network, unit);
                networkPool.releaseUsedNetwork(networkClone);
                return ptEsSecure;

            })).toList();

            boolean allTrue = true;
            try {
                for (ForkJoinTask<Boolean> task : ptEsTasks) {
                    try {
                        if (!task.get()) {
                            allTrue = false;
                        }
                    } catch (Exception e) {
                        throw new OpenRaoException(e);
                    }
                }
            } finally {
                networkPool.shutdownAndAwaitTermination(24, TimeUnit.HOURS);
            }
            if (!allTrue) {
                return false;
            }

        } catch (Exception e) {
            Thread.currentThread().interrupt();
            // Return false if an error is thrown
            return false;
        }

        businessLogger.info("----- Monitoring flow cnecs for two borders [end]");
        return true;
    }
}

