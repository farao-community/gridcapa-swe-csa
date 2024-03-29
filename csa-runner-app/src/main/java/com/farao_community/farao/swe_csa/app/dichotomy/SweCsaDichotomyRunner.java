package com.farao_community.farao.swe_csa.app.dichotomy;

/*
 * Copyright (c) 2024, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

/**
 * @author Jean-Pierre Arnould {@literal <jean-pierre.arnould at rte-france.com>}
 */

import com.farao_community.farao.swe_csa.app.FileImporter;
import com.powsybl.openrao.commons.EICode;
import com.powsybl.openrao.data.cracapi.cnec.Cnec;
import com.powsybl.openrao.data.cracapi.Crac;
import com.powsybl.openrao.data.cracapi.rangeaction.CounterTradeRangeAction;

import com.farao_community.farao.gridcapa_swe_commons.shift.CountryBalanceComputation;
import com.farao_community.farao.rao_runner.api.resource.RaoRequest;
import com.farao_community.farao.rao_runner.api.resource.RaoResponse;
import com.farao_community.farao.rao_runner.starter.RaoRunnerClient;

import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
import com.farao_community.farao.swe_csa.api.resource.CsaResponse;
import com.farao_community.farao.swe_csa.api.resource.Status;
import com.farao_community.farao.swe_csa.app.Threadable;
import com.farao_community.farao.swe_csa.app.dichotomy.dispatcher.SweCsaShiftDispatcher;
import com.farao_community.farao.swe_csa.app.dichotomy.index.Index;
import com.farao_community.farao.swe_csa.app.dichotomy.index.SweCsaHalfRangeDivisionIndexStrategy;
import com.farao_community.farao.swe_csa.app.dichotomy.shifter.SweCsaNetworkShifter;
import com.farao_community.farao.swe_csa.app.dichotomy.variable.MultipleDichotomyVariables;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SweCsaDichotomyRunner {

    private final RaoRunnerClient raoRunnerClient;
    private final FileImporter fileImporter;
    private static final Logger LOGGER = LoggerFactory.getLogger(SweCsaDichotomyRunner.class);

    @Value("${rao-parameters.index.precision}")
    private Double indexPrecision;

    public SweCsaDichotomyRunner(RaoRunnerClient raoRunnerClient, FileImporter fileImporter) {
        this.raoRunnerClient = raoRunnerClient;
        this.fileImporter = fileImporter;
    }

    @Threadable
    public CsaResponse runRaoDichotomy(CsaRequest csaRequest) throws IOException {
        RaoResponse raoResponse = null;
        try {
            String requestId = csaRequest.getId();
            LOGGER.info("Csa request received : {}", csaRequest);
            Instant utcInstant = Instant.parse(csaRequest.getBusinessTimestamp());
            String raoParametersUrl = fileImporter.uploadRaoParameters(requestId, utcInstant);
            Crac crac = fileImporter.importCrac(csaRequest.getCracFileUri());
            Network network = fileImporter.importNetwork(csaRequest.getGridModelUri());
            RaoRequest raoRequest = new RaoRequest.RaoRequestBuilder()
                .withId(requestId)
                .withNetworkFileUrl(csaRequest.getGridModelUri())
                .withCracFileUrl(csaRequest.getCracFileUri())
                .withRaoParametersFileUrl(raoParametersUrl)
                .withResultsDestination(csaRequest.getResultsUri())
                .build();

            SweCsaHalfRangeDivisionIndexStrategy indexStrategy = new SweCsaHalfRangeDivisionIndexStrategy(crac, network);
            SweCsaRaoValidator validator = new SweCsaRaoValidator(raoRunnerClient, requestId, csaRequest.getGridModelUri(), csaRequest.getCracFileUri(), crac, raoParametersUrl, this.getCnecsIdLists(indexStrategy));
            RaoResponse raoResponseAfterDichotomy = getDichotomyResponse(network, crac, validator, indexStrategy);
            LOGGER.info("dichotomy RAO computation answer received for TimeStamp: '{}'", raoRequest.getInstant());

            return new CsaResponse(raoResponseAfterDichotomy.getId(), Status.FINISHED.toString());

        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    protected RaoResponse getDichotomyResponse(Network network, Crac crac, SweCsaRaoValidator validator, SweCsaHalfRangeDivisionIndexStrategy indexStrategy) {
        DichotomyResult<RaoResponse, MultipleDichotomyVariables> result = this.getEngine(network, crac, validator, indexStrategy).run(network);
        return result.getHighestValidStep().getValidationData();
    }

    protected SweCsaDichotomyEngine getEngine(Network network, Crac crac, SweCsaRaoValidator validator, SweCsaHalfRangeDivisionIndexStrategy indexStrategy) {
        this.updateCracWithCounterTrageRangeActions(crac);
        Map<String, Double> initialCountriesPositions = CountryBalanceComputation.computeSweCountriesBalances(network);
        Pair<MultipleDichotomyVariables, MultipleDichotomyVariables> initialDichotomyVariable = getInitialDichotomyIndex(crac, initialCountriesPositions);

        return new SweCsaDichotomyEngine(
            new Index<>(initialDichotomyVariable.getLeft(), initialDichotomyVariable.getRight(), indexPrecision),
            indexStrategy,
            new SweCsaNetworkShifter(SweCsaZonalData.getZonalData(network), new SweCsaShiftDispatcher(getInitialEicCodeToCountriesPositions(initialCountriesPositions))),
            validator);
    }

    private Map<String, Double> getInitialEicCodeToCountriesPositions(Map<String, Double> initialEicCodePositions) {
        Map<String, Double> initialCountriesPositions = new HashMap<>();
        initialCountriesPositions.put(Country.FR.getName(), initialEicCodePositions.getOrDefault((new EICode(Country.FR)).getAreaCode(), 0.0));
        initialCountriesPositions.put(Country.PT.getName(), initialEicCodePositions.getOrDefault((new EICode(Country.PT)).getAreaCode(), 0.0));
        initialCountriesPositions.put(Country.ES.getName(), initialEicCodePositions.getOrDefault((new EICode(Country.ES)).getAreaCode(), 0.0));
        return initialCountriesPositions;
    }

    private Pair<MultipleDichotomyVariables, MultipleDichotomyVariables> getInitialDichotomyIndex(Crac crac, Map<String, Double> initialCountriesPositions) {
        CounterTradeRangeAction ctRaFrEs = this.getCounterTradeRangeActionByCountries(crac, Country.FR, Country.ES);
        CounterTradeRangeAction ctRaEsFr = this.getCounterTradeRangeActionByCountries(crac, Country.ES, Country.FR);
        CounterTradeRangeAction ctRaPtEs = this.getCounterTradeRangeActionByCountries(crac, Country.PT, Country.ES);
        CounterTradeRangeAction ctRaEsPt = this.getCounterTradeRangeActionByCountries(crac, Country.ES, Country.PT);

        double expFrEs = initialCountriesPositions.getOrDefault((new EICode(Country.FR)).getAreaCode(), 0.0);
        double expPtEs = initialCountriesPositions.getOrDefault((new EICode(Country.PT)).getAreaCode(), 0.0);
        double expEsFr = -expFrEs;
        double expEsPt = -expPtEs;

        double raFrEsMinAdmFrEs = ctRaFrEs == null ? 0.0 : ctRaFrEs.getMinAdmissibleSetpoint(expFrEs);
        double raFrEsMaxAdmFrEs = ctRaFrEs == null ? 0.0 : ctRaFrEs.getMaxAdmissibleSetpoint(expFrEs);
        double raEsFrMaxAdmEsFr = ctRaEsFr == null ? 0.0 : ctRaEsFr.getMaxAdmissibleSetpoint(expEsFr);
        double raEsFrMinAdmEsFr = ctRaEsFr == null ? 0.0 : ctRaEsFr.getMinAdmissibleSetpoint(expEsFr);

        double raPtEsMinAdmPtEs = ctRaPtEs == null ? 0.0 : ctRaPtEs.getMinAdmissibleSetpoint(expPtEs);
        double raPtEsMaxAdmPtEs = ctRaPtEs == null ? 0.0 : ctRaPtEs.getMaxAdmissibleSetpoint(expPtEs);
        double raEsPtMaxAdmEsPt = ctRaEsPt == null ? 0.0 : ctRaEsPt.getMaxAdmissibleSetpoint(expEsPt);
        double raEsPtMinAdmEsPt = ctRaEsPt == null ? 0.0 : ctRaEsPt.getMinAdmissibleSetpoint(expEsPt);

        double ctFrEsMax = expFrEs >= 0 ? Math.min(Math.min(-raFrEsMinAdmFrEs, raEsFrMaxAdmEsFr), expFrEs)
            : Math.min(Math.min(raFrEsMaxAdmFrEs, -raEsFrMinAdmEsFr), -expFrEs);
        double ctPtEsMax = expPtEs >= 0 ? Math.min(Math.min(-raPtEsMinAdmPtEs, raEsPtMaxAdmEsPt), expPtEs)
            : Math.min(Math.min(raPtEsMaxAdmPtEs, -raEsPtMinAdmEsPt), -expPtEs);

        MultipleDichotomyVariables initMinIndex = new MultipleDichotomyVariables(Map.of(CounterTradingDirection.FR_ES.getName(), 0.0, CounterTradingDirection.PT_ES.getName(), 0.0));
        MultipleDichotomyVariables initMaxIndex = new MultipleDichotomyVariables(Map.of(CounterTradingDirection.FR_ES.getName(), ctFrEsMax, CounterTradingDirection.PT_ES.getName(), ctPtEsMax));

        return Pair.of(initMinIndex, initMaxIndex);
    }

    private CounterTradeRangeAction getCounterTradeRangeActionByCountries(Crac crac, Country exportingCountry, Country importingCountry) {
        for (CounterTradeRangeAction counterTradeRangeAction : crac.getCounterTradeRangeActions()) {
            if (counterTradeRangeAction.getExportingCountry() == exportingCountry && counterTradeRangeAction.getImportingCountry() == importingCountry) {
                return counterTradeRangeAction;
            }
        }
        return null;
    }

    private Pair<List<String>, List<String>> getCnecsIdLists(SweCsaHalfRangeDivisionIndexStrategy indexStrategy) {
        List<String> frEsCnecsIds = indexStrategy.getFrEsFlowCnecs().stream().map(Cnec::getId).collect(Collectors.toList());
        frEsCnecsIds.addAll(indexStrategy.getFrEsAngleCnecs().stream().map(Cnec::getId).collect(Collectors.toList()));
        List<String> ptEsCnecsIds = indexStrategy.getPtEsFlowCnecs().stream().map(Cnec::getId).collect(Collectors.toList());
        ptEsCnecsIds.addAll(indexStrategy.getPtEsAngleCnecs().stream().map(Cnec::getId).collect(Collectors.toList()));
        return Pair.of(frEsCnecsIds, ptEsCnecsIds);
    }

    //TODO : countertrade range actions should be integrated in the CSA profiles input files
    private void updateCracWithCounterTrageRangeActions(Crac crac) {
        crac.newCounterTradeRangeAction()
            .withId(CounterTradeRangeActionDirection.PT_ES.getName())
            .withOperator("REN")
            .newRange().withMin(-5000.0)
                .withMax(5000.0).add()
            .withInitialSetpoint(0.0)
            .withExportingCountry(Country.PT)
            .withImportingCountry(Country.ES)
            .add();
        crac.newCounterTradeRangeAction()
            .withId(CounterTradeRangeActionDirection.ES_PT.getName())
            .withOperator("REE")
            .newRange().withMin(-5000.0)
            .withMax(5000.0).add()
            .withInitialSetpoint(0.0)
            .withExportingCountry(Country.ES)
            .withImportingCountry(Country.PT)
            .add();
        crac.newCounterTradeRangeAction()
            .withId(CounterTradeRangeActionDirection.ES_FR.getName())
            .withOperator("REE")
            .newRange().withMin(-5000.0)
            .withMax(5000.0).add()
            .withInitialSetpoint(0.0)
            .withExportingCountry(Country.ES)
            .withImportingCountry(Country.FR)
            .add();
        crac.newCounterTradeRangeAction()
            .withId(CounterTradeRangeActionDirection.FR_ES.getName())
            .withOperator("RTE")
            .newRange().withMin(-5000.0)
            .withMax(5000.0).add()
            .withInitialSetpoint(0.0)
            .withExportingCountry(Country.FR)
            .withImportingCountry(Country.ES)
            .add();
    }

}
