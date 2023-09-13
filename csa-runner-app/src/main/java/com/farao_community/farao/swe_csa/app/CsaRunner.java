package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.csa.runner.api.JsonApiConverter;
import com.farao_community.farao.csa.runner.api.exception.AbstractCsaException;
import com.farao_community.farao.csa.runner.api.exception.CsaInternalException;
import com.farao_community.farao.csa.runner.api.exception.CsaInvalidDataException;
import com.farao_community.farao.csa.runner.api.resource.CsaRequest;
import com.farao_community.farao.csa.runner.api.resource.CsaResponse;
import com.farao_community.farao.csa.runner.api.resource.Status;
import com.farao_community.farao.data.crac_creation.creator.csa_profile.crac_creator.CsaProfileCracCreationContext;
import com.farao_community.farao.data.crac_creation.creator.csa_profile.crac_creator.CsaProfileCracCreator;
import com.farao_community.farao.data.crac_io_api.CracExporters;
import com.farao_community.farao.data.crac_api.Crac;
import com.farao_community.farao.data.crac_creation.creator.api.parameters.CracCreationParameters;
import com.farao_community.farao.data.crac_creation.creator.csa_profile.CsaProfileCrac;
import com.farao_community.farao.data.crac_creation.creator.csa_profile.importer.CsaProfileCracImporter;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.farao_community.farao.rao_api.json.JsonRaoParameters;
import com.farao_community.farao.rao_api.parameters.RaoParameters;
import com.farao_community.farao.rao_runner.api.resource.RaoRequest;
import com.farao_community.farao.rao_runner.api.resource.RaoResponse;
import com.farao_community.farao.rao_runner.starter.RaoRunnerClient;
import com.google.common.base.Suppliers;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.iidm.network.ImportConfig;
import com.powsybl.iidm.network.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.UUID;

