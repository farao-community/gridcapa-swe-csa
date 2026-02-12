package com.farao_community.farao.swe_csa.app.multi_border_monitoring;

import com.farao_community.farao.swe_csa.api.exception.CsaInternalException;
import com.farao_community.farao.swe_csa.app.dichotomy.CounterTradingValues;
import com.farao_community.farao.swe_csa.app.dichotomy.DichotomyStepResult;
import com.farao_community.farao.swe_csa.app.dichotomy.ParallelDichotomiesResult;
import com.farao_community.farao.swe_csa.app.multi_border_monitoring.MultiBorderMonitoringInput.BorderMonitoringInput;
import com.powsybl.glsk.commons.ZonalData;
import com.powsybl.iidm.modification.scalable.Scalable;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openrao.commons.PhysicalParameter;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.cnec.Cnec;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import com.powsybl.openrao.monitoring.results.RaoResultWithAngleMonitoring;
import com.powsybl.openrao.monitoring.results.RaoResultWithVoltageMonitoring;
import org.slf4j.Logger;
import org.slf4j.MDC;

import java.util.*;
import java.util.stream.Collectors;

public class SweCsaMonitoring {

    private record MonitoringContext(Map<Border, RaoResult> raoResultPerBorder,
                                     Map<Border, Boolean> securityFlagPerBorder,
                                   Set<BorderMonitoringInput> borderMonitoringInputs) {}

    private final String loadFlowProvider;
    private final LoadFlowParameters loadFlowParameters;
    private final Logger businessLogger;

    public SweCsaMonitoring(String loadFlowProvider, LoadFlowParameters loadFlowParameters, Logger businessLogger) {
        this.loadFlowProvider = loadFlowProvider;
        this.loadFlowParameters = loadFlowParameters;
        this.businessLogger = businessLogger;
    }

    public ParallelDichotomiesResult validateNetworkForTwoBorders(Network network, ParallelDichotomiesResult parallelDichotomiesResult,
                                                                  Crac frEsCrac, Crac ptEsCrac, ZonalData<Scalable> zonalData) {
        MonitoringContext monitoringContext = initializeMonitoringContext(parallelDichotomiesResult, frEsCrac, ptEsCrac);
        try {
            monitoringContext = runFlowMonitoring(network, zonalData, monitoringContext);
            monitoringContext = runAngleMonitoringIfNeeded(network, zonalData, monitoringContext);
            monitoringContext = runVoltageMonitoringIfNeeded(network, zonalData, monitoringContext);
            return buildFinalResult(parallelDichotomiesResult, monitoringContext);
        } catch (Exception e) {
            throw new CsaInternalException(MDC.get("gridcapaTaskId"), "Monitoring failed", e);
        }
    }

    private MonitoringContext initializeMonitoringContext(ParallelDichotomiesResult parallelResult, Crac frEsCrac, Crac ptEsCrac) {
        RaoResult frEsRao = Objects.requireNonNull(parallelResult.getFrEsResult().getRaoResult(), "RaoResult of the border FR_ES is null");
        RaoResult ptEsRao = Objects.requireNonNull(parallelResult.getPtEsResult().getRaoResult(), "RaoResult of the border PT_ES is null");
        Map<Border, RaoResult> raoResultsPerBorder = Map.of(Border.FR_ES, frEsRao, Border.PT_ES, ptEsRao);
        Set<BorderMonitoringInput> monitoringInputsPerBorder = Set.of(new BorderMonitoringInput(Border.FR_ES, frEsCrac, frEsRao),
                new BorderMonitoringInput(Border.PT_ES, ptEsCrac, ptEsRao));
        Map<Border, Boolean> secureMap = new EnumMap<>(Border.class);
        secureMap.put(Border.FR_ES, true);
        secureMap.put(Border.PT_ES, true);
        return new MonitoringContext(raoResultsPerBorder, secureMap, monitoringInputsPerBorder);
    }

