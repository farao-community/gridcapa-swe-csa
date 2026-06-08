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
import com.powsybl.openrao.monitoring.results.MonitoringResult;
import com.powsybl.openrao.monitoring.results.RaoResultWithAngleMonitoring;
import com.powsybl.openrao.monitoring.results.RaoResultWithVoltageMonitoring;
import org.slf4j.Logger;
import org.slf4j.MDC;

import java.util.*;
import java.util.stream.Collectors;

public class SweCsaMonitoring {

    private record MonitoringContext(Map<Border, RaoResult> raoResultPerBorder,
                                     Map<Border, Boolean> securityFlagPerBorder,
                                     Set<BorderMonitoringInput> borderMonitoringInputs,
                                     Map<Border, MonitoringResult> flowMonitoringPerBorder,
                                     Map<Border, MonitoringResult> angleMonitoringPerBorder,
                                     Map<Border, MonitoringResult> voltageMonitoringPerBorder
    ) { }

    private final String loadFlowProvider;
    private final LoadFlowParameters loadFlowParameters;
    private final Logger businessLogger;
    private static final String MONITORING_NETWORK_VARIANT_ID = "monitoringVariantId";

    public SweCsaMonitoring(String loadFlowProvider, LoadFlowParameters loadFlowParameters, Logger businessLogger) {
        this.loadFlowProvider = loadFlowProvider;
        this.loadFlowParameters = loadFlowParameters;
        this.businessLogger = businessLogger;
    }

    public ParallelDichotomiesResult validateNetworkForSweBorders(Network network, ParallelDichotomiesResult parallelDichotomiesResult, Crac frEsCrac, Crac ptEsCrac, ZonalData<Scalable> zonalData) {
        MonitoringContext monitoringContext = initializeMonitoringContext(parallelDichotomiesResult, frEsCrac, ptEsCrac);
        try {
            String initialVariant = network.getVariantManager().getWorkingVariantId();
            createMonitoringNetworkVariant(network, initialVariant);
            monitoringContext = runFlowMonitoring(network, zonalData, monitoringContext);
            monitoringContext = runAngleMonitoringIfNeeded(network, zonalData, monitoringContext);
            monitoringContext = runVoltageMonitoringIfNeeded(network, zonalData, monitoringContext);
            resetToInitialNetworkVariant(network, initialVariant);
            return buildFinalResult(parallelDichotomiesResult, monitoringContext);
        } catch (Exception e) {
            throw new CsaInternalException(MDC.get("gridcapaTaskId"), "Monitoring failed", e);
        }
    }

    private void createMonitoringNetworkVariant(Network network, String initialVariant) {
        network.getVariantManager().cloneVariant(initialVariant, MONITORING_NETWORK_VARIANT_ID);
        network.getVariantManager().setWorkingVariant(MONITORING_NETWORK_VARIANT_ID);
    }

    private void resetToInitialNetworkVariant(Network network, String initialVariant) {
        network.getVariantManager().setWorkingVariant(initialVariant);
        network.getVariantManager().removeVariant(MONITORING_NETWORK_VARIANT_ID);
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
        return new MonitoringContext(raoResultsPerBorder, secureMap, monitoringInputsPerBorder, new EnumMap<>(Border.class), new EnumMap<>(Border.class), new EnumMap<>(Border.class));
    }

    private MonitoringContext runFlowMonitoring(Network network, ZonalData<Scalable> zonalData, MonitoringContext monitoringContext) {
        MultiBorderMonitoringInput input = buildMultiBorderMonitoringInput(network, monitoringContext.borderMonitoringInputs(), PhysicalParameter.FLOW, zonalData);
        MultiBorderMonitoring flowMonitoring = new MultiBorderMonitoring(input, Runtime.getRuntime().availableProcessors(), businessLogger);
        MultiBorderMonitoringResult flowMonitoringResult = flowMonitoring.run();
        Map<Border, Boolean> securityPerBorder = computeSecurityPerBorder(input, flowMonitoringResult);
        Map<Border, MonitoringResult> flowMonitoringResultsPerBorder = flowMonitoringResult.getResultsForAllBorders();
        return new MonitoringContext(monitoringContext.raoResultPerBorder(), securityPerBorder, monitoringContext.borderMonitoringInputs(), flowMonitoringResultsPerBorder, monitoringContext.angleMonitoringPerBorder(), monitoringContext.voltageMonitoringPerBorder());
    }

