package com.farao_community.farao.swe_csa.app.dichotomy;

import com.farao_community.farao.dichotomy.api.exceptions.GlskLimitationException;
import com.farao_community.farao.dichotomy.api.exceptions.ShiftingException;
import com.farao_community.farao.dichotomy.api.results.DichotomyStepResult;
import com.farao_community.farao.gridcapa_swe_commons.shift.CountryBalanceComputation;
import com.farao_community.farao.rao_runner.api.resource.RaoResponse;
import com.farao_community.farao.swe_csa.api.exception.CsaInvalidDataException;
import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
import com.farao_community.farao.swe_csa.app.FileImporter;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
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

@Service
public class DichotomyRunner {

    @Value("${dichotomy-parameters.index.precision}")
    private Double indexPrecision;
    @Value("${dichotomy-parameters.index.max-iterations-by-border}")
    private Double maxDichotomiesByBorder;
    private final SweCsaRaoValidator sweCsaRaoValidator;
    private final FileImporter fileImporter;
    private static final Logger LOGGER = LoggerFactory.getLogger(DichotomyRunner.class);

    public DichotomyRunner(SweCsaRaoValidator sweCsaRaoValidator, FileImporter fileImporter) {
        this.sweCsaRaoValidator = sweCsaRaoValidator;
        this.fileImporter = fileImporter;
    }

    public RaoResult runDichotomy(CsaRequest csaRequest) throws GlskLimitationException, ShiftingException {
        // run rao on best case CT=0 and set min CT values to 0
        String raoParametersUrl = fileImporter.uploadRaoParameters(csaRequest.getId(), Instant.parse(csaRequest.getBusinessTimestamp()));
        Network network = fileImporter.importNetwork(csaRequest.getGridModelUri());
        Crac crac = fileImporter.importCrac(csaRequest.getCracFileUri());

        Map<Country, Double> initialNetPositions = CountryBalanceComputation.computeSweCountriesBalances(network, LoadFlowParameters.load())
            .entrySet().stream()
            .collect(Collectors.toMap(entry -> Country.valueOf(entry.getKey()), Map.Entry::getValue));

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
            return sweCsaRaoValidator.validateNetwork(network, csaRequest, raoParametersUrl, true, true).getRaoResult();
        }
        // best case no counter trading , no scaling
        DichotomyStepResult<RaoResponse> noCtStepResult = sweCsaRaoValidator.validateNetwork(network, csaRequest, raoParametersUrl, true, true);

        if (noCtStepResult.isValid()) {
            return noCtStepResult.getRaoResult();
        } else {
            // initial network not secure, try worst case maximum counter trading
            double ctFrEsMax = expFrEs0 >= 0 ? Math.min(Math.min(-ctRaFrEs.getMinAdmissibleSetpoint(expFrEs0), ctRaEsFr.getMaxAdmissibleSetpoint(expEsFr0)), expFrEs0)
                : Math.min(Math.min(ctRaFrEs.getMaxAdmissibleSetpoint(expFrEs0), -ctRaEsFr.getMinAdmissibleSetpoint(expEsFr0)), -expFrEs0);

            double ctPtEsMax = expPtEs0 >= 0 ? Math.min(Math.min(-ctRaPtEs.getMinAdmissibleSetpoint(expPtEs0), ctRaEsPt.getMaxAdmissibleSetpoint(expEsPt0)), expPtEs0)
                : Math.min(Math.min(ctRaPtEs.getMaxAdmissibleSetpoint(expPtEs0), -ctRaEsPt.getMinAdmissibleSetpoint(expEsPt0)), -expPtEs0);

            SweCsaNetworkShifter networkShifter = new SweCsaNetworkShifter(SweCsaZonalData.getZonalData(network), new ShiftDispatcher(initialNetPositions));
            CounterTradingValues maxCounterTradingValues = new CounterTradingValues(ctPtEsMax, ctFrEsMax);
            networkShifter.shiftNetwork(maxCounterTradingValues, network);
            DichotomyStepResult<RaoResponse> maxCtStepResult = sweCsaRaoValidator.validateNetwork(network, csaRequest, raoParametersUrl, true, true);
            if (!maxCtStepResult.isValid()) {
                // TODO [US] [CSA-68] Handle cases that CT cannot secure
                throw new CsaInvalidDataException("Maximum CT value cannot secure this case");
            } else {
                // initial network not secure, and worst case with max CT is secure --> try to find optimum in between
                Index index = new Index(0, ctPtEsMax, 0, ctFrEsMax, indexPrecision, maxDichotomiesByBorder);
                index.addFrEsDichotomyStepResult(0, noCtStepResult);
                index.addPtEsDichotomyStepResult(0, noCtStepResult);
                index.addFrEsDichotomyStepResult(ctFrEsMax, maxCtStepResult);
                index.addPtEsDichotomyStepResult(ctPtEsMax, maxCtStepResult);
                while (index.exitConditionIsNotMetForPtEs() || index.exitConditionIsNotMetForFrEs()) {
                    CounterTradingValues counterTradingValues = index.nextValues();
                    networkShifter.shiftNetwork(counterTradingValues, network);
                    DichotomyStepResult<RaoResponse> ctStepResult = sweCsaRaoValidator.validateNetwork(network, csaRequest, raoParametersUrl, true, true);
                    index.addFrEsDichotomyStepResult(counterTradingValues.getFrEsCt(), ctStepResult);
                    index.addPtEsDichotomyStepResult(counterTradingValues.getPtEsCt(), ctStepResult);
                }
                return index.getFrEsLowestSecureStep().getRight().getRaoResult();
            }
        }
    }

    private CounterTradeRangeAction getCounterTradeRangeActionByCountries(Crac crac, Country exportingCountry, Country importingCountry) {
        for (CounterTradeRangeAction counterTradeRangeAction : crac.getCounterTradeRangeActions()) {
            if (counterTradeRangeAction.getExportingCountry() == exportingCountry && counterTradeRangeAction.getImportingCountry() == importingCountry) {
                return counterTradeRangeAction;
            }
        }
        throw new CsaInvalidDataException(String.format("Crac should contain 4 counter trading remedial actions for csa swe process, Two CT RAs by border, and couldn't find CT RA for '%s' as exporting country and '%s' as importing country", exportingCountry.getName(), importingCountry.getName()));
    }
}