    private MonitoringContext runFlowMonitoring(Network network, ZonalData<Scalable> zonalData, MonitoringContext monitoringContext) {
        MultiBorderMonitoringInput input = buildMultiBorderMonitoringInput(network, monitoringContext.borderMonitoringInputs(), PhysicalParameter.FLOW, zonalData);
        MultiBorderMonitoring flowMonitoring = new MultiBorderMonitoring(input, Runtime.getRuntime().availableProcessors(), businessLogger);
        MultiBorderMonitoringResult flowMonitoringResult = flowMonitoring.run();
        Map<Border, Boolean> securityPerBorder = computeSecurityPerBorder(input, flowMonitoringResult);
        return new MonitoringContext(monitoringContext.raoResultPerBorder(), securityPerBorder, monitoringContext.borderMonitoringInputs());
    }

    private MonitoringContext runAngleMonitoringIfNeeded(Network network, ZonalData<Scalable> zonalData, MonitoringContext monitoringContext) {
        boolean anyFlowSecure = monitoringContext.securityFlagPerBorder().values().stream().anyMatch(Boolean::booleanValue);
        boolean anyAngleCnecs = monitoringContext.borderMonitoringInputs().stream().anyMatch(i -> !i.crac().getAngleCnecs().isEmpty());
        if (!anyFlowSecure || !anyAngleCnecs) {
            return monitoringContext;
        }
        MultiBorderMonitoringInput angleInput = buildMultiBorderMonitoringInput(network, monitoringContext.borderMonitoringInputs(), PhysicalParameter.ANGLE, zonalData);
        Map<Border, RaoResult> updatedRaoResultPerBorder = updateRaoResultsWithAngleMonitoringForTwoBorders(angleInput, businessLogger);
        Map<Border, Boolean> securityPerBorder = updateSecurityPerBorder(monitoringContext.securityFlagPerBorder(), updatedRaoResultPerBorder, PhysicalParameter.ANGLE);
        logMonitoringOutcome(PhysicalParameter.ANGLE.toString(), securityPerBorder);
        Set<BorderMonitoringInput> updatedBorderMonitoringInput = rebuildMonitoringInputs(monitoringContext.borderMonitoringInputs(), updatedRaoResultPerBorder);
        return new MonitoringContext(updatedRaoResultPerBorder, securityPerBorder, updatedBorderMonitoringInput);
    }


    private MonitoringContext runVoltageMonitoringIfNeeded(Network network, ZonalData<Scalable> zonalData, MonitoringContext monitoringContext) {
        boolean anySecure = monitoringContext.securityFlagPerBorder().values().stream().anyMatch(Boolean::booleanValue);
        boolean anyVoltageCnecs = monitoringContext.borderMonitoringInputs().stream().anyMatch(i -> !i.crac().getVoltageCnecs().isEmpty());
        if (!anySecure || !anyVoltageCnecs) {
            return monitoringContext;
        }
        MultiBorderMonitoringInput voltageInput = buildMultiBorderMonitoringInput(network, monitoringContext.borderMonitoringInputs(), PhysicalParameter.VOLTAGE, zonalData);
        Map<Border, RaoResult> updatedRaoResultPerBorder = updateRaoResultsWithVoltageMonitoringForTwoBorders(voltageInput, businessLogger);
        Map<Border, Boolean> securityPerBorder = updateSecurityPerBorder(monitoringContext.securityFlagPerBorder(), updatedRaoResultPerBorder, PhysicalParameter.VOLTAGE);
        logMonitoringOutcome(PhysicalParameter.VOLTAGE.toString(), securityPerBorder);
        Set<BorderMonitoringInput> updatedBorderMonitoringInput = rebuildMonitoringInputs(monitoringContext.borderMonitoringInputs(), updatedRaoResultPerBorder);
        return new MonitoringContext(updatedRaoResultPerBorder, securityPerBorder, updatedBorderMonitoringInput);
    }


    private MultiBorderMonitoringInput buildMultiBorderMonitoringInput(Network network, Set<BorderMonitoringInput> borderMonitoringInputs,
                                                                       PhysicalParameter parameter, ZonalData<Scalable> zonalData) {
        return new MultiBorderMonitoringInput(network, borderMonitoringInputs, parameter, zonalData, loadFlowProvider, loadFlowParameters);
    }

