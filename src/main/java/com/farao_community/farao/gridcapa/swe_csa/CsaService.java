package com.farao_community.farao.gridcapa.swe_csa;

import com.farao_community.farao.data.crac_creation.creator.csa_profile.crac_creator.CsaProfileCracCreationContext;
import com.farao_community.farao.data.crac_creation.creator.csa_profile.crac_creator.CsaProfileCracCreator;
import com.farao_community.farao.data.crac_io_api.CracExporters;
import com.farao_community.farao.data.crac_api.Crac;
import com.farao_community.farao.data.crac_creation.creator.api.parameters.CracCreationParameters;
import com.farao_community.farao.data.crac_creation.creator.csa_profile.CsaProfileCrac;
import com.farao_community.farao.data.crac_creation.creator.csa_profile.importer.CsaProfileCracImporter;
import com.google.common.base.Suppliers;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.iidm.network.ImportConfig;
import com.powsybl.iidm.network.*;
import com.farao_community.farao.data.crac_api.*;

import com.rte_france.farao.rao_runner.api.resource.RaoRequest;
import com.rte_france.farao.rao_runner.starter.AsynchronousRaoRunnerClient;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
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
        Network network = importNetwork(inputFilesArchive);
        Crac crac = importCrac(inputFilesArchive, network, utcInstant);
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

    private Network importNetwork(MultipartFile inputFilesArchive) {
        Network network = Network.read(Paths.get(inputFilesArchive.toString()), LocalComputationManager.getDefault(), Suppliers.memoize(ImportConfig::load).get(), new Properties());
        return network;
    }

    private Crac importCrac(MultipartFile inputFilesArchive, Network network, Instant utcInstant) {
        CsaProfileCracImporter cracImporter = new CsaProfileCracImporter();
        CsaProfileCrac nativeCrac = null;
        try {
            nativeCrac = cracImporter.importNativeCrac(inputFilesArchive.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        CsaProfileCracCreator cracCreator = new CsaProfileCracCreator();
        CsaProfileCracCreationContext cracCreationContext = cracCreator.createCrac(nativeCrac, network, utcInstant.atOffset(ZoneOffset.UTC), new CracCreationParameters());
        return cracCreationContext.getCrac();
    }
}
