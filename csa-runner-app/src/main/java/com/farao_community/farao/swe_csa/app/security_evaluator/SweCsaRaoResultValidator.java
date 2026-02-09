package com.farao_community.farao.swe_csa.app.security_evaluator;

import com.farao_community.farao.swe_csa.api.exception.CsaInternalException;
import com.farao_community.farao.swe_csa.app.dichotomy.CounterTradingValues;
import com.farao_community.farao.swe_csa.app.dichotomy.DichotomyStepResult;
import com.farao_community.farao.swe_csa.app.dichotomy.ParallelDichotomiesResult;
import com.farao_community.farao.swe_csa.app.security_evaluator.MultiBorderMonitoringInput.CracRaoResultPair;
import com.powsybl.glsk.commons.ZonalData;
import com.powsybl.iidm.modification.scalable.Scalable;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.openrao.commons.PhysicalParameter;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.cnec.Cnec;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import com.powsybl.openrao.monitoring.results.MonitoringResult;
import org.slf4j.Logger;
import org.slf4j.MDC;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.farao_community.farao.swe_csa.app.security_evaluator.MultiBorderMonitoring.updateRaoResultsWithAngleMonitoringForTwoBorders;
import static com.farao_community.farao.swe_csa.app.security_evaluator.MultiBorderMonitoring.updateRaoResultsWithVoltageMonitoringForTwoBorders;

public class SweCsaRaoResultValidator {

    private final String loadFlowProvider;
    private final LoadFlowParameters loadFlowParameters;
    private final Logger businessLogger;

    public SweCsaRaoResultValidator(String loadFlowProvider, LoadFlowParameters loadFlowParameters, Logger businessLogger) {
        this.loadFlowProvider = loadFlowProvider;
        this.loadFlowParameters = loadFlowParameters;
        this.businessLogger = businessLogger;
    }

    /**
     * Check if the network is secure and update raoResults with Angle/Voltage monitoring
     * when considering Cnecs and RAs from 2 borders
     *
     * @param network                                  the network
     * @param parallelDichotomiesResult                results of dichotomy computation after validating and monitoring for each border separately
     * @param scalableZonalDataFilteredForSweCountries used for the redispatching in the case of Angle monitoring
     * @return ParallelDichotomiesResult updated parallelDichotomiesResult after validating for both borders at once and
     * updating angle/voltage monitoring results
     */
    public ParallelDichotomiesResult validateNetworkForTwoBorders(Network network, ParallelDichotomiesResult parallelDichotomiesResult, Crac frEsCrac, Crac ptEsCrac, ZonalData<Scalable> scalableZonalDataFilteredForSweCountries) {

        Objects.requireNonNull(parallelDichotomiesResult.getFrEsResult().getRaoResult(), "RaoResult of the border FR_ES is null");
        Objects.requireNonNull(parallelDichotomiesResult.getPtEsResult().getRaoResult(), "RaoResult of the border PT_ES is null");

        Map<Border, CracRaoResultPair> monitoringInputMap = Map.of(
                Border.FR_ES, new CracRaoResultPair(frEsCrac, parallelDichotomiesResult.getFrEsResult().getRaoResult()),
                Border.PT_ES, new CracRaoResultPair(ptEsCrac, parallelDichotomiesResult.getPtEsResult().getRaoResult())
        );

        MultiBorderMonitoringInput parallelInput =
                new MultiBorderMonitoringInput(network, monitoringInputMap, PhysicalParameter.FLOW, null, loadFlowProvider, loadFlowParameters);

        Map<Border, RaoResult> raoResultMap = Map.of(
                Border.FR_ES, parallelDichotomiesResult.getFrEsResult().getRaoResult(),
                Border.PT_ES, parallelDichotomiesResult.getPtEsResult().getRaoResult()
        );

        try {
            // Check if all the flowCnecs in two borders are secure after applying all RAs from two borders
            MultiBorderMonitoring checker = new MultiBorderMonitoring(parallelInput, Runtime.getRuntime().availableProcessors(), businessLogger);
            Map<Border, MonitoringResult> flowSecurityCheck = checker.run();
            Map<Border, Boolean> isSecureMap = flowSecurityCheck.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getStatus() == Cnec.SecurityStatus.SECURE));

