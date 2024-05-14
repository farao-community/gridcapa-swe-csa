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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(DichotomyRunner.class);

    public DichotomyRunner(SweCsaRaoValidator sweCsaRaoValidator, FileImporter fileImporter, FileExporter fileExporter) {
        this.sweCsaRaoValidator = sweCsaRaoValidator;
        this.fileImporter = fileImporter;
        this.fileExporter = fileExporter;
    }

    public RaoResult runDichotomy(CsaRequest csaRequest) throws GlskLimitationException, ShiftingException {
        String raoParametersUrl = fileImporter.uploadRaoParameters(Instant.parse(csaRequest.getBusinessTimestamp()));
        Network network = fileImporter.importNetwork(csaRequest.getGridModelUri());
        Crac crac = fileImporter.importCrac(csaRequest.getCracFileUri(), network);
        updateCracWithCounterTrageRangeActions(crac);

        String initialVariant = network.getVariantManager().getWorkingVariantId();

        Map<String, Double> initialNetPositions = CountryBalanceComputation.computeSweCountriesBalances(network)
            .entrySet().stream()
            .collect(Collectors.toMap(entry -> new CountryEICode(entry.getKey()).getCountry().getName(), Map.Entry::getValue));

        LOGGER.info("Initial net positions: PT: {}, ES: {}, FR: {}", initialNetPositions.get("PORTUGAL"), initialNetPositions.get("SPAIN"), initialNetPositions.get("FRANCE"));
        Map<String, Double> initialExchanges = CountryBalanceComputation.computeSweBordersExchanges(network);

        double expEsFr0 = initialExchanges.get("ES_FR");
        double expEsPt0 = initialExchanges.get("ES_PT");
        double expFrEs0 = -expEsFr0;
        double expPtEs0 = -expEsPt0;
        LOGGER.info("Initial exchanges: PT->ES: {}, FR->ES: {}", expPtEs0, expFrEs0);

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
            LOGGER.warn(e.getMessage());
            LOGGER.warn("No counter trading will be done, only input network will be checked by rao");
            return sweCsaRaoValidator.validateNetwork("input-network", network, crac, csaRequest, raoParametersUrl, true, true, minCounterTradingValues).getRaoResult();
        }
        // best case no counter trading , no scaling
        LOGGER.info("Starting Counter trading algorithm by validating input network without scaling");
        String noCtVariantName = "no-ct-PT-ES-0_FR-ES-0";
        setWorkingVariant(network, initialVariant, noCtVariantName);
        DichotomyStepResult noCtStepResult = sweCsaRaoValidator.validateNetwork("input-network", network, crac, csaRequest, raoParametersUrl, true, true, minCounterTradingValues);
        resetToInitialVariant(network, initialVariant, noCtVariantName);

        logBorderOverload(noCtStepResult);

        if (noCtStepResult.isValid()) {
            LOGGER.info("Input network is secure no need for counter trading");
            return noCtStepResult.getRaoResult();
        } else {
            // initial network not secure, try worst case maximum counter trading
            double ctFrEsMax = expFrEs0 >= 0 ? Math.min(Math.min(-ctRaFrEs.getMinAdmissibleSetpoint(expFrEs0), ctRaEsFr.getMaxAdmissibleSetpoint(expEsFr0)), expFrEs0)
                : Math.min(Math.min(ctRaFrEs.getMaxAdmissibleSetpoint(expFrEs0), -ctRaEsFr.getMinAdmissibleSetpoint(expEsFr0)), -expFrEs0);
            double ctPtEsMax = expPtEs0 >= 0 ? Math.min(Math.min(-ctRaPtEs.getMinAdmissibleSetpoint(expPtEs0), ctRaEsPt.getMaxAdmissibleSetpoint(expEsPt0)), expPtEs0)
                : Math.min(Math.min(ctRaPtEs.getMaxAdmissibleSetpoint(expPtEs0), -ctRaEsPt.getMinAdmissibleSetpoint(expEsPt0)), -expPtEs0);

            double ctPtEsUpperBound = noCtStepResult.isPtEsCnecsSecure() ? 0 : ctPtEsMax;
            double ctFrEsUpperBound = noCtStepResult.isFrEsCnecsSecure() ? 0 : ctFrEsMax;
            CounterTradingValues maxCounterTradingValues = new CounterTradingValues(ctPtEsUpperBound, ctFrEsUpperBound);
            LOGGER.info("Testing Counter trading worst case by scaling to maximum: CT PT-ES: '{}', and CT FR-ES: '{}'", ctPtEsUpperBound, ctFrEsUpperBound);

            String maxCtVariantName = getNewVariantName(maxCounterTradingValues);
            setWorkingVariant(network, initialVariant, maxCtVariantName);

            SweCsaNetworkShifter networkShifter = new SweCsaNetworkShifter(SweCsaZonalData.getZonalData(network), initialExchanges.get("ES_FR"), initialExchanges.get("ES_PT"), new ShiftDispatcher(initialNetPositions));
            networkShifter.applyCounterTrading(maxCounterTradingValues, network);
            DichotomyStepResult maxCtStepResult = sweCsaRaoValidator.validateNetwork(maxCounterTradingValues.print(), network, crac, csaRequest, raoParametersUrl, true, true, maxCounterTradingValues);
            resetToInitialVariant(network, initialVariant, maxCtVariantName);

            logBorderOverload(maxCtStepResult);
            if (!maxCtStepResult.isValid()) {
                // TODO [US] [CSA-68] Handle cases that CT cannot secure
                String errorMessage = "Maximum CT value cannot secure this case";
                LOGGER.error(errorMessage);
                throw new CsaInvalidDataException(errorMessage);
            } else {
                LOGGER.info("Best case in unsecure, worst case is secure, trying to find optimum in between using dichotomy");
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
                        LOGGER.info("Next CT values are '{}' for PT-ES and '{}' for FR-ES", counterTradingValues.getPtEsCt(), counterTradingValues.getFrEsCt());

                        setWorkingVariant(network, initialVariant, newVariantName);
                        networkShifter.applyCounterTrading(counterTradingValues, network);
                        ctStepResult = sweCsaRaoValidator.validateNetwork(counterTradingValues.print(), network, crac, csaRequest, raoParametersUrl, true, true, counterTradingValues);
                    } catch (GlskLimitationException e) {
                        LOGGER.warn("GLSK limits have been reached with CT of '{}' for PT-ES and '{}' for FR-ES", counterTradingValues.getPtEsCt(), counterTradingValues.getFrEsCt());
                        ctStepResult = DichotomyStepResult.fromFailure(ReasonInvalid.GLSK_LIMITATION, e.getMessage(), true, true, counterTradingValues);
                    } catch (ShiftingException | RaoRunnerException e) {
                        LOGGER.warn("Validation failed with CT of '{}' for PT-ES and '{}' for FR-ES", counterTradingValues.getPtEsCt(), counterTradingValues.getFrEsCt());
                        ctStepResult = DichotomyStepResult.fromFailure(ReasonInvalid.GLSK_LIMITATION, e.getMessage(), true, true, counterTradingValues);
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
                LOGGER.info("Dichotomy stop criterion reached, CT PT-ES: {}, CT FR-ES: {}", Math.round(index.getBestValidDichotomyStepResult().getCounterTradingValues().getPtEsCt()), Math.round(index.getBestValidDichotomyStepResult().getCounterTradingValues().getFrEsCt()));
                RaoResult raoResult = index.getBestValidDichotomyStepResult().getRaoResult();

                Map<CounterTradeRangeAction, CounterTradeRangeActionResult> counterTradingResult = new HashMap<>();
                List<String> frEsFlowCnecs = SweCsaRaoValidator.getBorderFlowCnecs(crac, network, Country.FR).stream().map(Identifiable::getId).toList();
                List<String> ptEsFlowCnecs = SweCsaRaoValidator.getBorderFlowCnecs(crac, network, Country.PT).stream().map(Identifiable::getId).toList();
                counterTradingResult.put(crac.getCounterTradeRangeAction("CT_RA_PTES"), new CounterTradeRangeActionResult("CT_RA_PTES", index.getPtEsLowestSecureStep().getLeft(), ptEsFlowCnecs));
                counterTradingResult.put(crac.getCounterTradeRangeAction("CT_RA_FRES"), new CounterTradeRangeActionResult("CT_RA_FRES", index.getFrEsLowestSecureStep().getLeft(), frEsFlowCnecs));
                CounterTradingResult ctResult = new CounterTradingResult(counterTradingResult);
                RaoResultWithCounterTradeRangeActions raoResultWithRangeAction = new RaoResultWithCounterTradeRangeActions(raoResult, ctResult);
                fileExporter.saveRaoResultInArtifact(raoResultWithRangeAction, crac, Unit.AMPERE, csaRequest.getBusinessTimestamp());
                return raoResultWithRangeAction;
            }
        }
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
            LOGGER.info("There is no overload on FR-ES border");
        } else {
            LOGGER.info("There is overloads on FR-ES border, network is not secure");
        }

        if (ctStepResult.isPtEsCnecsSecure()) {
            LOGGER.info("There is no overload on PT-ES border");
        } else {
            LOGGER.info("There is overloads on PT-ES border, network is not secure");
        }
    }

    private String getNewVariantName(CounterTradingValues counterTradingValues) {
        return String.format("network-ScaledBy-%s", counterTradingValues.print());
    }

    void updateCracWithCounterTrageRangeActions(Crac crac) {
        crac.newCounterTradeRangeAction()
            .withId("CT_RA_PTES")
            .withOperator("REN")
            .newRange().withMin(-50000.0)
            .withMax(50000.0).add()
            .withInitialSetpoint(0.0)
            .withExportingCountry(Country.PT)
            .withImportingCountry(Country.ES)
            .add();
        crac.newCounterTradeRangeAction()
            .withId("CT_RA_ESPT")
            .withOperator("REE")
            .newRange().withMin(-50000.0)
            .withMax(50000.0).add()
            .withInitialSetpoint(0.0)
            .withExportingCountry(Country.ES)
            .withImportingCountry(Country.PT)
            .add();
        crac.newCounterTradeRangeAction()
            .withId("CT_RA_ESFR")
            .withOperator("REE")
            .newRange().withMin(-50000.0)
            .withMax(50000.0).add()
            .withInitialSetpoint(0.0)
            .withExportingCountry(Country.ES)
            .withImportingCountry(Country.FR)
            .add();
        crac.newCounterTradeRangeAction()
            .withId("CT_RA_FRES")
            .withOperator("RTE")
            .newRange().withMin(-50000.0)
            .withMax(50000.0).add()
            .withInitialSetpoint(0.0)
            .withExportingCountry(Country.FR)
            .withImportingCountry(Country.ES)
            .add();
    }
}
