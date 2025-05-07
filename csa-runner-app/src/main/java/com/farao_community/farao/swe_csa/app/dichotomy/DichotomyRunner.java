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
import com.powsybl.openrao.data.crac.api.rangeaction.CounterTradeRangeAction;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import com.powsybl.openrao.raoapi.parameters.extensions.LoadFlowAndSensitivityParameters;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Service;

import java.time.Instant;
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
    private final ResultHelper resultHelper;
    private final FileImporter fileImporter;
    private final FileExporter fileExporter;
    private final InterruptionService interruptionService;
    private final StreamBridge streamBridge;
    private final S3ArtifactsAdapter s3ArtifactsAdapter;
    private final JsonApiConverter jsonApiConverter = new JsonApiConverter();
    private final Logger businessLogger;
    private final ParallelDichotomiesRunner parallelDichotomiesRunner;

    private static final String ES_FR = "ES_FR";
    private static final String ES_PT = "ES_PT";

    public DichotomyRunner(SweCsaRaoValidator sweCsaRaoValidator, FileImporter fileImporter, FileExporter fileExporter, InterruptionService interruptionService, StreamBridge streamBridge, S3ArtifactsAdapter s3ArtifactsAdapter, Logger businessLogger, ParallelDichotomiesRunner parallelDichotomiesRunner) {
        this.sweCsaRaoValidator = sweCsaRaoValidator;
        this.resultHelper = new ResultHelper(fileExporter);
        this.fileImporter = fileImporter;
        this.fileExporter = fileExporter;
        this.interruptionService = interruptionService;
        this.streamBridge = streamBridge;
        this.s3ArtifactsAdapter = s3ArtifactsAdapter;
        this.businessLogger = businessLogger;
        this.parallelDichotomiesRunner = parallelDichotomiesRunner;
    }

    public FinalResult runDichotomy(CsaRequest csaRequest, String ptEsRaoResultDestinationPath, String frEsRaoResultDestinationPath) throws GlskLimitationException, ShiftingException {
        RaoParameters raoParameters = RaoParameters.load();
        Instant instant = Instant.parse(csaRequest.getBusinessTimestamp());
        String raoParametersUrl = fileImporter.uploadRaoParameters(instant);
        Network network = fileImporter.importNetwork(csaRequest.getId(), csaRequest.getGridModelUri());
        Crac cracPtEs = fileImporter.importCrac(csaRequest.getId(), csaRequest.getPtEsCracFileUri(), network);
        Crac cracFrEs = fileImporter.importCrac(csaRequest.getId(), csaRequest.getFrEsCracFileUri(), network);

        ZonalData<Scalable> scalableZonalData = fileImporter.getZonalData(csaRequest.getId(), instant, csaRequest.getGlskUri(), network, false);
        ZonalData<Scalable> scalableZonalDataFilteredForSweCountries = fileImporter.getZonalData(csaRequest.getId(), instant, csaRequest.getGlskUri(), network, true);

        // TODO remove me, temporary workaround, in real data crac should already contain CT-RAs
        Preprocessing.updateCracWithPtEsCounterTradeRangeActions(cracPtEs);
        Preprocessing.updateCracWithFrEsCounterTradeRangeActions(cracFrEs);

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
            Supplier<DichotomyStepResult> ptEsraoResultPSupplier = () -> sweCsaRaoValidator.validateNetworkForPortugueseBorder(network, cracPtEs, csaRequest.getPtEsCracFileUri(), scalableZonalDataFilteredForSweCountries, raoParameters, csaRequest, raoParametersUrl, minCounterTradingValues);
            Supplier<DichotomyStepResult> frEsraoResultPSupplier = () -> sweCsaRaoValidator.validateNetworkForFrenchBorder(network, cracFrEs, csaRequest.getFrEsCracFileUri(), scalableZonalDataFilteredForSweCountries, raoParameters, csaRequest, raoParametersUrl, minCounterTradingValues);
            ParallelDichotomiesResult parallelDichotomiesResult = parallelDichotomiesRunner.run(csaRequest.getId(), minCounterTradingValues, ptEsraoResultPSupplier, frEsraoResultPSupplier);

            fileExporter.saveRaoResultInArtifact(ptEsRaoResultDestinationPath, parallelDichotomiesResult.getPtEsResult().getRaoResult(), cracPtEs);
            fileExporter.saveRaoResultInArtifact(frEsRaoResultDestinationPath, parallelDichotomiesResult.getFrEsResult().getRaoResult(), cracFrEs);

            return FinalResult.fromDichotomyStepResults(parallelDichotomiesResult.getPtEsResult(), parallelDichotomiesResult.getFrEsResult());
        }
        // best case no counter trading , no scaling
        businessLogger.info("Starting Counter trading algorithm by validating input network without scaling");
        String noCtVariantName = "no-ct-PT-ES-0_FR-ES-0";
        setWorkingVariant(network, initialVariant, noCtVariantName);

        Supplier<DichotomyStepResult> noCtStepResultPtEsSupplier = () -> sweCsaRaoValidator.validateNetworkForPortugueseBorder(network, cracPtEs, csaRequest.getPtEsCracFileUri(), scalableZonalDataFilteredForSweCountries, raoParameters, csaRequest, raoParametersUrl, minCounterTradingValues);
        Supplier<DichotomyStepResult> noCtStepResultFrEsSupplier = () -> sweCsaRaoValidator.validateNetworkForFrenchBorder(network, cracFrEs, csaRequest.getFrEsCracFileUri(), scalableZonalDataFilteredForSweCountries, raoParameters, csaRequest, raoParametersUrl, minCounterTradingValues);
        ParallelDichotomiesResult noCtParallelDichotomiesResult = parallelDichotomiesRunner.run(csaRequest.getId(), minCounterTradingValues, noCtStepResultPtEsSupplier, noCtStepResultFrEsSupplier);
        logBorderOverload(noCtParallelDichotomiesResult.getPtEsResult(), DichotomyDirection.PT_ES.toString());
        logBorderOverload(noCtParallelDichotomiesResult.getFrEsResult(), DichotomyDirection.FR_ES.toString());

        resetToInitialVariant(network, initialVariant, noCtVariantName);

        if (noCtParallelDichotomiesResult.getPtEsResult().isSecure() && noCtParallelDichotomiesResult.getFrEsResult().isSecure()) {
            businessLogger.info("Input network is secure no need for counter trading");
            fileExporter.saveRaoResultInArtifact(ptEsRaoResultDestinationPath, noCtParallelDichotomiesResult.getPtEsResult().getRaoResult(), cracPtEs);
            fileExporter.saveRaoResultInArtifact(frEsRaoResultDestinationPath, noCtParallelDichotomiesResult.getFrEsResult().getRaoResult(), cracFrEs);
            return FinalResult.fromDichotomyStepResults(noCtParallelDichotomiesResult.getPtEsResult(), noCtParallelDichotomiesResult.getFrEsResult());
        } else {
            if (interruptionService.getTasksToInterrupt().remove(csaRequest.getId())) {
                businessLogger.info("Interruption asked for task {}, before any secure situation is found", csaRequest.getId());
                return new FinalResult(Pair.of(null, Status.FINISHED_UNSECURE), Pair.of(null, Status.FINISHED_UNSECURE));
            }

            // initial network not secure, try worst case maximum counter trading
            double ctPtEsMax = getMaxCounterTrading(ctRaPtEs, ctRaEsPt, expPtEs0, DichotomyDirection.PT_ES.toString());
            double ctFrEsMax = getMaxCounterTrading(ctRaFrEs, ctRaEsFr, expFrEs0, DichotomyDirection.FR_ES.toString());

            double ctPtEsUpperBound = noCtParallelDichotomiesResult.getPtEsResult().isSecure() ? 0 : ctPtEsMax;
            double ctFrEsUpperBound = noCtParallelDichotomiesResult.getFrEsResult().isSecure() ? 0 : ctFrEsMax;
            CounterTradingValues maxCounterTradingValues = new CounterTradingValues(ctPtEsUpperBound, ctFrEsUpperBound);
            businessLogger.info("Testing Counter trading worst case by scaling to maximum: CT PT-ES: '{}', and CT FR-ES: '{}'", ctPtEsUpperBound, ctFrEsUpperBound);

            String maxCtVariantName = getNewVariantName(maxCounterTradingValues);
            setWorkingVariant(network, initialVariant, maxCtVariantName);

            SweCsaNetworkShifter networkShifter = new SweCsaNetworkShifter(scalableZonalData, initialExchanges.get(ES_FR), initialExchanges.get(ES_PT), new ShiftDispatcher(initialNetPositions));
            networkShifter.applyCounterTrading(maxCounterTradingValues, network);

            Supplier<DichotomyStepResult> maxCtStepResultPtEsSupplier = () -> sweCsaRaoValidator.validateNetworkForPortugueseBorder(network, cracPtEs, csaRequest.getPtEsCracFileUri(), scalableZonalDataFilteredForSweCountries, raoParameters, csaRequest, raoParametersUrl, maxCounterTradingValues);
            Supplier<DichotomyStepResult> maxCtStepResultFrEsSupplier = () -> sweCsaRaoValidator.validateNetworkForFrenchBorder(network, cracFrEs, csaRequest.getFrEsCracFileUri(), scalableZonalDataFilteredForSweCountries, raoParameters, csaRequest, raoParametersUrl, maxCounterTradingValues);

            ParallelDichotomiesResult maxCtParallelDichotomiesResult = parallelDichotomiesRunner.run(csaRequest.getId(), maxCounterTradingValues, maxCtStepResultPtEsSupplier, maxCtStepResultFrEsSupplier);
            logBorderOverload(maxCtParallelDichotomiesResult.getPtEsResult(), DichotomyDirection.PT_ES.toString());
            logBorderOverload(maxCtParallelDichotomiesResult.getFrEsResult(), DichotomyDirection.FR_ES.toString());

            resetToInitialVariant(network, initialVariant, maxCtVariantName);

            if (!maxCtParallelDichotomiesResult.getPtEsResult().isSecure() || !maxCtParallelDichotomiesResult.getFrEsResult().isSecure()) {
                businessLogger.error("Maximum CT value cannot secure this case");
                fileExporter.saveRaoResultInArtifact(ptEsRaoResultDestinationPath, maxCtParallelDichotomiesResult.getPtEsResult().getRaoResult(), cracPtEs);
                fileExporter.saveRaoResultInArtifact(frEsRaoResultDestinationPath, maxCtParallelDichotomiesResult.getFrEsResult().getRaoResult(), cracFrEs);
                return FinalResult.fromDichotomyStepResults(maxCtParallelDichotomiesResult.getPtEsResult(), maxCtParallelDichotomiesResult.getFrEsResult());
            } else {
                businessLogger.info("Best case in unsecure, worst case is secure, trying to find optimum in between using dichotomy");
                Index index = new Index(0, 0, indexPrecision, maxDichotomiesByBorder);

                index.addPtEsDichotomyStepResult(0, noCtParallelDichotomiesResult.getPtEsResult());
                index.addPtEsDichotomyStepResult(ctPtEsUpperBound, maxCtParallelDichotomiesResult.getPtEsResult());

                index.addFrEsDichotomyStepResult(0, noCtParallelDichotomiesResult.getFrEsResult());
                index.addFrEsDichotomyStepResult(ctFrEsUpperBound, maxCtParallelDichotomiesResult.getFrEsResult());

                index.setBestValidDichotomyStepResult(maxCtParallelDichotomiesResult);
                return processDichotomy(csaRequest, ptEsRaoResultDestinationPath, frEsRaoResultDestinationPath, raoParameters, raoParametersUrl, network, cracPtEs, cracFrEs, scalableZonalData, initialVariant, networkShifter, index);
            }
        }
    }

    private FinalResult processDichotomy(CsaRequest csaRequest, String ptEsRaoResultDestinationPath, String frEsRaoResultDestinationPath, RaoParameters raoParameters, String raoParametersUrl, Network network, Crac cracPtEs, Crac cracFrEs, ZonalData<Scalable> scalableZonalDataFilteredForSweCountries, String initialVariant, SweCsaNetworkShifter networkShifter, Index index) {
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
                ParallelDichotomiesResult parallelDichotomiesResult = parallelDichotomiesRunner.run(csaRequest.getId(), counterTradingValues, ptEsStepResultSupplier, frEsStepResultSupplier);

                logBorderOverload(parallelDichotomiesResult.getPtEsResult(), DichotomyDirection.PT_ES.toString());
                logBorderOverload(parallelDichotomiesResult.getFrEsResult(), DichotomyDirection.FR_ES.toString());

                ptEsCtStepResult = parallelDichotomiesResult.getPtEsResult();
                frEsCtStepResult = parallelDichotomiesResult.getFrEsResult();
                boolean ptEsCtSecure = index.addPtEsDichotomyStepResult(counterTradingValues.getPtEsCt(), ptEsCtStepResult);
                boolean frEsCtSecure = index.addFrEsDichotomyStepResult(counterTradingValues.getFrEsCt(), frEsCtStepResult);
                if (ptEsCtSecure && frEsCtSecure) {
                    index.setBestValidDichotomyStepResult(parallelDichotomiesResult);
                    // enhance rao result with monitoring result + CT values and send notification
                    resultHelper.uploadRaoResultWithCounterTradeRangeActions(ptEsRaoResultDestinationPath, raoParameters, network, cracPtEs, ptEsCtStepResult.getRaoResult(), index, Country.PT);
                    resultHelper.uploadRaoResultWithCounterTradeRangeActions(frEsRaoResultDestinationPath, raoParameters, network, cracFrEs, frEsCtStepResult.getRaoResult(), index, Country.FR);
                    CsaResponse csaResponse = new CsaResponse(csaRequest.getId(), "FINISHED_SECURE", s3ArtifactsAdapter.generatePreSignedUrl(ptEsRaoResultDestinationPath), "FINISHED_SECURE", s3ArtifactsAdapter.generatePreSignedUrl(frEsRaoResultDestinationPath));
                    streamBridge.send(RESPONSE_BRIDGE_NAME, jsonApiConverter.toJsonMessage(csaResponse, CsaResponse.class));
                }
            } catch (GlskLimitationException e) {
                businessLogger.warn("GLSK limits have been reached with CT of '{}' for PT-ES and '{}' for FR-ES", counterTradingValues.getPtEsCt(), counterTradingValues.getFrEsCt());
                ptEsCtStepResult = DichotomyStepResult.fromFailure(ReasonInvalid.GLSK_LIMITATION, e.getMessage(), counterTradingValues);
                frEsCtStepResult = ptEsCtStepResult;
                index.addPtEsDichotomyStepResult(counterTradingValues.getPtEsCt(), ptEsCtStepResult);
                index.addFrEsDichotomyStepResult(counterTradingValues.getFrEsCt(), frEsCtStepResult);

            } catch (ShiftingException | RaoRunnerException e) {
                businessLogger.warn("Validation failed with CT of '{}' for PT-ES and '{}' for FR-ES", counterTradingValues.getPtEsCt(), counterTradingValues.getFrEsCt());
                ptEsCtStepResult = DichotomyStepResult.fromFailure(ReasonInvalid.GLSK_LIMITATION, e.getMessage(), counterTradingValues);
                frEsCtStepResult = ptEsCtStepResult;
                index.addPtEsDichotomyStepResult(counterTradingValues.getPtEsCt(), ptEsCtStepResult);
                index.addFrEsDichotomyStepResult(counterTradingValues.getFrEsCt(), frEsCtStepResult);
            } finally {
                resetToInitialVariant(network, initialVariant, newVariantName);
            }

        }
        businessLogger.info("Dichotomy stop criterion reached, CT PT-ES: {}, CT FR-ES: {}", Math.round(index.getBestValidDichotomyStepResult().getCounterTradingValues().getPtEsCt()), Math.round(index.getBestValidDichotomyStepResult().getCounterTradingValues().getFrEsCt()));

        RaoResultWithCounterTradeRangeActions ptEsRaoResult = resultHelper.uploadRaoResultWithCounterTradeRangeActions(ptEsRaoResultDestinationPath, raoParameters, network, cracPtEs, index.getBestValidDichotomyStepResult().getPtEsResult().getRaoResult(), index, Country.PT);
        RaoResultWithCounterTradeRangeActions frEsRaoResult = resultHelper.uploadRaoResultWithCounterTradeRangeActions(frEsRaoResultDestinationPath, raoParameters, network, cracFrEs, index.getBestValidDichotomyStepResult().getFrEsResult().getRaoResult(), index, Country.FR);

        return new FinalResult(getRaoResultStatusPair(ptEsRaoResult, index, interrupted, false), getRaoResultStatusPair(frEsRaoResult, index, interrupted, false));
    }

    private Pair<RaoResult, Status> getRaoResultStatusPair(RaoResult raoResult, Index index, boolean interrupted, boolean stillRunningAndSecure) {
        Status status;
        if (index.getBestValidDichotomyStepResult() == null) {
            status = Status.FINISHED_UNSECURE;
            return Pair.of(null, status);
        } else {
            if (interrupted) {
                status = Status.INTERRUPTED_SECURE;
            } else if (stillRunningAndSecure) {
                status = Status.STILL_RUNNING_SECURE;
            } else {
                status = Status.FINISHED_SECURE;
            }
            return Pair.of(raoResult, status);
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

    void setIndexPrecision(double indexPrecision) {
        this.indexPrecision = indexPrecision;
    }

    void setMaxDichotomiesByBorder(double maxDichotomiesByBorder) {
        this.maxDichotomiesByBorder = maxDichotomiesByBorder;
    }
}
