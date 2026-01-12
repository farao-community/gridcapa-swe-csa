package com.farao_community.farao.swe_csa.app.dichotomy;

import com.farao_community.farao.swe_csa.api.exception.CsaInternalException;
import com.powsybl.action.*;
import com.powsybl.computation.ComputationManager;
import com.powsybl.contingency.Contingency;
import com.powsybl.glsk.commons.CountryEICode;
import com.powsybl.glsk.commons.ZonalData;
import com.powsybl.iidm.modification.scalable.Scalable;
import com.powsybl.iidm.network.*;
import com.powsybl.loadflow.LoadFlow;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.LoadFlowResult;
import com.powsybl.openrao.commons.OpenRaoException;
import com.powsybl.openrao.commons.PhysicalParameter;
import com.powsybl.openrao.commons.Unit;
import com.powsybl.openrao.commons.logs.OpenRaoLoggerProvider;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.Instant;
import com.powsybl.openrao.data.crac.api.RemedialAction;
import com.powsybl.openrao.data.crac.api.State;
import com.powsybl.openrao.data.crac.api.cnec.Cnec;
import com.powsybl.openrao.data.crac.api.cnec.CnecValue;
import com.powsybl.openrao.data.crac.api.networkaction.NetworkAction;
import com.powsybl.openrao.data.crac.api.usagerule.OnConstraint;
import com.powsybl.openrao.data.crac.impl.AngleCnecValue;
import com.powsybl.openrao.data.crac.impl.VoltageCnecValue;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import com.powsybl.openrao.monitoring.MonitoringInput;
import com.powsybl.openrao.monitoring.redispatching.RedispatchAction;
import com.powsybl.openrao.monitoring.results.CnecResult;
import com.powsybl.openrao.monitoring.results.MonitoringResult;
import com.powsybl.openrao.monitoring.results.RaoResultWithAngleMonitoring;
import com.powsybl.openrao.monitoring.results.RaoResultWithVoltageMonitoring;
import com.powsybl.openrao.util.AbstractNetworkPool;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class SweCsaRaoResultValidator {

    private final String loadFlowProvider;
    private final LoadFlowParameters loadFlowParameters;
    Map<PhysicalParameter, Unit> parameterToUnitMap = new HashMap<>();
    private final Logger businessLogger;

    public SweCsaRaoResultValidator(String loadFlowProvider, LoadFlowParameters loadFlowParameters, Logger businessLogger) {
        this.loadFlowProvider = loadFlowProvider;
        this.loadFlowParameters = loadFlowParameters;
        this.parameterToUnitMap.put(PhysicalParameter.ANGLE, Unit.DEGREE);
        this.parameterToUnitMap.put(PhysicalParameter.VOLTAGE, Unit.KILOVOLT);
        this.businessLogger = businessLogger;
    }

    /**
     * Check if the network is secure and update raoResults with Angle/Voltage monitoring
     * when considering Cnecs and RAs from 2 borders
     *
     * @param network                                  the network
     * @param parallelDichotomiesResult                results of dichotomy computation after validating and monitoring for each border separately
     * @param frEsCrac                                 crac of fr-es border
     * @param ptEsCrac                                 crac of pt-es border
     * @param scalableZonalDataFilteredForSweCountries used for the redispatching in the case of Angle monitoring
     * @return ParallelDichotomiesResult updated parallelDichotomiesResult after validating for both borders at once and
     * updating angle/voltage monitoring results
     */
    private ParallelDichotomiesResult validateNetworkForTwoBorders(Network network, ParallelDichotomiesResult parallelDichotomiesResult, Crac frEsCrac, Crac ptEsCrac, ZonalData<Scalable> scalableZonalDataFilteredForSweCountries) {
        RaoResult frEsRaoResult = parallelDichotomiesResult.getPtEsResult().getRaoResult();
        RaoResult ptEsRaoResult = parallelDichotomiesResult.getPtEsResult().getRaoResult();
        try {
            // Check if all the flowCnecs in two borders are secure after applying all RAs from two borders
            Boolean isSecure = checkFlowCnecSecurityForTwoBorders(network, frEsCrac, ptEsCrac, frEsRaoResult, ptEsRaoResult, Runtime.getRuntime().availableProcessors());
            if (isSecure && (!frEsCrac.getAngleCnecs().isEmpty() || !ptEsCrac.getAngleCnecs().isEmpty())) {
                // If angleCnecs exist, Angle monitoring
                Pair<RaoResult, RaoResult> raoResultPair = updateRaoResultsWithAngleMonitoringForTwoBorders(network, frEsCrac, ptEsCrac, scalableZonalDataFilteredForSweCountries, frEsRaoResult, ptEsRaoResult);
                isSecure = raoResultPair.getLeft().isSecure(PhysicalParameter.FLOW, PhysicalParameter.ANGLE) && raoResultPair.getRight().isSecure(PhysicalParameter.FLOW, PhysicalParameter.ANGLE);
                if (isSecure) {
                    businessLogger.info("Angle monitoring secure for both borders, Final result will contain Angle monitoring results");
                } else {
                    businessLogger.info("Angle monitoring unsecure for both border");
                }
            }

            if (isSecure && (!frEsCrac.getVoltageCnecs().isEmpty() || !ptEsCrac.getVoltageCnecs().isEmpty())) {
                // If voltageCnecs exist, Voltage monitoring
                Pair<RaoResult, RaoResult> raoResultPair = updateRaoResultsWithVoltageMonitoringForTwoBorders(network, frEsCrac, ptEsCrac, frEsRaoResult, ptEsRaoResult);
                isSecure = raoResultPair.getLeft().isSecure(PhysicalParameter.FLOW, PhysicalParameter.VOLTAGE) && raoResultPair.getRight().isSecure(PhysicalParameter.FLOW, PhysicalParameter.VOLTAGE);
                if (isSecure) {
                    businessLogger.info("Voltage monitoring secure for both borders, Final result will contain Voltage monitoring results");
                } else {
                    businessLogger.info("Voltage monitoring unsecure for both borders");
                }
            }

            DichotomyStepResult frEsDichotomyResult = DichotomyStepResult.fromNetworkValidationResult(frEsRaoResult, isSecure, parallelDichotomiesResult.getFrEsResult().getRaoSuccessResponse(), parallelDichotomiesResult.getCounterTradingValues());
            DichotomyStepResult ptEsDichotomyResult = DichotomyStepResult.fromNetworkValidationResult(frEsRaoResult, isSecure, parallelDichotomiesResult.getPtEsResult().getRaoSuccessResponse(), parallelDichotomiesResult.getCounterTradingValues());
            // Return the updated parallelDichotomiesResult
            return new ParallelDichotomiesResult(frEsDichotomyResult, ptEsDichotomyResult, parallelDichotomiesResult.getCounterTradingValues());
        } catch (Exception e) {
            throw new CsaInternalException(MDC.get("gridcapaTaskId"), "RAO run failed", e);
        }
    }

    /**
     * Check if all the flowCnecs is secure when considering the application of
     * RAs from 2 borders
     *
     * @param network                     network
     * @param numberOfLoadFlowsInParallel used for the parallelisation of loadFlow
     * @return True if all flowCnecs are secure and False if there is at least
     * one flowCnec is not secure
     */
    private Boolean checkFlowCnecSecurityForTwoBorders(Network network,
                                                       Crac frEsCrac,
                                                       Crac ptEsCrac,
                                                       RaoResult frEsRaoResult,
                                                       RaoResult ptEsRaoResult,
                                                       int numberOfLoadFlowsInParallel) {

        businessLogger.info("----- Monitoring flow cnecs for two borders [start]");

        PhysicalParameter flowPhysicalParameter = PhysicalParameter.FLOW;
        Unit unit = parameterToUnitMap.get(flowPhysicalParameter);

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
            // Note: check if applyOptimalRemedialActions works when ptEsPreventiveState is null
            applyOptimalRemedialActions(ptEsPreventiveState, network, ptEsRaoResult);

            // Compute the load-flow
            if (!computeLoadFlow(network)) {
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
                    return false;
                }

                Instant instant = frEsState.getInstant();
                State ptEsState = ptEsCrac.getState(contingency, instant);

                // Apply the CO to the network
                contingency.toModification().apply(networkClone, (ComputationManager) null);

                // Apply the optimal RA of fr-es border
                applyOptimalRemedialActionsOnContingencyState(
                        frEsState, networkClone, frEsCrac, frEsRaoResult);

                // If the state exist also in the border pt-es, treat the related RAs and remove the state
                // from the original list ptEsContingencyStates and compute the margin of cnecs
                if (ptEsState != null) {
                    applyOptimalRemedialActionsOnContingencyState(
                            ptEsState, networkClone, ptEsCrac, ptEsRaoResult);

                    ptEsContingencyStates.remove(ptEsState);
                }
                // compute the margin of related cnecs
                boolean frEsSecurity = checkMargins(frEsCrac, frEsState, flowPhysicalParameter, network, unit);
                boolean ptEsSecurity = checkMargins(ptEsCrac, ptEsState, flowPhysicalParameter, network, unit);
                networkPool.releaseUsedNetwork(networkClone);

                // Network secure when both borders are secure
                return frEsSecurity && ptEsSecurity;
            })).toList();

            // Gather all parallel tasks, return false if there is any task returning false
            boolean allTrue = frEsTasks.stream().allMatch(task -> {
                try {
                    return task.get();
                } catch (Exception e) {
                    throw new OpenRaoException(e);
                }
            });
            networkPool.shutdownAndAwaitTermination(24, TimeUnit.HOURS);
            if (!allTrue) {
                return false;
            }

        } catch (Exception e) {
            Thread.currentThread().interrupt();
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
                    return Boolean.FALSE;
                }

                contingency.toModification().apply(networkClone, (ComputationManager) null);

                applyOptimalRemedialActionsOnContingencyState(
                        ptEsState, networkClone, ptEsCrac, ptEsRaoResult);

                boolean ptEsSecure = checkMargins(ptEsCrac, ptEsState, flowPhysicalParameter, network, unit);
                networkPool.releaseUsedNetwork(networkClone);
                return ptEsSecure;

            })).toList();

            boolean allTrue = ptEsTasks.stream().allMatch(task -> {
                try {
                    return task.get();
                } catch (Exception e) {
                    throw new OpenRaoException(e);
                }
            });
            networkPool.shutdownAndAwaitTermination(24, TimeUnit.HOURS);
            if (!allTrue) {
                return false;
            }

        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }

        businessLogger.info("----- Monitoring flow cnecs for two borders [end]");
        return true;
    }

    private Pair<RaoResult, RaoResult> updateRaoResultsWithAngleMonitoringForTwoBorders(Network network, Crac frEsCrac, Crac ptEsCrac, ZonalData<Scalable> scalableZonalDataFilteredForSweCountries, RaoResult frEsRaoResult, RaoResult ptEsRaoResult) {
        MonitoringInput frEsAngleMonitoringInput = MonitoringInput.buildWithAngle(network, frEsCrac, frEsRaoResult, scalableZonalDataFilteredForSweCountries).build();
        MonitoringInput ptEsAngleMonitoringInput = MonitoringInput.buildWithAngle(network, ptEsCrac, ptEsRaoResult, scalableZonalDataFilteredForSweCountries).build();
        Pair<MonitoringResult, MonitoringResult> angleMonitoringResultPair = runMonitoringForTwoBorders(frEsAngleMonitoringInput, ptEsAngleMonitoringInput, Runtime.getRuntime().availableProcessors());
        RaoResult frEsRaoResultWithAngleMonitoring = new RaoResultWithAngleMonitoring(frEsRaoResult, angleMonitoringResultPair.getLeft());
        RaoResult ptEsRaoResultWithAngleMonitoring = new RaoResultWithAngleMonitoring(ptEsRaoResult, angleMonitoringResultPair.getRight());
        return Pair.of(frEsRaoResultWithAngleMonitoring, ptEsRaoResultWithAngleMonitoring);
    }

    private Pair<RaoResult, RaoResult> updateRaoResultsWithVoltageMonitoringForTwoBorders(Network network, Crac frEsCrac, Crac ptEsCrac, RaoResult frEsRaoResult, RaoResult ptEsRaoResult) {
        MonitoringInput frEsAngleMonitoringInput = MonitoringInput.buildWithVoltage(network, frEsCrac, frEsRaoResult).build();
        MonitoringInput ptEsAngleMonitoringInput = MonitoringInput.buildWithVoltage(network, ptEsCrac, ptEsRaoResult).build();
        Pair<MonitoringResult, MonitoringResult> voltageMonitoringResultPair = runMonitoringForTwoBorders(frEsAngleMonitoringInput, ptEsAngleMonitoringInput, Runtime.getRuntime().availableProcessors());
        RaoResult frEsRaoResultWithAngleMonitoring = new RaoResultWithVoltageMonitoring(frEsRaoResult, voltageMonitoringResultPair.getLeft());
        RaoResult ptEsRaoResultWithAngleMonitoring = new RaoResultWithVoltageMonitoring(ptEsRaoResult, voltageMonitoringResultPair.getRight());
        return Pair.of(frEsRaoResultWithAngleMonitoring, ptEsRaoResultWithAngleMonitoring);
    }

    private Pair<MonitoringResult, MonitoringResult> runMonitoringForTwoBorders(MonitoringInput frEsMonitoringInput, MonitoringInput ptEsMonitoringInput, int numberOfLoadFlowsInParallel) {
        // Inspired by the class Monitoring
        PhysicalParameter physicalParameter = frEsMonitoringInput.getPhysicalParameter();
        Network inputNetwork = frEsMonitoringInput.getNetwork();
        Crac frEsCrac = frEsMonitoringInput.getCrac();
        Crac ptEsCrac = ptEsMonitoringInput.getCrac();
        RaoResult frEsRaoResult = frEsMonitoringInput.getRaoResult();
        RaoResult ptEsRaoResult = ptEsMonitoringInput.getRaoResult();
        // Initial two empty monitoringResults
        MonitoringResult frEsMonitoringResult = new MonitoringResult(physicalParameter, Collections.emptySet(), Collections.emptyMap(), Cnec.SecurityStatus.SECURE);
        MonitoringResult ptEsMonitoringResult = new MonitoringResult(physicalParameter, Collections.emptySet(), Collections.emptyMap(), Cnec.SecurityStatus.SECURE);

        businessLogger.info("----- {} monitoring for two borders [start]", physicalParameter);
        Set<Cnec> frEsCnecs = frEsCrac.getCnecs(physicalParameter);
        Set<Cnec> ptEsCnecs = ptEsCrac.getCnecs(physicalParameter);
        if (frEsCnecs.isEmpty() && ptEsCnecs.isEmpty()) {
            businessLogger.warn("No Cnecs of type '{}' defined.", physicalParameter);
            businessLogger.info("----- {} monitoring for two borders [end]", physicalParameter);
            return Pair.of(frEsMonitoringResult, ptEsMonitoringResult);
        }

        // Preventive states
        State frEsPreventiveState = frEsCrac.getPreventiveState();
        State ptEsPreventiveState = ptEsCrac.getPreventiveState();
        if (Objects.nonNull(frEsPreventiveState) || Objects.nonNull(ptEsPreventiveState)) {

            applyOptimalRemedialActions(frEsPreventiveState, inputNetwork, frEsRaoResult);
            applyOptimalRemedialActions(ptEsPreventiveState, inputNetwork, ptEsRaoResult);

            Set<Cnec> frEsPreventiveStateCnecs = frEsCrac.getCnecs(physicalParameter, frEsPreventiveState);
            Set<Cnec> ptEsPreventiveStateCnecs = ptEsCrac.getCnecs(physicalParameter, ptEsPreventiveState);

            Pair<MonitoringResult, MonitoringResult> preventiveMonitoringResultsPair = monitorCnecsForTwoBorders(frEsPreventiveState, ptEsPreventiveState, frEsPreventiveStateCnecs, ptEsPreventiveStateCnecs,
                    inputNetwork, frEsMonitoringInput, ptEsMonitoringInput);

            MonitoringResult leftResult = preventiveMonitoringResultsPair.getLeft();
            if (Objects.nonNull(leftResult)) {
                frEsMonitoringResult.combine(leftResult);
            }
            MonitoringResult rightResult = preventiveMonitoringResultsPair.getRight();
            if (Objects.nonNull(rightResult)) {
                ptEsMonitoringResult.combine(rightResult);
            }

        }

        // Contingency States
        Set<State> frEsContingencyStates = frEsCrac.getCnecs(physicalParameter).stream().map(Cnec::getState).filter(state -> !state.isPreventive()).collect(Collectors.toSet());
        Set<State> ptEsContingencyStates = ptEsCrac.getCnecs(physicalParameter).stream().map(Cnec::getState).filter(state -> !state.isPreventive()).collect(Collectors.toSet());
        if (!frEsContingencyStates.isEmpty()) {
            try (AbstractNetworkPool networkPool = AbstractNetworkPool.create(inputNetwork, inputNetwork.getVariantManager().getWorkingVariantId(), Math.min(numberOfLoadFlowsInParallel, frEsContingencyStates.size()), true)) {
                List<ForkJoinTask<Object>> frEsTasks = frEsContingencyStates.stream().map(frEsState -> networkPool.submit(() -> {
                    Network networkClone = networkPool.getAvailableNetwork();
                    Contingency contingency = frEsState.getContingency().orElseThrow();
                    Instant instant = frEsState.getInstant();
                    State ptEsState = ptEsCrac.getState(contingency, instant);
                    if (!contingency.isValid(networkClone)) {
                        businessLogger.warn("Unable to apply contingency " + contingency.getId());
                        Pair<MonitoringResult, MonitoringResult> faileMonitonringResults = makeFailedMonitoringResultForStateWithNaNCnecRsults(frEsMonitoringInput, ptEsMonitoringInput, physicalParameter, frEsState, ptEsState, "Unable to apply contingency " + contingency.getId());
                        if (Objects.nonNull(faileMonitonringResults.getLeft())) {
                            frEsMonitoringResult.combine(faileMonitonringResults.getLeft());
                        }
                        if (Objects.nonNull(ptEsState)) {
                            // If ptEsState exist in frEs crac, retrieve it from the ptEsCOStates list
                            ptEsContingencyStates.remove(ptEsState);
                            if (Objects.nonNull(faileMonitonringResults.getRight())) {
                                ptEsMonitoringResult.combine(faileMonitonringResults.getRight());
                            }
                        }
                        networkPool.releaseUsedNetwork(networkClone);
                        return null;
                    } else {
                        contingency.toModification().apply(networkClone, (ComputationManager) null);
                        applyOptimalRemedialActionsOnContingencyState(frEsState, networkClone, frEsCrac, frEsRaoResult);
                        Set<Cnec> frEsCurrentStateCnecs = frEsCrac.getCnecs(physicalParameter, frEsState);
                        Set<Cnec> ptEsCurrentStateCnecs = new HashSet<>();

                        // If the state exists in both borders
                        if (ptEsState != null) {
                            applyOptimalRemedialActionsOnContingencyState(ptEsState, networkClone, ptEsCrac, ptEsRaoResult);
                            ptEsCurrentStateCnecs = ptEsCrac.getCnecs(physicalParameter, ptEsState);
                        }
                        Pair<MonitoringResult, MonitoringResult> currentStateMonitoringResults = monitorCnecsForTwoBorders(frEsState, ptEsState, frEsCurrentStateCnecs, ptEsCurrentStateCnecs, networkClone, frEsMonitoringInput, ptEsMonitoringInput);
                        currentStateMonitoringResults.getLeft().printConstraints().forEach(businessLogger::info);
                        frEsMonitoringResult.combine(currentStateMonitoringResults.getLeft());
                        if (ptEsState != null) {
                            currentStateMonitoringResults.getRight().printConstraints().forEach(businessLogger::info);
                            ptEsMonitoringResult.combine(currentStateMonitoringResults.getRight());
                        }
                        networkPool.releaseUsedNetwork(networkClone);
                        return null;
                    }
                })).toList();
                for (ForkJoinTask<Object> task : frEsTasks) {
                    try {
                        task.get();
                    } catch (ExecutionException e) {
                        throw new OpenRaoException(e);
                    }
                }
                networkPool.shutdownAndAwaitTermination(24, TimeUnit.HOURS);

            } catch (Exception var19) {
                Thread.currentThread().interrupt();
                frEsMonitoringResult.setStatusToFailure();
                ptEsMonitoringResult.setStatusToFailure();
            }
        }

        if (!ptEsContingencyStates.isEmpty()) {
            try (AbstractNetworkPool networkPool = AbstractNetworkPool.create(inputNetwork, inputNetwork.getVariantManager().getWorkingVariantId(), Math.min(numberOfLoadFlowsInParallel, ptEsContingencyStates.size()), true)) {
                List<ForkJoinTask<Object>> ptEsTasks = ptEsContingencyStates.stream().map(ptEsState -> networkPool.submit(() -> {
                    Network networkClone = networkPool.getAvailableNetwork();
                    Contingency contingency = ptEsState.getContingency().orElseThrow();
                    if (!contingency.isValid(networkClone)) {
                        businessLogger.warn("Unable to apply contingency " + contingency.getId());
                        Pair<MonitoringResult, MonitoringResult> faileMonitonringResults = makeFailedMonitoringResultForStateWithNaNCnecRsults(ptEsMonitoringInput, null, physicalParameter, ptEsState, null, "Unable to apply contingency " + contingency.getId());
                        ptEsMonitoringResult.combine(faileMonitonringResults.getLeft());
                        networkPool.releaseUsedNetwork(networkClone);
                        return null;
                    } else {
                        contingency.toModification().apply(networkClone, (ComputationManager) null);
                        applyOptimalRemedialActionsOnContingencyState(ptEsState, networkClone, ptEsCrac, ptEsRaoResult);
                        Set<Cnec> ptEsCurrentStateCnecs = ptEsCrac.getCnecs(physicalParameter, ptEsState);
                        Pair<MonitoringResult, MonitoringResult> currentStateMonitoringResults = monitorCnecsForTwoBorders(ptEsState, null, ptEsCurrentStateCnecs, null, networkClone, ptEsMonitoringInput, null);
                        currentStateMonitoringResults.getLeft().printConstraints().forEach(businessLogger::info);
                        ptEsMonitoringResult.combine(currentStateMonitoringResults.getLeft());
                        networkPool.releaseUsedNetwork(networkClone);
                        return null;
                    }
                })).toList();
                for (ForkJoinTask<Object> task : ptEsTasks) {
                    try {
                        task.get();
                    } catch (ExecutionException e) {
                        throw new OpenRaoException(e);
                    }
                }
                networkPool.shutdownAndAwaitTermination(24, TimeUnit.HOURS);

            } catch (Exception var19) {
                Thread.currentThread().interrupt();
                ptEsMonitoringResult.setStatusToFailure();
            }
        }

        businessLogger.info("----- {} monitoring [end]", physicalParameter);
        frEsMonitoringResult.printConstraints().forEach(businessLogger::info);
        ptEsMonitoringResult.printConstraints().forEach(businessLogger::info);
        return Pair.of(frEsMonitoringResult, ptEsMonitoringResult);
    }

    private Pair<MonitoringResult, MonitoringResult> monitorCnecsForTwoBorders(State primaryState, State secondaryState, Set<Cnec> primaryCnecs, Set<Cnec> secondaryCnecs, Network network, MonitoringInput primaryMonitoringInput, MonitoringInput secondaryMonitoringInput) {
        PhysicalParameter physicalParameter = primaryMonitoringInput.getPhysicalParameter();
        Unit unit = parameterToUnitMap.get(physicalParameter);
        Set<CnecResult> primaryCnecResults = new HashSet<>();
        Set<CnecResult> secondaryCnecResults = new HashSet<>();
        businessLogger.info("-- '{}' Monitoring at state '{}' for two borders [start]", physicalParameter, primaryState);
        boolean lfSuccess = computeLoadFlow(network);
        if (!lfSuccess) {
            String failureReason = String.format("Load-flow computation failed at state %s. Skipping this state.", primaryState);
            return makeFailedMonitoringResultForStateWithNaNCnecRsults(primaryMonitoringInput, secondaryMonitoringInput, physicalParameter, primaryState, secondaryState, failureReason);
        } else {
            List<AppliedNetworkActionsResult> appliedNetworkActionsResultList = new ArrayList<>();
            primaryCnecs.forEach(primaryCnec -> {
                if (primaryCnec.computeMargin(network, unit) < 0) {
                    // Get associated RAs to the Cnec
                    Set<NetworkAction> availableNetworkActions = getNetworkActionsAssociatedToCnec(primaryState, primaryMonitoringInput.getCrac(), primaryCnec, physicalParameter);
                    if (!availableNetworkActions.isEmpty()) {
                        // Apply the associated RAs
                        AppliedNetworkActionsResult appliedNetworkActionsResult = applyNetworkActions(network, availableNetworkActions, primaryCnec.getId(), primaryMonitoringInput);
                        if (!appliedNetworkActionsResult.getAppliedNetworkActions().isEmpty()) {
                            appliedNetworkActionsResultList.add(appliedNetworkActionsResult);
                        }
                    }
                }
                // Update the cnecResults
                CnecResult primaryCnecResult = new CnecResult(primaryCnec, unit, primaryCnec.computeValue(network, unit), primaryCnec.computeMargin(network, unit), primaryCnec.computeSecurityStatus(network, unit));
                primaryCnecResults.add(primaryCnecResult);
            });

            if (!secondaryCnecs.isEmpty()) {
                secondaryCnecs.forEach(secondaryCnec -> {
                    if (secondaryCnec.computeMargin(network, unit) < 0) {
                        Set<NetworkAction> availableNetworkActions = getNetworkActionsAssociatedToCnec(secondaryState, secondaryMonitoringInput.getCrac(), secondaryCnec, physicalParameter);
                        if (!availableNetworkActions.isEmpty()) {
                            AppliedNetworkActionsResult appliedNetworkActionsResult = applyNetworkActions(network, availableNetworkActions, secondaryCnec.getId(), secondaryMonitoringInput);
                            if (!appliedNetworkActionsResult.getAppliedNetworkActions().isEmpty()) {
                                appliedNetworkActionsResultList.add(appliedNetworkActionsResult);
                            }
                        }
                    }
                    CnecResult secondaryCnecResult = new CnecResult(secondaryCnec, unit, secondaryCnec.computeValue(network, unit), secondaryCnec.computeMargin(network, unit), secondaryCnec.computeSecurityStatus(network, unit));
                    secondaryCnecResults.add(secondaryCnecResult);
                });
            }
            // Re-dispatch the network (for angleCnecs)
            redispatchNetworkActions(network, appliedNetworkActionsResultList, primaryMonitoringInput.getScalableZonalData());
            if (appliedNetworkActionsResultList.stream().map(AppliedNetworkActionsResult::getAppliedNetworkActions).findAny().isPresent()) {
                lfSuccess = computeLoadFlow(network);
                if (!lfSuccess) {
                    businessLogger.warn("Load-flow computation failed at state {} after applying RAs. Skipping this state.", primaryState);
                    MonitoringResult primaryMonitoringResult = new MonitoringResult(physicalParameter, primaryCnecResults, Map.of(primaryState, Collections.emptySet()), Cnec.SecurityStatus.FAILURE);
                    MonitoringResult secondaryMonitoringResult = new MonitoringResult(physicalParameter, secondaryCnecResults, Map.of(primaryState, Collections.emptySet()), Cnec.SecurityStatus.FAILURE);
                    return Pair.of(primaryMonitoringResult, secondaryMonitoringResult);
                }

                primaryCnecResults.clear();
                secondaryCnecResults.clear();
                primaryCnecs.forEach(cnec -> {
                    CnecResult primaryCnecResult = new CnecResult(cnec, unit, cnec.computeValue(network, unit), cnec.computeMargin(network, unit), cnec.computeSecurityStatus(network, unit));
                    primaryCnecResults.add(primaryCnecResult);
                });

                if (!secondaryCnecs.isEmpty()) {
                    secondaryCnecs.forEach(cnec -> {
                        CnecResult secondaryCnecResult = new CnecResult(cnec, unit, cnec.computeValue(network, unit), cnec.computeMargin(network, unit), cnec.computeSecurityStatus(network, unit));
                        secondaryCnecResults.add(secondaryCnecResult);
                    });
                }
            }

            Cnec.SecurityStatus primaryMonitoringResultStatus = Cnec.SecurityStatus.SECURE;
            if (primaryCnecResults.stream().anyMatch(cnecResult -> cnecResult.getMargin() < 0)) {
                primaryMonitoringResultStatus = MonitoringResult.combineStatuses(primaryCnecResults.stream().map(CnecResult::getCnecSecurityStatus).toArray(Cnec.SecurityStatus[]::new));
            }
            Cnec.SecurityStatus secondaryMonitoringResultStatus = Cnec.SecurityStatus.SECURE;
            if (secondaryCnecResults.stream().anyMatch(cnecResult -> cnecResult.getMargin() < 0)) {
                secondaryMonitoringResultStatus = MonitoringResult.combineStatuses(secondaryCnecResults.stream().map(CnecResult::getCnecSecurityStatus).toArray(Cnec.SecurityStatus[]::new));
            }

            businessLogger.info("-- '{}' Monitoring at state '{}' [end]", physicalParameter, primaryState);
            return Pair.of(new MonitoringResult(physicalParameter, primaryCnecResults, Map.of(primaryState, appliedNetworkActionsResultList.stream().flatMap(r -> r.getAppliedNetworkActions().stream()).collect(Collectors.toSet())), primaryMonitoringResultStatus),
                    new MonitoringResult(physicalParameter, secondaryCnecResults, Map.of(secondaryState, appliedNetworkActionsResultList.stream().flatMap(r -> r.getAppliedNetworkActions().stream()).collect(Collectors.toSet())), secondaryMonitoringResultStatus));
        }
    }

    private void applyOptimalRemedialActions(State state, Network network, RaoResult raoResult) {
        raoResult.getActivatedNetworkActionsDuringState(state).forEach(na -> na.apply(network));
        raoResult.getActivatedRangeActionsDuringState(state).forEach(ra -> ra.apply(network, raoResult.getOptimizedSetPointOnState(state, ra)));
    }

    private void applyOptimalRemedialActionsOnContingencyState(State state, Network network, Crac crac, RaoResult raoResult) {
        if (state.getInstant().isCurative()) {
            Optional<Contingency> contingency = state.getContingency();
            crac.getStates((Contingency) contingency.orElseThrow()).forEach(contingencyState -> applyOptimalRemedialActions(contingencyState, network, raoResult));
        } else {
            applyOptimalRemedialActions(state, network, raoResult);
        }

    }

    private boolean computeLoadFlow(Network network) {
        OpenRaoLoggerProvider.TECHNICAL_LOGS.info("Load-flow computation [start]", new Object[0]);
        LoadFlowResult loadFlowResult = LoadFlow.find(loadFlowProvider).run(network, loadFlowParameters);
        if (loadFlowResult.isFailed()) {
            OpenRaoLoggerProvider.BUSINESS_WARNS.warn("LoadFlow error.", new Object[0]);
        }

        OpenRaoLoggerProvider.TECHNICAL_LOGS.info("Load-flow computation [end]", new Object[0]);
        return loadFlowResult.isFullyConverged();
    }

    private boolean checkMargins(Crac crac, State state, PhysicalParameter parameter, Network network, Unit unit) {
        if (state == null) {
            return true;
        }

        return crac.getCnecs(parameter, state).stream()
                .noneMatch(cnec -> cnec.computeMargin(network, unit) < 0.0);
    }

    private Set<NetworkAction> getNetworkActionsAssociatedToCnec(State state, Crac crac, Cnec cnec, PhysicalParameter physicalParameter) {
        Set<RemedialAction<?>> availableRemedialActions =
                crac.getRemedialActions().stream()
                        .filter(remedialAction ->
                                remedialAction.getUsageRules().stream().filter(OnConstraint.class::isInstance)
                                        .map(OnConstraint.class::cast)
                                        .anyMatch(onConstraint -> onConstraint.getCnec().equals(cnec)))
                        .collect(Collectors.toSet());
        if (availableRemedialActions.isEmpty()) {
            businessLogger.warn("{} Cnec {} in state {} has no associated RA. {} constraint cannot be secured.", physicalParameter, cnec.getId(), state.getId(), physicalParameter);
            return Collections.emptySet();
        } else if (state.isPreventive()) {
            businessLogger.warn("{} Cnec {} is constrained in preventive state, it cannot be secured.", physicalParameter, cnec.getId());
            return Collections.emptySet();
        }
        // Convert remedial actions to network actions
        return availableRemedialActions.stream().filter(remedialAction -> {
            if (remedialAction instanceof NetworkAction) {
                return true;
            } else {
                businessLogger.warn("Remedial action {} of Cnec {} in state {} is ignored : it's not a network action.", remedialAction.getId(), cnec.getId(), state.getId());
                return false;
            }
        }).map(NetworkAction.class::cast).collect(Collectors.toSet());
    }

    private void redispatchNetworkActions(Network network, List<AppliedNetworkActionsResult> appliedNetworkActionsResults, ZonalData<Scalable> scalableZonalData) {
        appliedNetworkActionsResults.forEach(appliedNetworkActionsResult -> appliedNetworkActionsResult.getPowerToBeRedispatched().forEach((key, value) -> {
            businessLogger.info("Redispatching {} MW in {} [start]", value, key);
            List<Scalable> countryScalables = scalableZonalData.getDataPerZone().entrySet().stream().filter(entry -> key.equals((new CountryEICode((String) entry.getKey())).getCountry())).map(Map.Entry::getValue).toList();
            if (countryScalables.size() > 1) {
                throw new OpenRaoException(String.format("> 1 (%s) glskPoints defined for country %s", countryScalables.size(), key.getName()));
            } else {
                (new RedispatchAction(value, appliedNetworkActionsResult.getNetworkElementsToBeExcluded(), (Scalable) countryScalables.get(0))).apply(network);
                businessLogger.info("Redispatching {} MW in {} [end]", value, key);
            }
        }));
    }

    private AppliedNetworkActionsResult applyNetworkActions(Network network, Set<NetworkAction> availableNetworkActions, String cnecId, MonitoringInput monitoringInput) {
        Set<RemedialAction> appliedNetworkActions = new TreeSet(Comparator.comparing(com.powsybl.openrao.data.crac.api.Identifiable::getId));
        AppliedNetworkActionsResult appliedNetworkActionsResult;
        if (monitoringInput.getPhysicalParameter().equals(PhysicalParameter.VOLTAGE)) {
            for (NetworkAction na : availableNetworkActions) {
                na.apply(network);
                appliedNetworkActions.add(na);
            }

            appliedNetworkActionsResult = (new AppliedNetworkActionsResult.AppliedNetworkActionsResultBuilder()).withAppliedNetworkActions(appliedNetworkActions).withNetworkElementsToBeExcluded(new HashSet()).withPowerToBeRedispatched(new EnumMap(Country.class)).build();
        } else {
            boolean networkActionOk = false;
            EnumMap<Country, Double> powerToBeRedispatched = new EnumMap(Country.class);
            Set<String> networkElementsToBeExcluded = new HashSet();

            for (NetworkAction na : availableNetworkActions) {
                EnumMap<Country, Double> tempPowerToBeRedispatched = new EnumMap(powerToBeRedispatched);

                for (Action ea : na.getElementaryActions()) {
                    networkActionOk = checkElementaryActionAndStoreInjection(ea, network, cnecId, na.getId(), networkElementsToBeExcluded, tempPowerToBeRedispatched, monitoringInput.getScalableZonalData());
                    if (!networkActionOk) {
                        break;
                    }
                }

                if (networkActionOk) {
                    na.apply(network);
                    appliedNetworkActions.add(na);
                    powerToBeRedispatched.putAll(tempPowerToBeRedispatched);
                }
            }

            appliedNetworkActionsResult = (new AppliedNetworkActionsResult.AppliedNetworkActionsResultBuilder()).withAppliedNetworkActions(appliedNetworkActions).withNetworkElementsToBeExcluded(networkElementsToBeExcluded).withPowerToBeRedispatched(powerToBeRedispatched).build();
        }

        OpenRaoLoggerProvider.BUSINESS_LOGS.info("Applied the following remedial action(s) in order to reduce constraints on CNEC \"{}\": {}", cnecId, appliedNetworkActions.stream().map(com.powsybl.openrao.data.crac.api.Identifiable::getId).collect(Collectors.joining(", ")));
        return appliedNetworkActionsResult;
    }

    private boolean checkElementaryActionAndStoreInjection(Action ea, Network network, String angleCnecId, String naId, Set<String> networkElementsToBeExcluded, Map<Country, Double> powerToBeRedispatched, ZonalData<Scalable> scalableZonalData) {
        if (!(ea instanceof LoadAction) && !(ea instanceof GeneratorAction)) {
            OpenRaoLoggerProvider.BUSINESS_WARNS.warn("Remedial action {} of AngleCnec {} is ignored : it has an elementary action that's not an injection setpoint.", naId, angleCnecId);
            return false;
        } else {
            Identifiable<?> ne = getInjectionSetpointIdentifiable(ea, network);
            if (ne == null) {
                OpenRaoLoggerProvider.BUSINESS_WARNS.warn("Remedial action {} of AngleCnec {} is ignored : it has no elementary actions.", naId, angleCnecId);
                return false;
            } else {
                Optional<Substation> substation = ((Injection) ne).getTerminal().getVoltageLevel().getSubstation();
                if (substation.isEmpty()) {
                    OpenRaoLoggerProvider.BUSINESS_WARNS.warn("Remedial action {} of AngleCnec {} is ignored : it has an elementary action that doesn't have a substation.", naId, angleCnecId);
                    return false;
                } else {
                    Optional<Country> country = ((Substation) substation.get()).getCountry();
                    if (country.isEmpty()) {
                        OpenRaoLoggerProvider.BUSINESS_WARNS.warn("Remedial action {} of AngleCnec {} is ignored : it has an elementary action that doesn't have a country.", naId, angleCnecId);
                        return false;
                    } else {
                        checkGlsks((Country) country.get(), naId, angleCnecId, scalableZonalData);
                        if (ne.getType().equals(IdentifiableType.GENERATOR)) {
                            powerToBeRedispatched.merge((Country) country.get(), ((Generator) ne).getTargetP() - ((GeneratorAction) ea).getActivePowerValue().getAsDouble(), Double::sum);
                        } else {
                            if (!ne.getType().equals(IdentifiableType.LOAD)) {
                                OpenRaoLoggerProvider.BUSINESS_WARNS.warn("Remedial action {} of AngleCnec {} is ignored : it has an injection setpoint that's neither a generator nor a load.", naId, angleCnecId);
                                return false;
                            }

                            powerToBeRedispatched.merge((Country) country.get(), -((Load) ne).getP0() + ((LoadAction) ea).getActivePowerValue().getAsDouble(), Double::sum);
                        }

                        networkElementsToBeExcluded.add(ne.getId());
                        return true;
                    }
                }
            }
        }
    }

    private void checkGlsks(Country country, String naId, String angleCnecId, ZonalData<Scalable> scalableZonalData) {
        Set<Country> glskCountries = new TreeSet(Comparator.comparing(Country::getName));
        if (Objects.isNull(scalableZonalData)) {
            String error = "ScalableZonalData undefined (no GLSK given)";
            OpenRaoLoggerProvider.BUSINESS_LOGS.error(error, new Object[0]);
            throw new OpenRaoException(error);
        } else {
            for (String zone : scalableZonalData.getDataPerZone().keySet()) {
                glskCountries.add((new CountryEICode(zone)).getCountry());
            }

            if (!glskCountries.contains(country)) {
                throw new OpenRaoException(String.format("INFEASIBLE Angle Monitoring : Glsks were not defined for country %s. Remedial action %s of AngleCnec %s is ignored.", country.getName(), naId, angleCnecId));
            }
        }
    }

    private Identifiable<?> getInjectionSetpointIdentifiable(Action ea, Network network) {
        if (ea instanceof GeneratorAction generatorAction) {
            return network.getIdentifiable(generatorAction.getGeneratorId());
        } else if (ea instanceof LoadAction loadAction) {
            return network.getIdentifiable(loadAction.getLoadId());
        } else if (ea instanceof DanglingLineAction danglingLineAction) {
            return network.getIdentifiable(danglingLineAction.getDanglingLineId());
        } else if (ea instanceof ShuntCompensatorPositionAction shuntCompensatorPositionAction) {
            return network.getIdentifiable(shuntCompensatorPositionAction.getShuntCompensatorId());
        } else {
            return null;
        }
    }

    private Pair<MonitoringResult, MonitoringResult> makeFailedMonitoringResultForStateWithNaNCnecRsults(MonitoringInput primaryMonitoringInput, MonitoringInput secondaryMonitoringInput, PhysicalParameter physicalParameter, State primaryState, State secondaryState, String failureReason) {
        Set<CnecResult> frEsCnecResults = new HashSet();
        Set<CnecResult> ptEsCnecResults = new HashSet();
        CnecValue cnecValue = physicalParameter.equals(PhysicalParameter.ANGLE) ? new AngleCnecValue(Double.NaN) : new VoltageCnecValue(Double.NaN, Double.NaN);
        primaryMonitoringInput.getCrac().getCnecs(primaryState).forEach(cnec -> frEsCnecResults.add(new CnecResult(cnec, parameterToUnitMap.get(physicalParameter), cnecValue, Double.NaN, Cnec.SecurityStatus.FAILURE)));
        MonitoringResult ptEsMonitoringResult = null;
        if (secondaryState != null) {
            // TODO: check the condition here
            secondaryMonitoringInput.getCrac().getCnecs(secondaryState).forEach(cnec -> ptEsCnecResults.add(new CnecResult(cnec, parameterToUnitMap.get(physicalParameter), cnecValue, Double.NaN, Cnec.SecurityStatus.FAILURE)));
            ptEsMonitoringResult = new MonitoringResult(physicalParameter, ptEsCnecResults, Map.of(secondaryState, Collections.emptySet()), Cnec.SecurityStatus.FAILURE);

        }
        businessLogger.warn(failureReason);
        MonitoringResult frEsMonitoringResult = new MonitoringResult(physicalParameter, frEsCnecResults, Map.of(primaryState, Collections.emptySet()), Cnec.SecurityStatus.FAILURE);
        return Pair.of(frEsMonitoringResult, ptEsMonitoringResult);
    }

}