            // If angleCnecs exist, Angle monitoring
            boolean anyFlowSecure = isSecureMap.values().stream().anyMatch(Boolean::booleanValue);
            boolean anyAngleCnecs = parallelInput.getBorders().stream().anyMatch(b -> !parallelInput.getCracForBorder(b).getAngleCnecs().isEmpty());

            if (anyFlowSecure && anyAngleCnecs) {
                MultiBorderMonitoringInput angleInput = new MultiBorderMonitoringInput(network, parallelInput.asMap(), PhysicalParameter.ANGLE, parallelInput.getZonalScalableData(), loadFlowProvider, loadFlowParameters);
                raoResultMap = updateRaoResultsWithAngleMonitoringForTwoBorders(angleInput, businessLogger);
                Map<Border, RaoResult> updatedAngleResults = raoResultMap;
                isSecureMap.replaceAll((border, oldSecure) -> oldSecure && Optional.ofNullable(updatedAngleResults.get(border)).map(r -> r.isSecure(PhysicalParameter.FLOW, PhysicalParameter.ANGLE)).orElse(false));
                if (isSecureMap.values().stream().allMatch(Boolean::booleanValue)) {
                    businessLogger.info("Angle monitoring secure for both borders, Final result will contain Angle monitoring results");
                } else {
                    businessLogger.info("Angle monitoring unsecure for at least one border");
                }
                monitoringInputMap = Map.of(
                        Border.FR_ES, new CracRaoResultPair(frEsCrac, raoResultMap.get(Border.FR_ES)),
                        Border.PT_ES, new CracRaoResultPair(ptEsCrac,  raoResultMap.get(Border.PT_ES)));

            }

            // If voltageCnecs exist, Voltage monitoring
            boolean anyVoltageCnecs = parallelInput.getBorders().stream().anyMatch(b -> !parallelInput.getCracForBorder(b).getVoltageCnecs().isEmpty());
            if (isSecureMap.values().stream().anyMatch(Boolean::booleanValue) && anyVoltageCnecs) {
                MultiBorderMonitoringInput voltageInput = new MultiBorderMonitoringInput(network, monitoringInputMap, PhysicalParameter.VOLTAGE, null, loadFlowProvider, loadFlowParameters);
                raoResultMap = updateRaoResultsWithVoltageMonitoringForTwoBorders(voltageInput, businessLogger);
                Map<Border, RaoResult> updatedVoltageResults = raoResultMap;
                isSecureMap.replaceAll((border, oldSecure) -> oldSecure && Optional.ofNullable(updatedVoltageResults.get(border)).map(r -> r.isSecure(PhysicalParameter.FLOW, PhysicalParameter.VOLTAGE)).orElse(false));
                if (isSecureMap.values().stream().allMatch(Boolean::booleanValue)) {
                    businessLogger.info("Voltage monitoring secure for both borders, Final result will contain Voltage monitoring results");
                } else {
                    businessLogger.info("Voltage monitoring unsecure for at least one border");
                }
            }
            CounterTradingValues counterTradingValues = parallelDichotomiesResult.getCounterTradingValues();
            DichotomyStepResult frEsDichotomyResult = DichotomyStepResult.fromNetworkValidationResult(raoResultMap.get(Border.FR_ES), isSecureMap.get(Border.FR_ES), parallelDichotomiesResult.getFrEsResult().getRaoSuccessResponse(), counterTradingValues);
            DichotomyStepResult ptEsDichotomyResult = DichotomyStepResult.fromNetworkValidationResult(raoResultMap.get(Border.PT_ES), isSecureMap.get(Border.PT_ES), parallelDichotomiesResult.getPtEsResult().getRaoSuccessResponse(), counterTradingValues);
            // Return the updated parallelDichotomiesResult
            return new ParallelDichotomiesResult(ptEsDichotomyResult, frEsDichotomyResult, counterTradingValues);
        } catch (Exception e) {
            throw new CsaInternalException(MDC.get("gridcapaTaskId"), "RAO run failed", e);
        }
    }

}
