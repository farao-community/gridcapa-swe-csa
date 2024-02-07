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

import com.farao_community.farao.commons.EICode;
import com.farao_community.farao.data.crac_api.Crac;
import com.farao_community.farao.data.crac_api.range_action.CounterTradeRangeAction;
import com.farao_community.farao.rao_runner.api.resource.RaoRequest;
import com.farao_community.farao.rao_runner.api.resource.RaoResponse;
import com.farao_community.farao.rao_runner.starter.RaoRunnerClient;
import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
import com.farao_community.farao.swe_csa.api.resource.CsaResponse;
import com.farao_community.farao.swe_csa.api.resource.Status;
import com.farao_community.farao.swe_csa.app.FileHelper;
import com.farao_community.farao.swe_csa.app.SweCsaRunner;
import com.farao_community.farao.swe_csa.app.Threadable;
import com.farao_community.farao.swe_csa.app.ZipHelper;
import com.farao_community.farao.swe_csa.app.dichotomy.dispatcher.SweCsaShiftDispatcher;
import com.farao_community.farao.swe_csa.app.dichotomy.index.Index;
import com.farao_community.farao.swe_csa.app.dichotomy.index.SweCsaHalfRangeDivisionIndexStrategy;
import com.farao_community.farao.swe_csa.app.dichotomy.shifter.LinearScaler;
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
import java.util.Map;
import java.util.Set;

@Service
public class SweCsaDichotomyRunner {

    private final RaoRunnerClient raoRunnerClient;
    private final FileHelper fileHelper;
    private static final Logger LOGGER = LoggerFactory.getLogger(SweCsaRunner.class);

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

        String timestamp = csaRequest.getBusinessTimestamp();
        Instant utcInstant = Instant.parse(csaRequest.getBusinessTimestamp());
        ZipHelper.zipDataCsaRequestFiles(csaRequest, archiveTempPath);
        Network network = fileHelper.importNetwork(archiveTempPath);
        Crac crac = fileHelper.importCrac(archiveTempPath, network, utcInstant);
        String networkFileUrl = fileHelper.uploadIidmNetworkToMinio(requestId, network, utcInstant);
        String cracFileUrl = fileHelper.uploadJsonCrac(requestId, crac, utcInstant);
        String raoParametersUrl = fileHelper.uploadRaoParameters(requestId, utcInstant);
        RaoRequest raoRequest = new RaoRequest(requestId, networkFileUrl, cracFileUrl, raoParametersUrl);
        SweCsaRaoValidator validator = new SweCsaRaoValidator(raoRunnerClient, requestId, networkFileUrl, cracFileUrl, crac, raoParametersUrl);

        RaoResponse raoResponseAfterDichotomy = getDichotomyResponse(network, crac, timestamp, validator);
        LOGGER.info("dichotomy RAO computation answer received for TimeStamp: '{}'", raoRequest.getInstant());

        return new CsaResponse(raoResponseAfterDichotomy.getId(), Status.FINISHED.toString());
    }

    protected RaoResponse getDichotomyResponse(Network network, Crac crac, String timestamp, SweCsaRaoValidator validator) {

        Pair<MultipleDichotomyVariables, MultipleDichotomyVariables> initialDichotomyVariable = getInitialDichotomyIndex(crac);
        DichotomyEngine<RaoResponse, MultipleDichotomyVariables> engine = new DichotomyEngine<>(
            new Index<>(initialDichotomyVariable.getLeft(), initialDichotomyVariable.getRight(), 10),
            new SweCsaHalfRangeDivisionIndexStrategy("CT_FRES", "CT_PTES"),
            new LinearScaler(SweCsaZonalData.getZonalData(network), new SweCsaShiftDispatcher(getInitialPositions(crac))),
            validator);
        DichotomyResult<RaoResponse, MultipleDichotomyVariables> result = engine.run(network);
        return result.getHighestValidStep().getValidationData();
    }

    protected Map<String, Double>  getInitialPositions(Crac crac) {
        CounterTradeRangeAction ctRaFrEs = crac.getCounterTradeRangeAction("CT_RA_FRES");
        CounterTradeRangeAction ctRaPtEs = crac.getCounterTradeRangeAction("CT_RA_PTES");

        double expFrEs = ctRaFrEs.getInitialSetpoint();
        double expPtEs = ctRaPtEs.getInitialSetpoint();

        return Map.of(
            new EICode(Country.ES).getAreaCode(), expFrEs + expPtEs,
            new EICode(Country.FR).getAreaCode(), -expFrEs,
            new EICode(Country.PT).getAreaCode(), -expPtEs);
    }

    private Pair<MultipleDichotomyVariables, MultipleDichotomyVariables> getInitialDichotomyIndex(Crac crac) {
        CounterTradeRangeAction ctRaFrEs = crac.getCounterTradeRangeAction("CT_RA_FRES");
        CounterTradeRangeAction ctRaEsFr = crac.getCounterTradeRangeAction("CT_RA_ESFR");
        CounterTradeRangeAction ctRaPtEs = crac.getCounterTradeRangeAction("CT_RA_PTES");
        CounterTradeRangeAction ctRaEsPt = crac.getCounterTradeRangeAction("CT_RA_ESPT");

        double expFrEs = ctRaFrEs.getInitialSetpoint();
        double expPtEs = ctRaPtEs.getInitialSetpoint();
        double expEsFr = ctRaEsFr.getInitialSetpoint();
        double expEsPt = ctRaEsPt.getInitialSetpoint();

        double ctFrEsMax = expFrEs >= 0 ? Math.min(Math.min(-ctRaFrEs.getMinAdmissibleSetpoint(expFrEs), ctRaEsFr.getMaxAdmissibleSetpoint(expEsFr)), expFrEs)
            : Math.min(Math.min(ctRaFrEs.getMaxAdmissibleSetpoint(expFrEs), -ctRaEsFr.getMinAdmissibleSetpoint(expEsFr)), -expFrEs);
        double ctPtEsMax = expPtEs >= 0 ? Math.min(Math.min(-ctRaPtEs.getMinAdmissibleSetpoint(expPtEs), ctRaEsPt.getMaxAdmissibleSetpoint(expEsPt)), expPtEs)
            : Math.min(Math.min(ctRaPtEs.getMaxAdmissibleSetpoint(expPtEs), -ctRaEsPt.getMinAdmissibleSetpoint(expEsPt)), -expPtEs);

        MultipleDichotomyVariables initMinIndex = new MultipleDichotomyVariables(Map.of("CT_FRES", 0.0, "CT_PTES", 0.0));
        MultipleDichotomyVariables initMaxIndex = new MultipleDichotomyVariables(Map.of("CT_FRES", ctFrEsMax, "CT_PTES", ctPtEsMax));

        return Pair.of(initMinIndex, initMaxIndex);
    }

}
