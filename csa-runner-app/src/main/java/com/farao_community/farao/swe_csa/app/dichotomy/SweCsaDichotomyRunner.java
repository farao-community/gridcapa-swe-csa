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
import com.farao_community.farao.swe_csa.app.FileHelper;
import com.farao_community.farao.swe_csa.app.Threadable;
import com.farao_community.farao.swe_csa.app.ZipHelper;
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
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SweCsaDichotomyRunner {

    private final RaoRunnerClient raoRunnerClient;
    private final FileHelper fileHelper;
    private static final Logger LOGGER = LoggerFactory.getLogger(SweCsaDichotomyRunner.class);

    public SweCsaDichotomyRunner(RaoRunnerClient raoRunnerClient, FileHelper fileHelper) {
        this.raoRunnerClient = raoRunnerClient;
        this.fileHelper = fileHelper;
    }

    @Threadable
    public CsaResponse runRaoDichotomy(CsaRequest csaRequest) throws IOException {
        String requestId = csaRequest.getId();
        LOGGER.info("Csa request received : {}", csaRequest);
        FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));
        Path archiveTempPath = Files.createTempFile("csa-temp-inputs", "inputs.zip", attr);

        Instant utcInstant = Instant.parse(csaRequest.getBusinessTimestamp());
        ZipHelper.zipDataCsaRequestFiles(csaRequest, archiveTempPath);
        Network network = fileHelper.importNetwork(archiveTempPath);
        Crac crac = fileHelper.importCrac(archiveTempPath, network, utcInstant);
        String networkFileUrl = fileHelper.uploadIidmNetworkToMinio(requestId, network, utcInstant);
        String cracFileUrl = fileHelper.uploadJsonCrac(requestId, crac, utcInstant);
        String raoParametersUrl = fileHelper.uploadRaoParameters(requestId, utcInstant);

        RaoRequest raoRequest = new RaoRequest(requestId, networkFileUrl, cracFileUrl, raoParametersUrl);
        SweCsaHalfRangeDivisionIndexStrategy indexStrategy = new SweCsaHalfRangeDivisionIndexStrategy(crac, network);
        SweCsaRaoValidator validator = new SweCsaRaoValidator(raoRunnerClient, requestId, networkFileUrl, cracFileUrl, crac, raoParametersUrl, this.getCnecsIdLists(indexStrategy));
        RaoResponse raoResponseAfterDichotomy = getDichotomyResponse(network, crac, validator, indexStrategy);
        LOGGER.info("dichotomy RAO computation answer received for TimeStamp: '{}'", raoRequest.getInstant());

        return new CsaResponse(raoResponseAfterDichotomy.getId(), Status.FINISHED.toString());
    }

    protected RaoResponse getDichotomyResponse(Network network, Crac crac, SweCsaRaoValidator validator, SweCsaHalfRangeDivisionIndexStrategy indexStrategy) {
        DichotomyResult<RaoResponse, MultipleDichotomyVariables> result = this.getEngine(network, crac, validator, indexStrategy).run(network);
        return result.getHighestValidStep().getValidationData();
    }

    protected SweCsaDichotomyEngine getEngine(Network network, Crac crac, SweCsaRaoValidator validator, SweCsaHalfRangeDivisionIndexStrategy indexStrategy) {
        this.updateCracWithCounterTrageRangeActions(crac);
        Map<String, Double> initialBordersPositions = CountryBalanceComputation.computeSweBordersExchanges(network);
        Pair<MultipleDichotomyVariables, MultipleDichotomyVariables> initialDichotomyVariable = getInitialDichotomyIndex(crac, initialBordersPositions);

        return new SweCsaDichotomyEngine(
            new Index<>(initialDichotomyVariable.getLeft(), initialDichotomyVariable.getRight(), 10),
            indexStrategy,
            new SweCsaNetworkShifter(SweCsaZonalData.getZonalData(network), new SweCsaShiftDispatcher(getInitialCountriesPositions(initialBordersPositions))),
            validator);
    }

    private Map<String, Double> getInitialCountriesPositions(Map<String, Double> initialBordersPositions) {
        Map<String, Double> initialCountriesPositions = new HashMap<>();
        initialCountriesPositions.put(Country.FR.getName(), -initialBordersPositions.get("ES-FR"));
        initialCountriesPositions.put(Country.PT.getName(), -initialBordersPositions.get("ES-PT"));
        initialCountriesPositions.put(Country.ES.getName(), initialBordersPositions.get("ES-FR") + initialBordersPositions.get("ES-PT"));
        return initialCountriesPositions;
    }

    private Pair<MultipleDichotomyVariables, MultipleDichotomyVariables> getInitialDichotomyIndex(Crac crac, Map<String, Double> initialBordersPositions) {
        CounterTradeRangeAction ctRaFrEs = this.getCounterTradeRangeActionByCountries(crac, Country.FR, Country.ES);
        CounterTradeRangeAction ctRaEsFr = this.getCounterTradeRangeActionByCountries(crac, Country.ES, Country.FR);
        CounterTradeRangeAction ctRaPtEs = this.getCounterTradeRangeActionByCountries(crac, Country.PT, Country.ES);
        CounterTradeRangeAction ctRaEsPt = this.getCounterTradeRangeActionByCountries(crac, Country.ES, Country.PT);

        double expEsFr = initialBordersPositions.get("ES-FR");
        double expEsPt = initialBordersPositions.get("ES-PT");
        double expFrEs = -expEsFr;
        double expPtEs = -expEsPt;

        double ctFrEsMax = expFrEs >= 0 ? Math.min(Math.min(-ctRaFrEs.getMinAdmissibleSetpoint(expFrEs), ctRaEsFr.getMaxAdmissibleSetpoint(expEsFr)), expFrEs)
            : Math.min(Math.min(ctRaFrEs.getMaxAdmissibleSetpoint(expFrEs), -ctRaEsFr.getMinAdmissibleSetpoint(expEsFr)), -expFrEs);
        double ctPtEsMax = expPtEs >= 0 ? Math.min(Math.min(-ctRaPtEs.getMinAdmissibleSetpoint(expPtEs), ctRaEsPt.getMaxAdmissibleSetpoint(expEsPt)), expPtEs)
            : Math.min(Math.min(ctRaPtEs.getMaxAdmissibleSetpoint(expPtEs), -ctRaEsPt.getMinAdmissibleSetpoint(expEsPt)), -expPtEs);

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
            .newRange().withMin(-2000.0)
                .withMax(3000.0).add()
            .withInitialSetpoint(0.0)
            .withExportingCountry(Country.PT)
            .withImportingCountry(Country.ES)
            .add();
        crac.newCounterTradeRangeAction()
            .withId(CounterTradeRangeActionDirection.ES_PT.getName())
            .withOperator("REE")
            .newRange().withMin(-2100.0)
            .withMax(3100.0).add()
            .withInitialSetpoint(0.0)
            .withExportingCountry(Country.ES)
            .withImportingCountry(Country.PT)
            .add();
        crac.newCounterTradeRangeAction()
            .withId(CounterTradeRangeActionDirection.ES_FR.getName())
            .withOperator("REE")
            .newRange().withMin(-2200.0)
            .withMax(3200.0).add()
            .withInitialSetpoint(0.0)
            .withExportingCountry(Country.ES)
            .withImportingCountry(Country.FR)
            .add();
        crac.newCounterTradeRangeAction()
            .withId(CounterTradeRangeActionDirection.FR_ES.getName())
            .withOperator("RTE")
            .newRange().withMin(-2300.0)
            .withMax(3300.0).add()
            .withInitialSetpoint(0.0)
            .withExportingCountry(Country.FR)
            .withImportingCountry(Country.ES)
            .add();
    }

}