    private MonitoringContext runAngleMonitoringIfNeeded(Network network, ZonalData<Scalable> zonalData, MonitoringContext monitoringContext) {
        boolean anyFlowSecure = monitoringContext.securityFlagPerBorder().values().stream().anyMatch(Boolean::booleanValue);
        boolean anyAngleCnecs = monitoringContext.borderMonitoringInputs().stream().anyMatch(i -> !i.crac().getAngleCnecs().isEmpty());
        if (!anyFlowSecure || !anyAngleCnecs) {
            return monitoringContext;
        }
        MultiBorderMonitoringInput angleInput = buildMultiBorderMonitoringInput(network, monitoringContext.borderMonitoringInputs(), PhysicalParameter.ANGLE, zonalData);
        MultiBorderMonitoring monitoring = new MultiBorderMonitoring(angleInput, Runtime.getRuntime().availableProcessors(), businessLogger);
        MultiBorderMonitoringResult angleMonitoringResults = monitoring.run();
        Map<Border, MonitoringResult> angleMonitoringResultsPerBorder = angleMonitoringResults.getResultsForAllBorders();
        Map<Border, RaoResult> updatedRaoResultPerBorder = updateRaoResultsWithAngleMonitoringForTwoBorders(angleInput, angleMonitoringResultsPerBorder);
        Map<Border, Boolean> securityPerBorder = updateSecurityPerBorder(monitoringContext.securityFlagPerBorder(), updatedRaoResultPerBorder, PhysicalParameter.ANGLE);
        logMonitoringOutcome(PhysicalParameter.ANGLE.toString(), securityPerBorder);
        Set<BorderMonitoringInput> updatedBorderMonitoringInput = rebuildMonitoringInputs(monitoringContext.borderMonitoringInputs(), updatedRaoResultPerBorder);
        return new MonitoringContext(updatedRaoResultPerBorder, securityPerBorder, updatedBorderMonitoringInput, monitoringContext.flowMonitoringPerBorder(), angleMonitoringResultsPerBorder, monitoringContext.voltageMonitoringPerBorder());
    }

    private MonitoringContext runVoltageMonitoringIfNeeded(Network network, ZonalData<Scalable> zonalData, MonitoringContext monitoringContext) {
        boolean anySecure = monitoringContext.securityFlagPerBorder().values().stream().anyMatch(Boolean::booleanValue);
        boolean anyVoltageCnecs = monitoringContext.borderMonitoringInputs().stream().anyMatch(i -> !i.crac().getVoltageCnecs().isEmpty());
        if (!anySecure || !anyVoltageCnecs) {
            return monitoringContext;
        }
        MultiBorderMonitoringInput voltageInput = buildMultiBorderMonitoringInput(network, monitoringContext.borderMonitoringInputs(), PhysicalParameter.VOLTAGE, zonalData);
        MultiBorderMonitoring monitoring = new MultiBorderMonitoring(voltageInput, Runtime.getRuntime().availableProcessors(), businessLogger);
        MultiBorderMonitoringResult voltageMonitoringResults = monitoring.run();
        Map<Border, MonitoringResult> voltageMonitoringResultsPerBorder = voltageMonitoringResults.getResultsForAllBorders();
        Map<Border, RaoResult> updatedRaoResultPerBorder = updateRaoResultsWithVoltageMonitoringForTwoBorders(voltageInput, voltageMonitoringResultsPerBorder);
        Map<Border, Boolean> securityPerBorder = updateSecurityPerBorder(monitoringContext.securityFlagPerBorder(), updatedRaoResultPerBorder, PhysicalParameter.VOLTAGE);
        logMonitoringOutcome(PhysicalParameter.VOLTAGE.toString(), securityPerBorder);
        Set<BorderMonitoringInput> updatedBorderMonitoringInput = rebuildMonitoringInputs(monitoringContext.borderMonitoringInputs(), updatedRaoResultPerBorder);
        return new MonitoringContext(updatedRaoResultPerBorder, securityPerBorder, updatedBorderMonitoringInput, monitoringContext.flowMonitoringPerBorder(), monitoringContext.angleMonitoringPerBorder(), voltageMonitoringResultsPerBorder);
    }

    private MultiBorderMonitoringInput buildMultiBorderMonitoringInput(Network network, Set<BorderMonitoringInput> borderMonitoringInputs, PhysicalParameter parameter, ZonalData<Scalable> zonalData) {
        return new MultiBorderMonitoringInput(network, borderMonitoringInputs, parameter, zonalData, loadFlowProvider, loadFlowParameters);
    }

