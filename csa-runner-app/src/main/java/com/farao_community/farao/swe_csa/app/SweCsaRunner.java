package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.data.crac_api.Crac;
import com.farao_community.farao.rao_runner.api.resource.RaoRequest;
import com.farao_community.farao.rao_runner.api.resource.RaoResponse;
import com.farao_community.farao.rao_runner.starter.RaoRunnerClient;
import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
import com.farao_community.farao.swe_csa.api.resource.CsaResponse;
import com.farao_community.farao.swe_csa.api.resource.Status;
import com.powsybl.iidm.network.Network;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
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
    public CsaResponse run(CsaRequest csaRequest) throws IOException {
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

}
