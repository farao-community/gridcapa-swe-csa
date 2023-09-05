package com.farao_community.farao.gridcapa.swe_csa;

import com.farao_community.farao.data.crac_io_api.CracExporters;
import com.powsybl.iidm.network.*;
import com.farao_community.farao.data.crac_api.*;

import com.rte_france.farao.rao_runner.api.resource.RaoRequest;
import com.rte_france.farao.rao_runner.starter.AsynchronousRaoRunnerClient;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
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
        // TODO import zip  to have a network and crac , as in unit tests
        //Network network = importNetwork(inputFilesArchive);
        // Crac crac = importCrac(inputFilesArchive);

        Network network = null;
        Crac crac = null;
        String taskId = UUID.randomUUID().toString();
        String networkFileUrl = uploadIidmNetworkToMinio(taskId, network, utcInstant);
        String cracFileUrl = uploadJsonCrac(taskId, crac, utcInstant);

        // create a rao request network and crac links
        RaoRequest raoRequest = new RaoRequest(taskId, networkFileUrl, cracFileUrl);
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

    private String uploadJsonCrac(String taskId, Crac crac, Instant utcInstant) {
            String jsonCracFilePath = String.format("%s/inputs/cracs/%s", taskId, HOURLY_NAME_FORMATTER.format(utcInstant).concat(".json"));
            ByteArrayOutputStream cracByteArrayOutputStream = new ByteArrayOutputStream();
            CracExporters.exportCrac(crac, "Json", cracByteArrayOutputStream);
            try (InputStream is = new ByteArrayInputStream(cracByteArrayOutputStream.toByteArray())) {
                minioAdapter.uploadFile(jsonCracFilePath, is);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        return minioAdapter.generatePreSignedUrl(jsonCracFilePath);
    }
}
