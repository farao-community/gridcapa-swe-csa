package com.farao_community.farao.swe_csa.app.dichotomy;

import com.farao_community.farao.dichotomy.api.exceptions.GlskLimitationException;
import com.farao_community.farao.dichotomy.api.exceptions.ShiftingException;
import com.farao_community.farao.dichotomy.api.results.ReasonInvalid;
import com.farao_community.farao.gridcapa_swe_commons.shift.CountryBalanceComputation;
import com.farao_community.farao.rao_runner.api.exceptions.RaoRunnerException;
import com.farao_community.farao.swe_csa.api.JsonApiConverter;
import com.farao_community.farao.swe_csa.api.exception.CsaInvalidDataException;
import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
import com.farao_community.farao.swe_csa.api.resource.CsaResponse;
import com.farao_community.farao.swe_csa.api.resource.Status;
import com.farao_community.farao.swe_csa.api.results.CounterTradeRangeActionResult;
import com.farao_community.farao.swe_csa.api.results.CounterTradingResult;
import com.farao_community.farao.swe_csa.app.*;
import com.farao_community.farao.swe_csa.app.rao_result.RaoResultWithCounterTradeRangeActions;
import com.farao_community.farao.swe_csa.app.s3.S3ArtifactsAdapter;
import com.farao_community.farao.swe_csa.app.shift.ShiftDispatcher;
import com.farao_community.farao.swe_csa.app.shift.SweCsaZonalData;
import com.powsybl.glsk.commons.CountryEICode;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.Identifiable;
import com.powsybl.openrao.data.crac.api.rangeaction.CounterTradeRangeAction;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import com.powsybl.openrao.monitoring.Monitoring;
import com.powsybl.openrao.monitoring.MonitoringInput;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import kotlin.Pair;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DichotomyRunner {

    public static final String RESPONSE_BRIDGE_NAME = "csa-response";
    @Value("${dichotomy-parameters.index.precision}")
    private double indexPrecision;
    @Value("${dichotomy-parameters.index.max-iterations-by-border}")
    private double maxDichotomiesByBorder;
    private final SweCsaRaoValidator sweCsaRaoValidator;
    private final FileImporter fileImporter;
    private final FileExporter fileExporter;
    private final InterruptionService interruptionService;
    private final StreamBridge streamBridge;
    private final S3ArtifactsAdapter s3ArtifactsAdapter;
    private final JsonApiConverter jsonApiConverter = new JsonApiConverter();
    private final Logger businessLogger;

    private static final String CT_RA_PTES = "CT_RA_PTES";
    private static final String CT_RA_FRES = "CT_RA_FRES";
    private static final String CT_RA_ESPT = "CT_RA_ESPT";
    private static final String CT_RA_ESFR = "CT_RA_ESFR";
    private static final String ES_FR = "ES_FR";
    private static final String ES_PT = "ES_PT";

    public DichotomyRunner(SweCsaRaoValidator sweCsaRaoValidator, FileImporter fileImporter, FileExporter fileExporter, InterruptionService interruptionService, StreamBridge streamBridge, S3ArtifactsAdapter s3ArtifactsAdapter, Logger businessLogger) {
        this.sweCsaRaoValidator = sweCsaRaoValidator;
        this.fileImporter = fileImporter;
        this.fileExporter = fileExporter;
        this.interruptionService = interruptionService;
        this.streamBridge = streamBridge;
        this.s3ArtifactsAdapter = s3ArtifactsAdapter;
        this.businessLogger = businessLogger;
    }

    public Pair<RaoResult, Status> runDichotomy(CsaRequest csaRequest) throws GlskLimitationException, ShiftingException {
        RaoParameters raoParameters = RaoParameters.load();
        String raoParametersUrl = fileImporter.uploadRaoParameters(Instant.parse(csaRequest.getBusinessTimestamp()));
        Network network = fileImporter.importNetwork(csaRequest.getGridModelUri());
        Crac crac = fileImporter.importCrac(csaRequest.getCracFileUri(), network);
        updateCracWithCounterTrageRangeActions(crac);

        String initialVariant = network.getVariantManager().getWorkingVariantId();

        Map<String, Double> initialNetPositions = CountryBalanceComputation.computeSweCountriesBalances(network, raoParameters.getLoadFlowAndSensitivityParameters().getSensitivityWithLoadFlowParameters().getLoadFlowParameters())
            .entrySet().stream()
            .collect(Collectors.toMap(entry -> new CountryEICode(entry.getKey()).getCountry().getName(), Map.Entry::getValue));

        businessLogger.info("Initial net positions: PT: {}, ES: {}, FR: {}", initialNetPositions.get(Country.PT.getName()), initialNetPositions.get(Country.ES.getName()), initialNetPositions.get(Country.FR.getName()));
        Map<String, Double> initialExchanges = CountryBalanceComputation.computeSweBordersExchanges(network);

        double expEsFr0 = initialExchanges.get(ES_FR);
        double expEsPt0 = initialExchanges.get(ES_PT);
        double expFrEs0 = -expEsFr0;
        double expPtEs0 = -expEsPt0;
        businessLogger.info("Initial exchanges: PT->ES: {}, FR->ES: {}", expPtEs0, expFrEs0);

        CounterTradingValues minCounterTradingValues = new CounterTradingValues(0, 0);
        CounterTradeRangeAction ctRaFrEs;
        CounterTradeRangeAction ctRaEsFr;
        CounterTradeRangeAction ctRaPtEs;
        CounterTradeRangeAction ctRaEsPt;
        try {
            ctRaFrEs = getCounterTradeRangeActionByCountries(crac, Country.FR, Country.ES);
            ctRaEsFr = getCounterTradeRangeActionByCountries(crac, Country.ES, Country.FR);
            ctRaPtEs = getCounterTradeRangeActionByCountries(crac, Country.PT, Country.ES);
            ctRaEsPt = getCounterTradeRangeActionByCountries(crac, Country.ES, Country.PT);
        } catch (CsaInvalidDataException e) {
            businessLogger.warn(e.getMessage());
            businessLogger.warn("No counter trading will be done, only input network will be checked by rao");
            RaoResult raoResult = sweCsaRaoValidator.validateNetwork(network, crac, raoParameters, csaRequest, raoParametersUrl, minCounterTradingValues).getRaoResult();
            fileExporter.saveRaoResultInArtifact(csaRequest.getResultsUri(), raoResult, crac);
            return raoResult.isSecure() ? new Pair<>(raoResult, Status.FINISHED_SECURE) : new Pair<>(raoResult, Status.FINISHED_UNSECURE);
        }
        // best case no counter trading , no scaling
        businessLogger.info("Starting Counter trading algorithm by validating input network without scaling");
        String noCtVariantName = "no-ct-PT-ES-0_FR-ES-0";
        setWorkingVariant(network, initialVariant, noCtVariantName);
        DichotomyStepResult noCtStepResult = sweCsaRaoValidator.validateNetwork(network, crac, raoParameters, csaRequest, raoParametersUrl, minCounterTradingValues);
        resetToInitialVariant(network, initialVariant, noCtVariantName);

        logBorderOverload(noCtStepResult);

        if (noCtStepResult.isValid()) {
            businessLogger.info("Input network is secure no need for counter trading");
            fileExporter.saveRaoResultInArtifact(csaRequest.getResultsUri(), noCtStepResult.getRaoResult(), crac);
            return new Pair<>(noCtStepResult.getRaoResult(), Status.FINISHED_SECURE);
        } else {
            if (interruptionService.getTasksToInterrupt().remove(csaRequest.getId())) {
                businessLogger.info("Interruption asked for task {}, before any secure situation is found", csaRequest.getId());
                return new Pair<>(null, Status.FINISHED_UNSECURE);
            }

            // initial network not secure, try worst case maximum counter trading
            double ctFrEsMax = getMaxCounterTrading(ctRaFrEs, ctRaEsFr, expFrEs0, "FR-ES");
            double ctPtEsMax = getMaxCounterTrading(ctRaPtEs, ctRaEsPt, expPtEs0, "PT-ES");

            double ctPtEsUpperBound = noCtStepResult.isPtEsCnecsSecure() ? 0 : ctPtEsMax;
            double ctFrEsUpperBound = noCtStepResult.isFrEsCnecsSecure() ? 0 : ctFrEsMax;
            CounterTradingValues maxCounterTradingValues = new CounterTradingValues(ctPtEsUpperBound, ctFrEsUpperBound);
            businessLogger.info("Testing Counter trading worst case by scaling to maximum: CT PT-ES: '{}', and CT FR-ES: '{}'", ctPtEsUpperBound, ctFrEsUpperBound);

            String maxCtVariantName = getNewVariantName(maxCounterTradingValues);
            setWorkingVariant(network, initialVariant, maxCtVariantName);

            SweCsaNetworkShifter networkShifter = new SweCsaNetworkShifter(SweCsaZonalData.getZonalData(network), initialExchanges.get(ES_FR), initialExchanges.get(ES_PT), new ShiftDispatcher(initialNetPositions));
            networkShifter.applyCounterTrading(maxCounterTradingValues, network);
            DichotomyStepResult maxCtStepResult = sweCsaRaoValidator.validateNetwork(network, crac, raoParameters, csaRequest, raoParametersUrl, maxCounterTradingValues);
            resetToInitialVariant(network, initialVariant, maxCtVariantName);

            logBorderOverload(maxCtStepResult);
            if (!maxCtStepResult.isValid()) {
                businessLogger.error("Maximum CT value cannot secure this case");
                fileExporter.saveRaoResultInArtifact(csaRequest.getResultsUri(), maxCtStepResult.getRaoResult(), crac);
                return new Pair<>(maxCtStepResult.getRaoResult(), Status.FINISHED_UNSECURE);
            } else {
                businessLogger.info("Best case in unsecure, worst case is secure, trying to find optimum in between using dichotomy");
                Index index = new Index(0, 0, indexPrecision, maxDichotomiesByBorder);
                index.addPtEsDichotomyStepResult(0, noCtStepResult);
                index.addPtEsDichotomyStepResult(ctPtEsUpperBound, maxCtStepResult);
                index.addFrEsDichotomyStepResult(0, noCtStepResult);
                index.addFrEsDichotomyStepResult(ctFrEsUpperBound, maxCtStepResult);
                index.setBestValidDichotomyStepResult(maxCtStepResult);
                return processDichotomy(csaRequest, raoParameters, raoParametersUrl, network, crac, initialVariant, networkShifter, index);
            }
        }
    }

    private Pair<RaoResult, Status> processDichotomy(CsaRequest csaRequest, RaoParameters raoParameters, String raoParametersUrl, Network network, Crac crac, String initialVariant, SweCsaNetworkShifter networkShifter, Index index) {
        boolean interrupted = false;
        while (index.exitConditionIsNotMetForPtEs() || index.exitConditionIsNotMetForFrEs()) {
            if (interruptionService.getTasksToInterrupt().remove(csaRequest.getId())) {
                businessLogger.info("Interruption asked for task {}, best secure situation at current time will be returned", csaRequest.getId());
                interrupted = true;
                break;
            }
            CounterTradingValues counterTradingValues = index.nextValues();
            DichotomyStepResult ctStepResult;
            String newVariantName = getNewVariantName(counterTradingValues);
            try {
                businessLogger.info("Next CT values are '{}' for PT-ES and '{}' for FR-ES", counterTradingValues.getPtEsCt(), counterTradingValues.getFrEsCt());
                setWorkingVariant(network, initialVariant, newVariantName);
                networkShifter.applyCounterTrading(counterTradingValues, network);
                ctStepResult = sweCsaRaoValidator.validateNetwork(network, crac, raoParameters, csaRequest, raoParametersUrl, counterTradingValues);
            } catch (GlskLimitationException e) {
                businessLogger.warn("GLSK limits have been reached with CT of '{}' for PT-ES and '{}' for FR-ES", counterTradingValues.getPtEsCt(), counterTradingValues.getFrEsCt());
                ctStepResult = DichotomyStepResult.fromFailure(ReasonInvalid.GLSK_LIMITATION, e.getMessage(), counterTradingValues);
            } catch (ShiftingException | RaoRunnerException e) {
                businessLogger.warn("Validation failed with CT of '{}' for PT-ES and '{}' for FR-ES", counterTradingValues.getPtEsCt(), counterTradingValues.getFrEsCt());
                ctStepResult = DichotomyStepResult.fromFailure(ReasonInvalid.GLSK_LIMITATION, e.getMessage(), counterTradingValues);
            } finally {
                resetToInitialVariant(network, initialVariant, newVariantName);
            }
            logBorderOverload(ctStepResult);
            boolean ptEsCtSecure = index.addPtEsDichotomyStepResult(counterTradingValues.getPtEsCt(), ctStepResult);
            boolean frEsCtSecure = index.addFrEsDichotomyStepResult(counterTradingValues.getFrEsCt(), ctStepResult);
            if (ptEsCtSecure && frEsCtSecure) {
                index.setBestValidDichotomyStepResult(ctStepResult);
                // enhance rao result with monitoring result + CT values and send notification
                Pair<RaoResult, Status> intermediateSuccessfulStep = getRaoResultStatusPair(csaRequest, raoParameters, network, crac, index, false, true);
                streamBridge.send(RESPONSE_BRIDGE_NAME, jsonApiConverter.toJsonMessage(new CsaResponse(csaRequest.getId(), intermediateSuccessfulStep.getSecond().toString(), s3ArtifactsAdapter.generatePreSignedUrl(csaRequest.getResultsUri())), CsaResponse.class));

            }
        }
        businessLogger.info("Dichotomy stop criterion reached, CT PT-ES: {}, CT FR-ES: {}", Math.round(index.getBestValidDichotomyStepResult().getCounterTradingValues().getPtEsCt()), Math.round(index.getBestValidDichotomyStepResult().getCounterTradingValues().getFrEsCt()));
        return getRaoResultStatusPair(csaRequest, raoParameters, network, crac, index, interrupted, false);
    }

    private Pair<RaoResult, Status> getRaoResultStatusPair(CsaRequest csaRequest, RaoParameters raoParameters, Network network, Crac crac, Index index, boolean interrupted, boolean stillRunningAndSecure) {
        Status status;
        if (index.getBestValidDichotomyStepResult() == null) {
            status = Status.FINISHED_UNSECURE;
            return new Pair<>(null, status);
        } else {
            if (interrupted) {
                status = Status.INTERRUPTED_SECURE;
            } else if (stillRunningAndSecure) {
                status = Status.STILL_RUNNING_SECURE;
            } else {
                status = Status.FINISHED_SECURE;
            }

            RaoResult raoResult = index.getBestValidDichotomyStepResult().getRaoResult();
            // TODO MBR : check if we need to rerun angle monitoring here
            raoResult = updateRaoResultWithVoltageMonitoring(network, crac, raoResult, raoParameters);
            RaoResultWithCounterTradeRangeActions raoResultWithRangeAction = updateRaoResultWithCounterTradingRAs(network, crac, index, raoResult);
            fileExporter.saveRaoResultInArtifact(csaRequest.getResultsUri(), raoResultWithRangeAction, crac);
            return new Pair<>(raoResultWithRangeAction, status);
        }
    }

    double getMaxCounterTrading(CounterTradeRangeAction ctraTowardsES, CounterTradeRangeAction ctraFromES, double initialExchangeTowardsES, String borderName) {
        double ctMax = initialExchangeTowardsES >= 0 ? Math.min(Math.min(-ctraTowardsES.getMinAdmissibleSetpoint(initialExchangeTowardsES), ctraFromES.getMaxAdmissibleSetpoint(-initialExchangeTowardsES)), initialExchangeTowardsES)
            : Math.min(Math.min(ctraTowardsES.getMaxAdmissibleSetpoint(initialExchangeTowardsES), -ctraFromES.getMinAdmissibleSetpoint(-initialExchangeTowardsES)), -initialExchangeTowardsES);

        if (ctMax != Math.abs(initialExchangeTowardsES)) {
            businessLogger.warn("Maximum counter trading {} '{}' is different from initial exchange {} '{}' ", borderName, ctMax, borderName, Math.abs(initialExchangeTowardsES));
        }

        return ctMax;
    }

    private static RaoResult updateRaoResultWithVoltageMonitoring(Network network, Crac crac, RaoResult raoResult, RaoParameters raoParameters) {
        MonitoringInput voltageMonitoringInput = MonitoringInput.buildWithVoltage(network, crac, raoResult).build();
        return Monitoring.runVoltageAndUpdateRaoResult(raoParameters.getLoadFlowAndSensitivityParameters().getLoadFlowProvider(), raoParameters.getLoadFlowAndSensitivityParameters().getSensitivityWithLoadFlowParameters().getLoadFlowParameters(), Runtime.getRuntime().availableProcessors(), voltageMonitoringInput);
    }

    private RaoResultWithCounterTradeRangeActions updateRaoResultWithCounterTradingRAs(Network network, Crac crac, Index index, RaoResult raoResult) {
        Map<CounterTradeRangeAction, CounterTradeRangeActionResult> counterTradingResultsMap = new HashMap<>();
        List<String> frEsFlowCnecs = SweCsaRaoValidator.getBorderFlowCnecs(crac, network, Country.FR).stream().map(Identifiable::getId).toList();
        List<String> ptEsFlowCnecs = SweCsaRaoValidator.getBorderFlowCnecs(crac, network, Country.PT).stream().map(Identifiable::getId).toList();
        counterTradingResultsMap.put(crac.getCounterTradeRangeAction(CT_RA_PTES), new CounterTradeRangeActionResult(CT_RA_PTES, Math.abs(index.getPtEsLowestSecureStep().getLeft()), ptEsFlowCnecs));
        counterTradingResultsMap.put(crac.getCounterTradeRangeAction(CT_RA_ESPT), new CounterTradeRangeActionResult(CT_RA_ESPT, Math.abs(index.getPtEsLowestSecureStep().getLeft()), ptEsFlowCnecs));
        counterTradingResultsMap.put(crac.getCounterTradeRangeAction(CT_RA_FRES), new CounterTradeRangeActionResult(CT_RA_FRES, Math.abs(index.getFrEsLowestSecureStep().getLeft()), frEsFlowCnecs));
        counterTradingResultsMap.put(crac.getCounterTradeRangeAction(CT_RA_ESFR), new CounterTradeRangeActionResult(CT_RA_ESFR, Math.abs(index.getFrEsLowestSecureStep().getLeft()), frEsFlowCnecs));
        return new RaoResultWithCounterTradeRangeActions(raoResult, new CounterTradingResult(counterTradingResultsMap));
    }

    private void setWorkingVariant(Network network, String initialVariant, String newVariantName) {
        network.getVariantManager().cloneVariant(initialVariant, newVariantName);
        network.getVariantManager().setWorkingVariant(newVariantName);
    }

    private void resetToInitialVariant(Network network, String initialVariant, String newVariantName) {
        network.getVariantManager().setWorkingVariant(initialVariant);
        network.getVariantManager().removeVariant(newVariantName);
    }

    private CounterTradeRangeAction getCounterTradeRangeActionByCountries(Crac crac, Country exportingCountry, Country importingCountry) {
        for (CounterTradeRangeAction counterTradeRangeAction : crac.getCounterTradeRangeActions()) {
            if (counterTradeRangeAction.getExportingCountry() == exportingCountry && counterTradeRangeAction.getImportingCountry() == importingCountry) {
                return counterTradeRangeAction;
            }
        }
        throw new CsaInvalidDataException(MDC.get("gridcapaTaskId"), String.format("Crac should contain 4 counter trading remedial actions for csa swe process, Two CT RAs by border, and couldn't find CT RA for '%s' as exporting country and '%s' as importing country", exportingCountry.getName(), importingCountry.getName()));
    }

    private void logBorderOverload(DichotomyStepResult ctStepResult) {
        if (ctStepResult.isFrEsCnecsSecure()) {
            businessLogger.info("There is no overload on FR-ES border");
        } else {
            businessLogger.info("There is overloads on FR-ES border, network is not secure");
            if (ctStepResult.getFrEsMostLimitingCnec() != null) {
                businessLogger.info("On the FR-ES border, the most limiting CNEC is {}", ctStepResult.getFrEsMostLimitingCnec().getLeft());
            }
        }

        if (ctStepResult.isPtEsCnecsSecure()) {
            businessLogger.info("There is no overload on PT-ES border");
        } else {
            businessLogger.info("There is overloads on PT-ES border, network is not secure");
            if (ctStepResult.getPtEsMostLimitingCnec() != null) {
                businessLogger.info("On the PT-ES border, the most limiting CNEC is {}", ctStepResult.getPtEsMostLimitingCnec().getLeft());
            }
        }
    }

    private String getNewVariantName(CounterTradingValues counterTradingValues) {
        return String.format("network-ScaledBy-%s", counterTradingValues.print());
    }

    void updateCracWithCounterTrageRangeActions(Crac crac) {
        crac.newCounterTradeRangeAction()
            .withId(CT_RA_PTES)
            .withOperator("REN")
            .newRange().withMin(-50000.0)
            .withMax(50000.0).add()
            .withInitialSetpoint(0.0)
            .withExportingCountry(Country.PT)
            .withImportingCountry(Country.ES)
            .add();
        crac.newCounterTradeRangeAction()
            .withId(CT_RA_ESPT)
            .withOperator("REE")
            .newRange().withMin(-50000.0)
            .withMax(50000.0).add()
            .withInitialSetpoint(0.0)
            .withExportingCountry(Country.ES)
            .withImportingCountry(Country.PT)
            .add();
        crac.newCounterTradeRangeAction()
            .withId(CT_RA_ESFR)
            .withOperator("REE")
            .newRange().withMin(-50000.0)
            .withMax(50000.0).add()
            .withInitialSetpoint(0.0)
            .withExportingCountry(Country.ES)
            .withImportingCountry(Country.FR)
            .add();
        crac.newCounterTradeRangeAction()
            .withId(CT_RA_FRES)
            .withOperator("RTE")
            .newRange().withMin(-50000.0)
            .withMax(50000.0).add()
            .withInitialSetpoint(0.0)
            .withExportingCountry(Country.FR)
            .withImportingCountry(Country.ES)
            .add();
    }

    void setIndexPrecision(double indexPrecision) {
        this.indexPrecision = indexPrecision;
    }

    void setMaxDichotomiesByBorder(double maxDichotomiesByBorder) {
        this.maxDichotomiesByBorder = maxDichotomiesByBorder;
    }
}
