package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.data.crac_api.Crac;
import com.farao_community.farao.data.crac_io_json.JsonImport;
import com.farao_community.farao.data.rao_result_api.RaoResult;
import com.farao_community.farao.data.rao_result_json.RaoResultImporter;
import com.farao_community.farao.dichotomy.shift.LinearScaler;
import com.farao_community.farao.dichotomy.shift.SplittingFactors;
import com.farao_community.farao.rao_runner.api.resource.RaoRequest;
import com.farao_community.farao.rao_runner.api.resource.RaoResponse;
import com.farao_community.farao.rao_runner.starter.RaoRunnerClient;
import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
import com.farao_community.farao.swe_csa.api.resource.CsaResponse;
import com.farao_community.farao.swe_csa.api.resource.Status;
import com.farao_community.farao.swe_csa.app.dichotomy.DichotomyEngine;
import com.farao_community.farao.swe_csa.app.dichotomy.DichotomyResult;
import com.farao_community.farao.swe_csa.app.dichotomy.HalfRangeDivisionIndexStrategy;
import com.farao_community.farao.swe_csa.app.dichotomy.Index;
import com.farao_community.farao.swe_csa.app.dichotomy.MultipleDichotomyVariables;
import com.farao_community.farao.swe_csa.app.dichotomy.NetworkShifter;
import com.farao_community.farao.swe_csa.app.rao_result.RaoResultWithCounterTradeRangeActions;
import com.powsybl.iidm.network.Network;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Service
public class SweCsaRunner {

    private final RaoRunnerClient raoRunnerClient;
    private final FileHelper fileHelper;
    private static final Logger LOGGER = LoggerFactory.getLogger(SweCsaRunner.class);

    public SweCsaRunner(RaoRunnerClient raoRunnerClient, FileHelper fileHelper) {
        this.raoRunnerClient = raoRunnerClient;
        this.fileHelper = fileHelper;
    }

    @Threadable
    public CsaResponse runSingleRao(CsaRequest csaRequest) throws IOException {
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

        RaoResponse raoResponse = raoRunnerClient.runRao(raoRequest);
        LOGGER.info("RAO computation answer received for TimeStamp: '{}'", raoRequest.getInstant());
        return new CsaResponse(raoResponse.getId(), Status.FINISHED.toString());
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
        Glsk
        RaoRequest raoRequest = new RaoRequest(requestId, networkFileUrl, cracFileUrl, raoParametersUrl);

        RaoResponse raoResponse = raoRunnerClient.runRao(raoRequest);
        String raoResultFileUrl = raoResponse.getRaoResultFileUrl();
        LOGGER.info("RAO computation answer received for TimeStamp: '{}'", raoRequest.getInstant());
        return new CsaResponse(raoResponse.getId(), Status.FINISHED.toString());
    }

    private RaoResultWithCounterTradeRangeActions launchDichotomy(Network network, Crac crac, String raoParametersUrl, RaoResponse raoResponse) throws IOException {
        String raoResultFileUrl = raoResponse.getRaoResultFileUrl();
        InputStream raoResultFile = new URL(raoResultFileUrl).openStream();
        RaoResult raoResult = new RaoResultImporter().importRaoResult(raoResultFile, crac);
        Pair<MultipleDichotomyVariables,MultipleDichotomyVariables> initialDichotomyVariable = getInitialDichotomyIndex();

        DichotomyEngine<RaoResponse, MultipleDichotomyVariables> engine = new DichotomyEngine<>(
            new Index<>(initialDichotomyVariable.getLeft(), initialDichotomyVariable.getRight(), 10),
            new HalfRangeDivisionIndexStrategy<MultipleDichotomyVariables>(true),
            new NetworkShifter<>(),
                new SplittingFactors(splittiFactorMap),
                SHIFT_TOLERANCE),
            new MockNetworkValidator());
        DichotomyResult<RaoResponse, MultipleDichotomyVariables> result = engine.run(network);
        return new RaoResultWithCounterTradeRangeActions();
    }

    private Pair<MultipleDichotomyVariables,MultipleDichotomyVariables> getInitialDichotomyIndex() {

    }

    public NetworkShifter get(String glskUrl, Network network, Map<String, Double> referenceExchanges, ZonalScalableProvider zonalScalableProvider) throws IOException {
        return new LinearScaler(
            zonalScalableProvider.get(glskUrl, network, request.getProcessType()),
            getShiftDispatcher(request.getProcessType(), cseData, referenceExchanges),
            SHIFT_TOLERANCE);
    }

}