    private Map<Border, Boolean> computeSecurityPerBorder(MultiBorderMonitoringInput monitoringInput,
                                                          MultiBorderMonitoringResult monitoringResult) {
        return monitoringInput.getBorders().stream()
                .collect(Collectors.toMap(b -> b, b -> monitoringResult.getMonitoringResultForBorder(b).getStatus() == Cnec.SecurityStatus.SECURE));
    }

    private Map<Border, Boolean> updateSecurityPerBorder(Map<Border, Boolean> securityFlagPerBorder,
                                                         Map<Border, RaoResult> updatedRaoResultPerBorder, PhysicalParameter parameter) {
        Map<Border, Boolean> newMap = new EnumMap<>(Border.class);
        securityFlagPerBorder.forEach((border, wasSecure) -> {
            boolean nowSecure = wasSecure && Optional.ofNullable(updatedRaoResultPerBorder.get(border))
                    .map(r -> r.isSecure(PhysicalParameter.FLOW, parameter))
                    .orElse(false);
            newMap.put(border, nowSecure);
        });
        return newMap;
    }

    private void logMonitoringOutcome(String label, Map<Border, Boolean> securityFlagPerBorder) {
        if (securityFlagPerBorder.values().stream().allMatch(Boolean::booleanValue)) {
            businessLogger.info(label + " monitoring secure for both borders, Final result will contain " + label + " monitoring results");
        } else {
            businessLogger.info(label + " monitoring unsecure for at least one border");
        }
    }

    private Set<BorderMonitoringInput> rebuildMonitoringInputs(Set<BorderMonitoringInput> borderMonitoringInput, Map<Border, RaoResult> updatedRaoResultPerBorder) {
        return borderMonitoringInput.stream()
                .map(i -> new MultiBorderMonitoringInput.BorderMonitoringInput(i.border(), i.crac(), updatedRaoResultPerBorder.get(i.border())))
                .collect(Collectors.toSet());
    }

    public static Map<Border, RaoResult> updateRaoResultsWithAngleMonitoringForTwoBorders(MultiBorderMonitoringInput parallelInput, Logger businessLogger) {
        MultiBorderMonitoring monitoring = new MultiBorderMonitoring(parallelInput, Runtime.getRuntime().availableProcessors(), businessLogger);
        MultiBorderMonitoringResult angleMonitoringResults = monitoring.run();
        return parallelInput.getBorders().stream().collect(Collectors.toMap(border -> border,
                border -> new RaoResultWithAngleMonitoring(parallelInput.getRaoResultForBorder(border), angleMonitoringResults.getMonitoringResultForBorder(border))));
    }

    public static Map<Border, RaoResult> updateRaoResultsWithVoltageMonitoringForTwoBorders(MultiBorderMonitoringInput parallelInput, Logger businessLogger) {
        MultiBorderMonitoring monitoring = new MultiBorderMonitoring(parallelInput, Runtime.getRuntime().availableProcessors(), businessLogger);
        MultiBorderMonitoringResult voltageMonitoringResults = monitoring.run();
        return parallelInput.getBorders().stream().collect(Collectors.toMap(border -> border,
                border -> new RaoResultWithVoltageMonitoring(parallelInput.getRaoResultForBorder(border), voltageMonitoringResults.getMonitoringResultForBorder(border))));
    }

    private ParallelDichotomiesResult buildFinalResult(ParallelDichotomiesResult initialResult, MonitoringContext monitoringContext) {
        CounterTradingValues ct = initialResult.getCounterTradingValues();
        DichotomyStepResult frEs = DichotomyStepResult.fromNetworkValidationResult(
                monitoringContext.raoResultPerBorder().get(Border.FR_ES),
                monitoringContext.securityFlagPerBorder().get(Border.FR_ES),
                initialResult.getFrEsResult().getRaoSuccessResponse(),
                ct);
        DichotomyStepResult ptEs = DichotomyStepResult.fromNetworkValidationResult(
                monitoringContext.raoResultPerBorder().get(Border.PT_ES),
                monitoringContext.securityFlagPerBorder().get(Border.PT_ES),
                initialResult.getPtEsResult().getRaoSuccessResponse(),
                ct);
        return new ParallelDichotomiesResult(ptEs, frEs, ct);
    }

}