    private Map<Border, Boolean> computeSecurityPerBorder(MultiBorderMonitoringInput monitoringInput, MultiBorderMonitoringResult monitoringResult) {
        return monitoringInput.getBorders().stream().collect(Collectors.toMap(b -> b, b -> monitoringResult.getMonitoringResultForBorder(b).getStatus() == Cnec.SecurityStatus.SECURE));
    }

    private Map<Border, Boolean> updateSecurityPerBorder(Map<Border, Boolean> securityFlagPerBorder, Map<Border, RaoResult> updatedRaoResultPerBorder, PhysicalParameter parameter) {

        Map<Border, Boolean> newMap = new EnumMap<>(Border.class);
        securityFlagPerBorder.forEach((border, wasSecure) -> {
            boolean nowSecure = wasSecure &&
                    Optional.ofNullable(updatedRaoResultPerBorder.get(border))
                            .map(r -> r.isSecure(parameter))
                            .orElse(false);
            newMap.put(border, nowSecure);
        });
        return newMap;
    }

    private void logMonitoringOutcome(String label, Map<Border, Boolean> securityFlagPerBorder) {
        if (securityFlagPerBorder.values().stream().allMatch(Boolean::booleanValue)) {
            businessLogger.info("{} monitoring secure for both borders, Final result will contain {} monitoring results", label, label);
        } else {
            businessLogger.info("{} monitoring unsecure for at least one border", label);
        }
    }

    private Set<BorderMonitoringInput> rebuildMonitoringInputs(Set<BorderMonitoringInput> borderMonitoringInput, Map<Border, RaoResult> updatedRaoResultPerBorder) {
        return borderMonitoringInput.stream()
                .map(i -> new MultiBorderMonitoringInput.BorderMonitoringInput(
                        i.border(),
                        i.crac(),
                        updatedRaoResultPerBorder.get(i.border())
                ))
                .collect(Collectors.toSet());
    }

    public static Map<Border, RaoResult> updateRaoResultsWithAngleMonitoringForTwoBorders(MultiBorderMonitoringInput parallelInput, Map<Border, MonitoringResult> angleMonitoringResults) {
        return parallelInput.getBorders().stream()
                .collect(Collectors.toMap(border -> border,
                        border -> new RaoResultWithAngleMonitoring(
                                parallelInput.getRaoResultForBorder(border),
                                angleMonitoringResults.get(border)
                        )
                ));
    }

    public static Map<Border, RaoResult> updateRaoResultsWithVoltageMonitoringForTwoBorders(MultiBorderMonitoringInput parallelInput, Map<Border, MonitoringResult> voltageMonitoringResults) {
        return parallelInput.getBorders().stream()
                .collect(Collectors.toMap(border -> border,
                        border -> new RaoResultWithVoltageMonitoring(
                                parallelInput.getRaoResultForBorder(border),
                                voltageMonitoringResults.get(border)
                        )
                ));
    }

    private ParallelDichotomiesResult buildFinalResult(ParallelDichotomiesResult initialResult, MonitoringContext monitoringContext) {
        CounterTradingValues ct = initialResult.getCounterTradingValues();
        DichotomyStepResult frEs = DichotomyStepResult.fromNetworkValidationWithMonitoringResult(
                monitoringContext.raoResultPerBorder().get(Border.FR_ES),
                monitoringContext.securityFlagPerBorder().get(Border.FR_ES),
                initialResult.getFrEsResult().getRaoSuccessResponse(),
                ct,
                monitoringContext.flowMonitoringPerBorder().get(Border.FR_ES),
                monitoringContext.angleMonitoringPerBorder().get(Border.FR_ES),
                monitoringContext.voltageMonitoringPerBorder().get(Border.FR_ES)
        );
        DichotomyStepResult ptEs = DichotomyStepResult.fromNetworkValidationWithMonitoringResult(
                monitoringContext.raoResultPerBorder().get(Border.PT_ES),
                monitoringContext.securityFlagPerBorder().get(Border.PT_ES),
                initialResult.getPtEsResult().getRaoSuccessResponse(),
                ct,
                monitoringContext.flowMonitoringPerBorder().get(Border.PT_ES),
                monitoringContext.angleMonitoringPerBorder().get(Border.PT_ES),
                monitoringContext.voltageMonitoringPerBorder().get(Border.PT_ES)
        );
        return new ParallelDichotomiesResult(ptEs, frEs, ct);
    }
}

