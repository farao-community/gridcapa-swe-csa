package com.farao_community.farao.swe_csa.app.dichotomy;

import com.farao_community.farao.dichotomy.api.exceptions.GlskLimitationException;
import com.farao_community.farao.dichotomy.api.exceptions.ShiftingException;
import com.farao_community.farao.dichotomy.api.exceptions.ValidationException;
import com.farao_community.farao.dichotomy.api.results.ReasonInvalid;
import com.farao_community.farao.gridcapa_swe_commons.shift.CountryBalanceComputation;
import com.farao_community.farao.rao_runner.api.exceptions.RaoRunnerException;
import com.farao_community.farao.swe_csa.api.exception.CsaInvalidDataException;
import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
import com.farao_community.farao.swe_csa.app.FileImporter;
import com.powsybl.glsk.commons.CountryEICode;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.cracapi.Crac;
import com.powsybl.openrao.data.cracapi.rangeaction.CounterTradeRangeAction;
import com.powsybl.openrao.data.raoresultapi.RaoResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

import static com.farao_community.farao.dichotomy.api.logging.DichotomyLoggerProvider.BUSINESS_WARNS;

@Service
public class DichotomyRunner {

    @Value("${dichotomy-parameters.index.precision}")
    private double indexPrecision;
    @Value("${dichotomy-parameters.index.max-iterations-by-border}")
    private double maxDichotomiesByBorder;
    private final SweCsaRaoValidator sweCsaRaoValidator;
    private final FileImporter fileImporter;
    private static final Logger LOGGER = LoggerFactory.getLogger(DichotomyRunner.class);

    public DichotomyRunner(SweCsaRaoValidator sweCsaRaoValidator, FileImporter fileImporter) {
        this.sweCsaRaoValidator = sweCsaRaoValidator;
        this.fileImporter = fileImporter;
    }

    public RaoResult runDichotomy(CsaRequest csaRequest) throws GlskLimitationException, ShiftingException {
        String raoParametersUrl = fileImporter.uploadRaoParameters(csaRequest.getId(), Instant.parse(csaRequest.getBusinessTimestamp()));
        Network network = fileImporter.importNetwork(csaRequest.getGridModelUri());
        Crac crac = fileImporter.importCrac(csaRequest.getCracFileUri(), network);

        String initialVariant = network.getVariantManager().getWorkingVariantId();

        Map<String, Double> initialNetPositions = CountryBalanceComputation.computeSweCountriesBalances(network)
            .entrySet().stream()
            .collect(Collectors.toMap(entry -> new CountryEICode(entry.getKey()).getCountry().getName(), Map.Entry::getValue));

        Map<String, Double> initialExchanges = CountryBalanceComputation.computeSweBordersExchanges(network);

        double expEsFr0 = initialExchanges.get("ES_FR");
        double expEsPt0 = initialExchanges.get("ES_PT");
        double expFrEs0 = -expEsFr0;
        double expPtEs0 = -expEsPt0;

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
            return sweCsaRaoValidator.validateNetwork(network, crac, csaRequest, raoParametersUrl, true, true).getRaoResult();
        }
        // best case no counter trading , no scaling
        LOGGER.info("Starting Counter trading algorithm by validating input network without scaling");
        String noCtVariantName = "no ct PT-ES: 0, FR-ES: 0";
        setWorkingVariant(network, initialVariant, noCtVariantName);
        DichotomyStepResult noCtStepResult = sweCsaRaoValidator.validateNetwork(network, crac, csaRequest, raoParametersUrl, true, true);
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

            String maxCtVariantName = getNewVariantName(maxCounterTradingValues, initialVariant);
            setWorkingVariant(network, initialVariant, maxCtVariantName);

            SweCsaNetworkShifter networkShifter = new SweCsaNetworkShifter(SweCsaZonalData.getZonalData(network), new ShiftDispatcher(initialNetPositions));
            networkShifter.shiftNetwork(maxCounterTradingValues, network);
            DichotomyStepResult maxCtStepResult = sweCsaRaoValidator.validateNetwork(network, crac, csaRequest, raoParametersUrl, true, true);
            resetToInitialVariant(network, initialVariant, maxCtVariantName);

            logBorderOverload(maxCtStepResult);
            if (!maxCtStepResult.isValid()) {
                // TODO [US] [CSA-68] Handle cases that CT cannot secure
                throw new CsaInvalidDataException("Maximum CT value cannot secure this case");
            } else {
                LOGGER.info("Best case in unsecure, worst case is secure, trying to find optimum in between using dichotomy");
                Index index = new Index(0, ctPtEsUpperBound, 0, ctFrEsUpperBound, 10, 15);
                index.addPtEsDichotomyStepResult(0, noCtStepResult);
                index.addFrEsDichotomyStepResult(0, noCtStepResult);
                index.addPtEsDichotomyStepResult(ctPtEsUpperBound, maxCtStepResult);
                index.addFrEsDichotomyStepResult(ctFrEsUpperBound, maxCtStepResult);

                while (index.exitConditionIsNotMetForPtEs() || index.exitConditionIsNotMetForFrEs()) {
                    CounterTradingValues counterTradingValues = index.nextValues();
                    DichotomyStepResult ctStepResult;
                    String newVariantName = getNewVariantName(counterTradingValues, initialVariant);

                    try {
                        LOGGER.info("Next CT values are '{}' for PT-ES and '{}' for FR-ES", counterTradingValues.getPtEsCt(), counterTradingValues.getFrEsCt());

                        setWorkingVariant(network, initialVariant, newVariantName);
                        networkShifter.shiftNetwork(counterTradingValues, network);
                        ctStepResult = sweCsaRaoValidator.validateNetwork(network, crac, csaRequest, raoParametersUrl, true, true);
                        resetToInitialVariant(network, initialVariant, newVariantName);

                    } catch (GlskLimitationException e) {
                        LOGGER.warn("GLSK limits have been reached with CT of '{}' for PT-ES and '{}' for FR-ES", counterTradingValues.getPtEsCt(), counterTradingValues.getFrEsCt());
                        ctStepResult = DichotomyStepResult.fromFailure(ReasonInvalid.GLSK_LIMITATION, e.getMessage(), true, true);
                    } catch (ShiftingException | RaoRunnerException e) {
                        LOGGER.warn("Validation failed with CT of '{}' for PT-ES and '{}' for FR-ES", counterTradingValues.getPtEsCt(), counterTradingValues.getFrEsCt());
                        ctStepResult = DichotomyStepResult.fromFailure(ReasonInvalid.GLSK_LIMITATION, e.getMessage(), true, true);
                    }
                    logBorderOverload(ctStepResult);
                    index.addPtEsDichotomyStepResult(counterTradingValues.getPtEsCt(), ctStepResult);
                    index.addFrEsDichotomyStepResult(counterTradingValues.getFrEsCt(), ctStepResult);
                }
                LOGGER.info("Dichotomy stop criterion reached", index.getFrEsLowestSecureStep().getLeft());
                return index.getFrEsLowestSecureStep().getRight().getRaoResult();
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
            LOGGER.info("There is overloads on FR-ES border, netowrk is not secure");
        }

        if (ctStepResult.isPtEsCnecsSecure()) {
            LOGGER.info("There is no overload on PT-ES border");
        } else {
            LOGGER.info("There is overloads on PT-ES border, netowrk is not secure");
        }
    }

    private String getNewVariantName(CounterTradingValues counterTradingValues, String initialVariant) {
        return String.format("%s-ScaledBy-%s", initialVariant, counterTradingValues.print());
    }
}