@Service
public class CsaRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(CsaRunner.class);
    private static final DateTimeFormatter HOURLY_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'_'HHmm").withZone(ZoneId.of("UTC"));

    private final RaoRunnerClient raoRunnerClient;
    private final MinioAdapter minioAdapter;
    private final JsonApiConverter jsonApiConverter = new JsonApiConverter();
    private final StreamBridge streamBridge;

    public CsaRunner(RaoRunnerClient raoRunnerClient, MinioAdapter minioAdapter, StreamBridge streamBridge) {
        this.raoRunnerClient = raoRunnerClient;
        this.minioAdapter = minioAdapter;
        this.streamBridge = streamBridge;
    }

    public byte[] launchCsaRequest(byte[] req) {
        byte[] result;
        CsaRequest csaRequest = jsonApiConverter.fromJsonMessage(req, CsaRequest.class);
        try {
            LOGGER.info("Csa request received : {}", csaRequest);
            // todo download files from minio
            //  todo create inputFilesArchive (ongoing JP)
            MultipartFile inputFilesArchive = null;
            Instant utcInstant = null;
            String requestId = "";

            Network network = importNetwork(inputFilesArchive);
            Crac crac = importCrac(inputFilesArchive, network, utcInstant);
            String taskId = UUID.randomUUID().toString();
            String networkFileUrl = uploadIidmNetworkToMinio(taskId, network, utcInstant);
            String cracFileUrl = uploadJsonCrac(taskId, crac, utcInstant);
            String raoParametersUrl = uploadRaoParameters(taskId, utcInstant);
            RaoRequest raoRequest = new RaoRequest(taskId, networkFileUrl, cracFileUrl, raoParametersUrl);
            // todo send ack message = inputs accepted, if exception send data exception message
            streamBridge.send("acknowledgement", new CsaResponse(requestId, Status.ACCEPTED.toString()));

            try {
                RaoResponse raoResponse = raoRunnerClient.runRao(raoRequest);
                CsaResponse csaResponse = new CsaResponse(requestId, Status.FINISHED.toString());
                return jsonApiConverter.toJsonMessage(csaResponse, CsaResponse.class);
            } catch (Exception raoExp) {
                AbstractCsaException csaException = new CsaInternalException("Error during rao", raoExp);
                LOGGER.error(csaException.getDetails(), csaException);
                LOGGER.error(csaException.getDetails());
                return jsonApiConverter.toJsonMessage(csaException);
            }

        } catch (Exception e) {
            AbstractCsaException csaException = new CsaInvalidDataException("Couldn't convert Csa data to farao data", e);
            LOGGER.error(csaException.getDetails(), csaException);
            LOGGER.error(csaException.getDetails());
            return jsonApiConverter.toJsonMessage(csaException);
        }
    }

    public ResponseEntity runRao(MultipartFile inputFilesArchive, Instant utcInstant) throws IOException {
        Network network = importNetwork(inputFilesArchive);
        Crac crac = importCrac(inputFilesArchive, network, utcInstant);
        String taskId = UUID.randomUUID().toString();
        String networkFileUrl = uploadIidmNetworkToMinio(taskId, network, utcInstant);
        String cracFileUrl = uploadJsonCrac(taskId, crac, utcInstant);
        String raoParametersUrl = uploadRaoParameters(taskId, utcInstant);
        RaoRequest raoRequest = new RaoRequest(taskId, networkFileUrl, cracFileUrl, raoParametersUrl);
        raoRunnerClient.runRao(raoRequest);
        return ResponseEntity.accepted().build();
    }

    private String uploadIidmNetworkToMinio(String taskId, Network network, Instant utcInstant) throws IOException {
        Path iidmTmpPath = new File(System.getProperty("java.io.tmpdir"), "network.xiidm").toPath();
        network.write("XIIDM", null, iidmTmpPath);
        String iidmNetworkDestinationPath = String.format("%s/networks/%s", taskId, HOURLY_NAME_FORMATTER.format(utcInstant).concat(".xiidm"));
        try (FileInputStream iidmNetworkInputStream = new FileInputStream(iidmTmpPath.toString())) {
            minioAdapter.uploadArtifact(iidmNetworkDestinationPath, iidmNetworkInputStream);
        }
        return minioAdapter.generatePreSignedUrl(iidmNetworkDestinationPath);
    }

    private String uploadJsonCrac(String taskId, Crac crac, Instant utcInstant) {
        String jsonCracFilePath = String.format("%s/cracs/%s", taskId, HOURLY_NAME_FORMATTER.format(utcInstant).concat(".json"));
        ByteArrayOutputStream cracByteArrayOutputStream = new ByteArrayOutputStream();
        CracExporters.exportCrac(crac, "Json", cracByteArrayOutputStream);
        try (InputStream is = new ByteArrayInputStream(cracByteArrayOutputStream.toByteArray())) {
            minioAdapter.uploadArtifact(jsonCracFilePath, is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return minioAdapter.generatePreSignedUrl(jsonCracFilePath);
    }

    private Network importNetwork(MultipartFile inputFilesArchive) {
        Network network = Network.read(saveFileToTmpDirectory(inputFilesArchive), LocalComputationManager.getDefault(), Suppliers.memoize(ImportConfig::load).get(), new Properties());
        return network;
    }

    public Path saveFileToTmpDirectory(MultipartFile inputFilesArchive) {
        Path tempFilePath = Path.of(System.getProperty("java.io.tmpdir"), inputFilesArchive.getOriginalFilename());
        try {
            inputFilesArchive.transferTo(tempFilePath);
            return tempFilePath;
        } catch (IOException e) {
            throw new RuntimeException("Failed to save the file to the temporary directory.", e);
        }
    }

    private Crac importCrac(MultipartFile inputFilesArchive, Network network, Instant utcInstant) {
        CsaProfileCracImporter cracImporter = new CsaProfileCracImporter();
        CsaProfileCrac nativeCrac;
        try {
            nativeCrac = cracImporter.importNativeCrac(inputFilesArchive.getInputStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        CsaProfileCracCreator cracCreator = new CsaProfileCracCreator();
        CsaProfileCracCreationContext cracCreationContext = cracCreator.createCrac(nativeCrac, network, utcInstant.atOffset(ZoneOffset.UTC), new CracCreationParameters());
        return cracCreationContext.getCrac();
    }

    String uploadRaoParameters(String taskId, Instant utcInstant) {
        String raoParametersFilePath = String.format("%s/rao-parameters/%s", taskId, HOURLY_NAME_FORMATTER.format(utcInstant).concat(".json"));
        RaoParameters raoParameters = RaoParameters.load();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonRaoParameters.write(raoParameters, baos);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        minioAdapter.uploadArtifact(raoParametersFilePath, bais);
        return minioAdapter.generatePreSignedUrl(raoParametersFilePath);
    }

}
