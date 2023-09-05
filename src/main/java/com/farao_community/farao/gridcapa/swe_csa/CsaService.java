package com.farao_community.farao.gridcapa.swe_csa;

import com.powsybl.iidm.network.*;

import com.rte_france.farao.rao_runner.api.resource.RaoRequest;
import com.rte_france.farao.rao_runner.starter.AsynchronousRaoRunnerClient;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class CsaService {
    private static final DateTimeFormatter HOURLY_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'_'HHmm").withZone(ZoneId.of("UTC"));

    private final AsynchronousRaoRunnerClient asynchronousRaoRunnerClient;
    private final MinioAdapter minioAdapter;

    public CsaService(AsynchronousRaoRunnerClient asynchronousRaoRunnerClient, MinioAdapter minioAdapter) {
        this.asynchronousRaoRunnerClient = asynchronousRaoRunnerClient;
        this.minioAdapter = minioAdapter;
    }

    public ResponseEntity runRao(MultipartFile inputFilesArchive, Instant utcInstant) throws IOException {
        // create a deployment with rao runner, minio and rabbitmq

        // TODO import zip  to have a network and crac , as in unit tests
        //Network network = importNetwork(inputFilesArchive);
        // Crac crac = importCrac(inputFilesArchive);


        // TODO upload crac and network in minio (minio launched locally with docker compose)
        Network network = null;
        String taskId = UUID.randomUUID().toString();
        String networkFileUrl = uploadIidmNetworkToMinio(taskId, network, utcInstant);
        // String cracFileUrl = uploadCracToMinio(destination, network, fileName, utcInstant);

        // create a rao request network and crac links
        RaoRequest raoRequest = new RaoRequest(taskId, networkFileUrl, "cracFileUrl");
        asynchronousRaoRunnerClient.runRaoAsynchronously(raoRequest);
        // call RAO.run
        return ResponseEntity.accepted().build();
    }
//
    private String uploadIidmNetworkToMinio(String taskId, Network network, Instant utcInstant) throws IOException {
        Path iidmTmpPath = new File("/tmp", "network").toPath();
        network.write("XIIDM", null, iidmTmpPath);
        String iidmNetworkDestinationPath = String.format("%s/inputs/networks/%s", taskId, HOURLY_NAME_FORMATTER.format(utcInstant).concat(".xiidm"));
        try (FileInputStream iidmNetworkInputStream = new FileInputStream(iidmTmpPath.toString())) {
            minioAdapter.uploadFile(iidmNetworkDestinationPath, iidmNetworkInputStream);
        }
        return minioAdapter.generatePreSignedUrl(iidmNetworkDestinationPath);
    }
}
