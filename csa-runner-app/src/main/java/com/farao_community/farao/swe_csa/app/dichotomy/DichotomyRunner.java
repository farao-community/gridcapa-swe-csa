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
import com.powsybl.glsk.commons.CountryEICode;
import com.powsybl.glsk.commons.ZonalData;
import com.powsybl.iidm.modification.scalable.Scalable;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.crac.api.Identifiable;
import com.powsybl.openrao.data.crac.api.rangeaction.CounterTradeRangeAction;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import com.powsybl.openrao.monitoring.Monitoring;
import com.powsybl.openrao.monitoring.MonitoringInput;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import com.powsybl.openrao.raoapi.parameters.extensions.LoadFlowAndSensitivityParameters;
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
import java.util.function.Supplier;
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

    public ParallelDichotomyResult runDichotomy(CsaRequest csaRequest, String ptEsRaoResultDestinationPath, String frEsRaoResultDestinationPath) throws GlskLimitationException, ShiftingException {
        RaoParameters raoParameters = RaoParameters.load();
        Instant instant = Instant.parse(csaRequest.getBusinessTimestamp());
        String raoParametersUrl = fileImporter.uploadRaoParameters(instant);
        Network network = fileImporter.importNetwork(csaRequest.getId(), csaRequest.getGridModelUri());
        Crac cracPtEs = fileImporter.importCrac(csaRequest.getId(), csaRequest.getPtEsCracFileUri(), network);
        Crac cracFrEs = fileImporter.importCrac(csaRequest.getId(), csaRequest.getFrEsCracFileUri(), network);

        ZonalData<Scalable> scalableZonalData = fileImporter.getZonalData(csaRequest.getId(), instant, csaRequest.getGlskUri(), network, false);
        ZonalData<Scalable> scalableZonalDataFilteredForSweCountries = fileImporter.getZonalData(csaRequest.getId(), instant, csaRequest.getGlskUri(), network, true);

        // TODO remove me, temporary workaround, in real data crac should already contain CT-RAs
        updateCracWithPtEsCounterTradeRangeActions(cracPtEs);
        updateCracWithFrEsCounterTradeRangeActions(cracFrEs);

        String initialVariant = network.getVariantManager().getWorkingVariantId();

        Map<String, Double> initialNetPositions = CountryBalanceComputation.computeSweCountriesBalances(network, LoadFlowAndSensitivityParameters.getSensitivityWithLoadFlowParameters(raoParameters).getLoadFlowParameters())
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

        // TODO: check this: probably presence of counter trading RA in one border and absence in the other one is acceptable when no CT is intended for that border
        try {
            ctRaFrEs = getCounterTradeRangeActionByCountries(cracFrEs, Country.FR, Country.ES);
            ctRaEsFr = getCounterTradeRangeActionByCountries(cracFrEs, Country.ES, Country.FR);
            ctRaPtEs = getCounterTradeRangeActionByCountries(cracPtEs, Country.PT, Country.ES);
            ctRaEsPt = getCounterTradeRangeActionByCountries(cracPtEs, Country.ES, Country.PT);
        } catch (CsaInvalidDataException e) {
            businessLogger.warn(e.getMessage());
            businessLogger.warn("No counter trading will be done, only input network will be checked by rao");
            Supplier<DichotomyStepResult> raoResultPtEsSupplier = () -> sweCsaRaoValidator.validateNetworkForPortugueseBorder(network, cracPtEs, csaRequest.getPtEsCracFileUri(), scalableZonalDataFilteredForSweCountries, raoParameters, csaRequest, raoParametersUrl, minCounterTradingValues);
            Supplier<DichotomyStepResult> raoResultFrEsSupplier = () -> sweCsaRaoValidator.validateNetworkForFrenchBorder(network, cracFrEs, csaRequest.getFrEsCracFileUri(), scalableZonalDataFilteredForSweCountries, raoParameters, csaRequest, raoParametersUrl, minCounterTradingValues);
            ParallelDichotomies.runParallel(raoResultPtEsSupplier, raoResultFrEsSupplier);
            RaoResult raoResultPtEs = raoResultPtEsSupplier.get().getRaoResult();
            RaoResult raoResultFrEs = raoResultFrEsSupplier.get().getRaoResult();

            fileExporter.saveRaoResultInArtifact(ptEsRaoResultDestinationPath, raoResultPtEs, cracPtEs);
            fileExporter.saveRaoResultInArtifact(frEsRaoResultDestinationPath, raoResultFrEs, cracFrEs);

            Status ptEsStatus = raoResultPtEs.isSecure() ? Status.FINISHED_SECURE : Status.FINISHED_UNSECURE;
            Status frEsStatus = raoResultFrEs.isSecure() ? Status.FINISHED_SECURE : Status.FINISHED_UNSECURE;

            return new ParallelDichotomyResult(new Pair<>(raoResultPtEs, ptEsStatus), new Pair<>(raoResultFrEs, frEsStatus), minCounterTradingValues);
        }
        // best case no counter trading , no scaling
        businessLogger.info("Starting Counter trading algorithm by validating input network without scaling");
        String noCtVariantName = "no-ct-PT-ES-0_FR-ES-0";
        setWorkingVariant(network, initialVariant, noCtVariantName);

        Supplier<DichotomyStepResult> noCtStepResultPtEsSupplier = () -> sweCsaRaoValidator.validateNetworkForPortugueseBorder(network, cracPtEs, csaRequest.getPtEsCracFileUri(), scalableZonalDataFilteredForSweCountries, raoParameters, csaRequest, raoParametersUrl, minCounterTradingValues);
        Supplier<DichotomyStepResult> noCtStepResultFrEsSupplier = () -> sweCsaRaoValidator.validateNetworkForFrenchBorder(network, cracFrEs, csaRequest.getFrEsCracFileUri(), scalableZonalDataFilteredForSweCountries, raoParameters, csaRequest, raoParametersUrl, minCounterTradingValues);
        ParallelDichotomies.runParallel(noCtStepResultPtEsSupplier, noCtStepResultFrEsSupplier);
        logBorderOverload(noCtStepResultPtEsSupplier.get(), "PT-ES");
        logBorderOverload(noCtStepResultFrEsSupplier.get(), "FR-ES");

        resetToInitialVariant(network, initialVariant, noCtVariantName);

        if (noCtStepResultPtEsSupplier.get().isSecure() && noCtStepResultFrEsSupplier.get().isSecure()) {
            businessLogger.info("Input network is secure no need for counter trading");
            RaoResult noCtRaoResultPtEs = noCtStepResultPtEsSupplier.get().getRaoResult();
            RaoResult noCtRaoResultFrEs = noCtStepResultFrEsSupplier.get().getRaoResult();
            fileExporter.saveRaoResultInArtifact(ptEsRaoResultDestinationPath, noCtRaoResultPtEs, cracPtEs);
            fileExporter.saveRaoResultInArtifact(frEsRaoResultDestinationPath, noCtRaoResultFrEs, cracFrEs);
            return new ParallelDichotomyResult(new Pair<>(noCtRaoResultPtEs, Status.FINISHED_SECURE), new Pair<>(noCtRaoResultFrEs, Status.FINISHED_SECURE), minCounterTradingValues);
        } else {
            if (interruptionService.getTasksToInterrupt().remove(csaRequest.getId())) {
                businessLogger.info("Interruption asked for task {}, before any secure situation is found", csaRequest.getId());
                return new ParallelDichotomyResult(new Pair<>(null, Status.INTERRUPTED_UNSECURE), new Pair<>(null, Status.INTERRUPTED_UNSECURE), null);
            }

            // initial network not secure, try worst case maximum counter trading
            double ctPtEsMax = getMaxCounterTrading(ctRaPtEs, ctRaEsPt, expPtEs0, "PT-ES");
            double ctFrEsMax = getMaxCounterTrading(ctRaFrEs, ctRaEsFr, expFrEs0, "FR-ES");

            double ctPtEsUpperBound = noCtStepResultPtEsSupplier.get().isSecure() ? 0 : ctPtEsMax;
            double ctFrEsUpperBound = noCtStepResultFrEsSupplier.get().isSecure() ? 0 : ctFrEsMax;
            CounterTradingValues maxCounterTradingValues = new CounterTradingValues(ctPtEsUpperBound, ctFrEsUpperBound);
            businessLogger.info("Testing Counter trading worst case by scaling to maximum: CT PT-ES: '{}', and CT FR-ES: '{}'", ctPtEsUpperBound, ctFrEsUpperBound);

            String maxCtVariantName = getNewVariantName(maxCounterTradingValues);
            setWorkingVariant(network, initialVariant, maxCtVariantName);

            SweCsaNetworkShifter networkShifter = new SweCsaNetworkShifter(scalableZonalData, initialExchanges.get(ES_FR), initialExchanges.get(ES_PT), new ShiftDispatcher(initialNetPositions));
            networkShifter.applyCounterTrading(maxCounterTradingValues, network);

            Supplier<DichotomyStepResult> maxCtStepResultPtEsSupplier = () -> sweCsaRaoValidator.validateNetworkForPortugueseBorder(network, cracPtEs, csaRequest.getPtEsCracFileUri(), scalableZonalDataFilteredForSweCountries, raoParameters, csaRequest, raoParametersUrl, maxCounterTradingValues);
            Supplier<DichotomyStepResult> maxCtStepResultFrEsSupplier = () -> sweCsaRaoValidator.validateNetworkForFrenchBorder(network, cracFrEs, csaRequest.getFrEsCracFileUri(), scalableZonalDataFilteredForSweCountries, raoParameters, csaRequest, raoParametersUrl, maxCounterTradingValues);
            ParallelDichotomies.runParallel(maxCtStepResultPtEsSupplier, maxCtStepResultFrEsSupplier);

            RaoResult maxCtRaoResultPtEs = maxCtStepResultPtEsSupplier.get().getRaoResult();
            RaoResult maxCtRaoResultFrEs = maxCtStepResultFrEsSupplier.get().getRaoResult();

            logBorderOverload(maxCtStepResultPtEsSupplier.get(), "PT-ES");
            logBorderOverload(maxCtStepResultPtEsSupplier.get(), "FR-ES");

            resetToInitialVariant(network, initialVariant, maxCtVariantName);

            if (!maxCtStepResultPtEsSupplier.get().isSecure() || !maxCtStepResultFrEsSupplier.get().isSecure()) {
                businessLogger.error("Maximum CT value cannot secure this case");
                fileExporter.saveRaoResultInArtifact(ptEsRaoResultDestinationPath, maxCtRaoResultPtEs, cracPtEs);
                fileExporter.saveRaoResultInArtifact(frEsRaoResultDestinationPath, maxCtRaoResultFrEs, cracFrEs);
                return new ParallelDichotomyResult(new Pair<>(maxCtRaoResultPtEs, Status.FINISHED_UNSECURE), new Pair<>(maxCtRaoResultFrEs, Status.FINISHED_UNSECURE), null);
            } else {
                businessLogger.info("Best case in unsecure, worst case is secure, trying to find optimum in between using dichotomy");
                Index index = new Index(0, 0, indexPrecision, maxDichotomiesByBorder);
                index.addPtEsDichotomyStepResult(0, noCtStepResultPtEsSupplier.get());
                index.addPtEsDichotomyStepResult(ctPtEsUpperBound, maxCtStepResultPtEsSupplier.get());
                index.addFrEsDichotomyStepResult(0, noCtStepResultFrEsSupplier.get());
                index.addFrEsDichotomyStepResult(ctFrEsUpperBound, maxCtStepResultFrEsSupplier.get());
                index.setBestValidDichotomyStepResult(new ParallelDichotomyResult(new Pair<>(maxCtRaoResultPtEs, Status.FINISHED_SECURE), new Pair<>(maxCtRaoResultFrEs, Status.FINISHED_SECURE), maxCounterTradingValues));
                return processDichotomy(csaRequest, ptEsRaoResultDestinationPath, frEsRaoResultDestinationPath, raoParameters, raoParametersUrl, network, cracPtEs, cracFrEs, scalableZonalData, initialVariant, networkShifter, index);
            }
        }
    }

    private ParallelDichotomyResult processDichotomy(CsaRequest csaRequest, String ptEsRaoResultDestinationPath, String frEsRaoResultDestinationPath, RaoParameters raoParameters, String raoParametersUrl, Network network, Crac cracPtEs, Crac cracFrEs, ZonalData<Scalable> scalableZonalDataFilteredForSweCountries, String initialVariant, SweCsaNetworkShifter networkShifter, Index index) {
        boolean interrupted = false;
        while (index.exitConditionIsNotMetForPtEs() || index.exitConditionIsNotMetForFrEs()) {
            if (interruptionService.getTasksToInterrupt().remove(csaRequest.getId())) {
                businessLogger.info("Interruption asked for task {}, best secure situation at current time will be returned", csaRequest.getId());
                interrupted = true;
                break;
            }
            CounterTradingValues counterTradingValues = index.nextValues();
            DichotomyStepResult ptEsCtStepResult;
            DichotomyStepResult frEsCtStepResult;

            String newVariantName = getNewVariantName(counterTradingValues);
            try {
                businessLogger.info("Next CT values are '{}' for PT-ES and '{}' for FR-ES", counterTradingValues.getPtEsCt(), counterTradingValues.getFrEsCt());
                setWorkingVariant(network, initialVariant, newVariantName);
                networkShifter.applyCounterTrading(counterTradingValues, network);

                Supplier<DichotomyStepResult> ptEsStepResultSupplier = () -> sweCsaRaoValidator.validateNetworkForPortugueseBorder(network, cracPtEs, csaRequest.getPtEsCracFileUri(), scalableZonalDataFilteredForSweCountries, raoParameters, csaRequest, raoParametersUrl, counterTradingValues);
                Supplier<DichotomyStepResult> frEsStepResultSupplier = () -> sweCsaRaoValidator.validateNetworkForFrenchBorder(network, cracFrEs, csaRequest.getFrEsCracFileUri(), scalableZonalDataFilteredForSweCountries, raoParameters, csaRequest, raoParametersUrl, counterTradingValues);
                ParallelDichotomies.runParallel(ptEsStepResultSupplier, frEsStepResultSupplier);

                logBorderOverload(ptEsStepResultSupplier.get(), "PT-ES");
                logBorderOverload(frEsStepResultSupplier.get(), "FR-ES");

                ptEsCtStepResult = ptEsStepResultSupplier.get();
                frEsCtStepResult = frEsStepResultSupplier.get();

            } catch (GlskLimitationException e) {
                businessLogger.warn("GLSK limits have been reached with CT of '{}' for PT-ES and '{}' for FR-ES", counterTradingValues.getPtEsCt(), counterTradingValues.getFrEsCt());
                ptEsCtStepResult = DichotomyStepResult.fromFailure(ReasonInvalid.GLSK_LIMITATION, e.getMessage(), counterTradingValues);
                frEsCtStepResult = ptEsCtStepResult;
            } catch (ShiftingException | RaoRunnerException e) {
                businessLogger.warn("Validation failed with CT of '{}' for PT-ES and '{}' for FR-ES", counterTradingValues.getPtEsCt(), counterTradingValues.getFrEsCt());
                ptEsCtStepResult = DichotomyStepResult.fromFailure(ReasonInvalid.GLSK_LIMITATION, e.getMessage(), counterTradingValues);
                frEsCtStepResult = ptEsCtStepResult;
            } finally {
                resetToInitialVariant(network, initialVariant, newVariantName);
            }
            boolean ptEsCtSecure = index.addPtEsDichotomyStepResult(counterTradingValues.getPtEsCt(), ptEsCtStepResult);
            boolean frEsCtSecure = index.addFrEsDichotomyStepResult(counterTradingValues.getFrEsCt(), frEsCtStepResult);
            if (ptEsCtSecure && frEsCtSecure) {
                index.setBestValidDichotomyStepResult(new ParallelDichotomyResult(new Pair<>(ptEsCtStepResult.getRaoResult(), Status.FINISHED_SECURE), new Pair<>(frEsCtStepResult.getRaoResult(), Status.FINISHED_SECURE), counterTradingValues));
                // enhance rao result with monitoring result + CT values and send notification

                RaoResultWithCounterTradeRangeActions ptRaoResultWithRangeActions = getPtRaoResultWithCounterTradeRangeActions(ptEsRaoResultDestinationPath, raoParameters, network, cracPtEs, ptEsCtStepResult.getRaoResult(), index);
                Pair<RaoResult, Status> ptEsIntermediateSuccessfulStep = getRaoResultStatusPair(ptRaoResultWithRangeActions, index, false, true);

                RaoResultWithCounterTradeRangeActions frRaoResultWithRangeActions = getFrRaoResultWithCounterTradeRangeActions(frEsRaoResultDestinationPath, raoParameters, network, cracFrEs, frEsCtStepResult.getRaoResult(), index);
                Pair<RaoResult, Status> frEsIntermediateSuccessfulStep = getRaoResultStatusPair(frRaoResultWithRangeActions, index, false, true);
                CsaResponse csaResponse = new CsaResponse(csaRequest.getId(), ptEsIntermediateSuccessfulStep.getSecond().toString(), s3ArtifactsAdapter.generatePreSignedUrl(ptEsRaoResultDestinationPath), frEsIntermediateSuccessfulStep.getSecond().toString(), s3ArtifactsAdapter.generatePreSignedUrl(frEsRaoResultDestinationPath));
                streamBridge.send(RESPONSE_BRIDGE_NAME, jsonApiConverter.toJsonMessage(csaResponse, CsaResponse.class));
            }
        }
        businessLogger.info("Dichotomy stop criterion reached, CT PT-ES: {}, CT FR-ES: {}", Math.round(index.getBestValidDichotomyStepResult().getCounterTradingValues().getPtEsCt()), Math.round(index.getBestValidDichotomyStepResult().getCounterTradingValues().getFrEsCt()));

        RaoResultWithCounterTradeRangeActions ptRaoResultWithRangeActions = getPtRaoResultWithCounterTradeRangeActions(ptEsRaoResultDestinationPath, raoParameters, network, cracPtEs, index.getBestValidDichotomyStepResult().getPtEsResult().getFirst(), index);
        Pair<RaoResult, Status> ptEsFinalStep = getRaoResultStatusPair(ptRaoResultWithRangeActions, index, interrupted, false);

        RaoResultWithCounterTradeRangeActions frRaoResultWithRangeActions = getFrRaoResultWithCounterTradeRangeActions(frEsRaoResultDestinationPath, raoParameters, network, cracFrEs, index.getBestValidDichotomyStepResult().getFrEsResult().getFirst(), index);
        Pair<RaoResult, Status> frEsFinalStep = getRaoResultStatusPair(frRaoResultWithRangeActions, index, interrupted, false);
        return new ParallelDichotomyResult(ptEsFinalStep, frEsFinalStep, index.getBestValidDichotomyStepResult().getCounterTradingValues());
    }

    private Pair<RaoResult, Status> getRaoResultStatusPair(RaoResult raoResultWithRangeActions, Index index, boolean interrupted, boolean stillRunningAndSecure) {
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
            return new Pair<>(raoResultWithRangeActions, status);
        }
    }

    private RaoResultWithCounterTradeRangeActions getPtRaoResultWithCounterTradeRangeActions(String raoResultDestinationPath, RaoParameters raoParameters, Network network, Crac crac, RaoResult raoResult, Index index) {
        RaoResult raoResultWithAngle = updateRaoResultWithVoltageMonitoring(network, crac, raoResult, raoParameters);
        RaoResultWithCounterTradeRangeActions raoResultWithRangeAction = updatePortugueseRaoResultWithCounterTradingRAs(network, crac, index, raoResultWithAngle);
        fileExporter.saveRaoResultInArtifact(raoResultDestinationPath, raoResultWithRangeAction, crac);
        return raoResultWithRangeAction;
    }

    private RaoResultWithCounterTradeRangeActions getFrRaoResultWithCounterTradeRangeActions(String raoResultDestinationPath, RaoParameters raoParameters, Network network, Crac crac, RaoResult raoResult, Index index) {
        RaoResult raoResultWithAngle = updateRaoResultWithVoltageMonitoring(network, crac, raoResult, raoParameters);
        RaoResultWithCounterTradeRangeActions raoResultWithRangeAction = updateFrenchRaoResultWithCounterTradingRAs(network, crac, index, raoResultWithAngle);
        fileExporter.saveRaoResultInArtifact(raoResultDestinationPath, raoResultWithRangeAction, crac);
        return raoResultWithRangeAction;
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
        return Monitoring.runVoltageAndUpdateRaoResult(LoadFlowAndSensitivityParameters.getLoadFlowProvider(raoParameters), LoadFlowAndSensitivityParameters.getSensitivityWithLoadFlowParameters(raoParameters).getLoadFlowParameters(), Runtime.getRuntime().availableProcessors(), voltageMonitoringInput);
    }

    private RaoResultWithCounterTradeRangeActions updatePortugueseRaoResultWithCounterTradingRAs(Network network, Crac crac, Index index, RaoResult raoResult) {
        Map<CounterTradeRangeAction, CounterTradeRangeActionResult> counterTradingResultsMap = new HashMap<>();
        List<String> ptEsFlowCnecs = SweCsaRaoValidator.getBorderFlowCnecs(crac, network, Country.PT).stream().map(Identifiable::getId).toList();
        counterTradingResultsMap.put(crac.getCounterTradeRangeAction(CT_RA_PTES), new CounterTradeRangeActionResult(CT_RA_PTES, Math.abs(index.getPtEsLowestSecureStep().getLeft()), ptEsFlowCnecs));
        counterTradingResultsMap.put(crac.getCounterTradeRangeAction(CT_RA_ESPT), new CounterTradeRangeActionResult(CT_RA_ESPT, Math.abs(index.getPtEsLowestSecureStep().getLeft()), ptEsFlowCnecs));
        return new RaoResultWithCounterTradeRangeActions(raoResult, new CounterTradingResult(counterTradingResultsMap));
    }

    private RaoResultWithCounterTradeRangeActions updateFrenchRaoResultWithCounterTradingRAs(Network network, Crac crac, Index index, RaoResult raoResult) {
        Map<CounterTradeRangeAction, CounterTradeRangeActionResult> counterTradingResultsMap = new HashMap<>();
        List<String> frEsFlowCnecs = SweCsaRaoValidator.getBorderFlowCnecs(crac, network, Country.FR).stream().map(Identifiable::getId).toList();
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

    private void logBorderOverload(DichotomyStepResult ctStepResult, String borderName) {
        if (ctStepResult.isSecure()) {
            businessLogger.info("There is no overload on '{}' border", borderName);
        } else {
            businessLogger.info("There is overloads on '{}' border, network is not secure", borderName);
            if (ctStepResult.getMostLimitingCnec() != null) {
                businessLogger.info("On the '{}' border, the most limiting CNEC is {}", borderName, ctStepResult.getMostLimitingCnec().getLeft());
            }
        }
    }

    private String getNewVariantName(CounterTradingValues counterTradingValues) {
        return String.format("network-ScaledBy-%s", counterTradingValues.print());
    }

    void updateCracWithPtEsCounterTradeRangeActions(Crac crac) {
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
    }

    void updateCracWithFrEsCounterTradeRangeActions(Crac crac) {
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
