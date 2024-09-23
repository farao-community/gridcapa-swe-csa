package com.farao_community.farao.swe_csa.app.dichotomy;

import com.farao_community.farao.dichotomy.api.exceptions.GlskLimitationException;
import com.farao_community.farao.dichotomy.api.exceptions.ShiftingException;
import com.farao_community.farao.dichotomy.api.results.ReasonInvalid;
import com.farao_community.farao.gridcapa_swe_commons.shift.CountryBalanceComputation;
import com.farao_community.farao.rao_runner.api.exceptions.RaoRunnerException;
import com.farao_community.farao.swe_csa.api.exception.CsaInvalidDataException;
import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
import com.farao_community.farao.swe_csa.api.results.CounterTradeRangeActionResult;
import com.farao_community.farao.swe_csa.api.results.CounterTradingResult;
import com.farao_community.farao.swe_csa.app.*;
import com.farao_community.farao.swe_csa.app.rao_result.RaoResultWithCounterTradeRangeActions;
import com.powsybl.glsk.commons.CountryEICode;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.commons.Unit;
import com.powsybl.openrao.data.cracapi.Crac;
import com.powsybl.openrao.data.cracapi.Identifiable;
import com.powsybl.openrao.data.cracapi.rangeaction.CounterTradeRangeAction;
import com.powsybl.openrao.data.raoresultapi.RaoResult;
import com.powsybl.openrao.monitoring.voltagemonitoring.VoltageMonitoring;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DichotomyRunner {

    @Value("${dichotomy-parameters.index.precision}")
    private double indexPrecision;
    @Value("${dichotomy-parameters.index.max-iterations-by-border}")
    private double maxDichotomiesByBorder;
    private final SweCsaRaoValidator sweCsaRaoValidator;
    private final FileImporter fileImporter;
    private final FileExporter fileExporter;

    private static final String CT_RA_PTES = "CT_RA_PTES";
    private static final String CT_RA_FRES = "CT_RA_FRES";
    private static final String CT_RA_ESPT = "CT_RA_ESPT";

    private static final String CT_RA_ESFR = "CT_RA_ESFR";

    private static final String ES_FR = "ES_FR";
    private static final String ES_PT = "ES_PT";

    private final Logger businessLogger;

    public DichotomyRunner(SweCsaRaoValidator sweCsaRaoValidator, FileImporter fileImporter, FileExporter fileExporter, Logger businessLogger) {
        this.sweCsaRaoValidator = sweCsaRaoValidator;
        this.fileImporter = fileImporter;
        this.fileExporter = fileExporter;
        this.businessLogger = businessLogger;
    }

    public RaoResult runDichotomy(CsaRequest csaRequest) throws GlskLimitationException, ShiftingException {
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
            return sweCsaRaoValidator.validateNetwork(network, crac, raoParameters, csaRequest, raoParametersUrl, minCounterTradingValues).getRaoResult();
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
            return noCtStepResult.getRaoResult();
        } else {
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
                String errorMessage = "Maximum CT value cannot secure this case";
                businessLogger.error(errorMessage);
                throw new CsaInvalidDataException(errorMessage);
            } else {
                businessLogger.info("Best case in unsecure, worst case is secure, trying to find optimum in between using dichotomy");
                Index index = new Index(0, 0, indexPrecision, maxDichotomiesByBorder);
                index.addPtEsDichotomyStepResult(0, noCtStepResult);
                index.addPtEsDichotomyStepResult(ctPtEsUpperBound, maxCtStepResult);

                index.addFrEsDichotomyStepResult(0, noCtStepResult);
                index.addFrEsDichotomyStepResult(ctFrEsUpperBound, maxCtStepResult);

                while (index.exitConditionIsNotMetForPtEs() || index.exitConditionIsNotMetForFrEs()) {
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
                    }
                }
                businessLogger.info("Dichotomy stop criterion reached, CT PT-ES: {}, CT FR-ES: {}", Math.round(index.getBestValidDichotomyStepResult().getCounterTradingValues().getPtEsCt()), Math.round(index.getBestValidDichotomyStepResult().getCounterTradingValues().getFrEsCt()));
                RaoResult raoResult = index.getBestValidDichotomyStepResult().getRaoResult();

                raoResult = updateRaoResultWithVoltageMonitoring(network, crac, raoResult, raoParameters);
                RaoResultWithCounterTradeRangeActions raoResultWithRangeAction = updateRaoResultWithCounterTradingRAs(network, crac, index, raoResult);
                fileExporter.saveRaoResultInArtifact(raoResultWithRangeAction, crac, Unit.AMPERE, csaRequest.getBusinessTimestamp());
                return raoResultWithRangeAction;
            }
        }
    }

    double getMaxCounterTrading(CounterTradeRangeAction ctraTowardsES, CounterTradeRangeAction ctraFromES, double initialExchangeTowardsES, String borderName) {
        double ctMax = initialExchangeTowardsES >= 0 ? Math.min(Math.min(-ctraTowardsES.getMinAdmissibleSetpoint(initialExchangeTowardsES), ctraFromES.getMaxAdmissibleSetpoint(-initialExchangeTowardsES)), initialExchangeTowardsES)
            : Math.min(Math.min(ctraTowardsES.getMaxAdmissibleSetpoint(initialExchangeTowardsES), -ctraFromES.getMinAdmissibleSetpoint(-initialExchangeTowardsES)), -initialExchangeTowardsES);

        if (ctMax != Math.abs(initialExchangeTowardsES)) {
            businessLogger.warn("Maximum counter trading " + borderName + " '{}' is different from initial exchange " + borderName + " '{}' ", ctMax, Math.abs(initialExchangeTowardsES));
        }

        return ctMax;
    }

    private static RaoResult updateRaoResultWithVoltageMonitoring(Network network, Crac crac, RaoResult raoResult, RaoParameters raoParameters) {
        VoltageMonitoring voltageMonitoring = new VoltageMonitoring(crac, network, raoResult);
        return voltageMonitoring.runAndUpdateRaoResult(raoParameters.getLoadFlowAndSensitivityParameters().getLoadFlowProvider(), raoParameters.getLoadFlowAndSensitivityParameters().getSensitivityWithLoadFlowParameters().getLoadFlowParameters(), Runtime.getRuntime().availableProcessors());
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
        throw new CsaInvalidDataException(String.format("Crac should contain 4 counter trading remedial actions for csa swe process, Two CT RAs by border, and couldn't find CT RA for '%s' as exporting country and '%s' as importing country", exportingCountry.getName(), importingCountry.getName()));
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
