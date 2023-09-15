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
import com.farao_community.farao.rao_runner.starter.AsynchronousRaoRunnerClient;
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
import org.springframework.mock.web.MockMultipartFile;

import java.io.*;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class CsaRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(CsaRunner.class);
    private static final DateTimeFormatter HOURLY_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'_'HHmm").withZone(ZoneId.of("UTC"));

    private final AsynchronousRaoRunnerClient asynchronousRaoRunnerClient;
    private final MinioAdapter minioAdapter;
    private final JsonApiConverter jsonApiConverter = new JsonApiConverter();
    private final StreamBridge streamBridge;
    private byte[] resultBytes;

    public CsaRunner(AsynchronousRaoRunnerClient asynchronousRaoRunnerClient, MinioAdapter minioAdapter, StreamBridge streamBridge) {
        this.asynchronousRaoRunnerClient = asynchronousRaoRunnerClient;
        this.minioAdapter = minioAdapter;
        this.streamBridge = streamBridge;
    }

    public byte[] launchCsaRequest(byte[] req) {
        try {
            CsaRequest csaRequest = jsonApiConverter.fromJsonMessage(req, CsaRequest.class);
            LOGGER.info("Csa request received : {}", csaRequest);
            // todo create a mock app that pushes files to minio and return a request json file with presigned urls
            // todo browse request and download files from minio
            // todo create inputFilesArchive (ongoing by JP)
            Instant utcInstant = null;
            MultipartFile inputFilesArchive = zipDataFiles(csaRequest, utcInstant);
            String requestId = "";

            Network network = importNetwork(inputFilesArchive);
            Crac crac = importCrac(inputFilesArchive, network, utcInstant);
            String taskId = UUID.randomUUID().toString();
            String networkFileUrl = uploadIidmNetworkToMinio(taskId, network, utcInstant);
            String cracFileUrl = uploadJsonCrac(taskId, crac, utcInstant);
            String raoParametersUrl = uploadRaoParameters(taskId, utcInstant);
            RaoRequest raoRequest = new RaoRequest(taskId, networkFileUrl, cracFileUrl, raoParametersUrl);

            // send ack message
            streamBridge.send("acknowledgement", new CsaResponse(requestId, Status.ACCEPTED.toString()));

            CompletableFuture<RaoResponse> raoResponseFuture = asynchronousRaoRunnerClient.runRaoAsynchronously(raoRequest);
            raoResponseFuture.thenComposeAsync(raoResponse -> {
                LOGGER.info("RAO computation answer received  for TimeStamp: '{}'", raoRequest.getInstant());
                // TODO create rao schedule and send to minio/sds
                CsaResponse csaResponse = new CsaResponse(requestId, Status.FINISHED.toString());
                setResultBytes(jsonApiConverter.toJsonMessage(csaResponse, CsaResponse.class));
                return null;
            }).exceptionally(raoException -> {
                AbstractCsaException csaException = new CsaInternalException("Error during rao", raoException);
                LOGGER.error(csaException.getDetails(), csaException);
                setResultBytes(jsonApiConverter.toJsonMessage(csaException));
                return null;
            });
        } catch (Exception e) {
            AbstractCsaException csaException = new CsaInvalidDataException("Couldn't convert Csa data to farao data", e);
            LOGGER.error(csaException.getDetails(), csaException);
            setResultBytes(jsonApiConverter.toJsonMessage(csaException));
        }
        return resultBytes;
    }

    public ResponseEntity runRao(MultipartFile inputFilesArchive, Instant utcInstant) throws IOException, ExecutionException, InterruptedException {
        Network network = importNetwork(inputFilesArchive);
        Crac crac = importCrac(inputFilesArchive, network, utcInstant);
        String taskId = UUID.randomUUID().toString();
        String networkFileUrl = uploadIidmNetworkToMinio(taskId, network, utcInstant);
        String cracFileUrl = uploadJsonCrac(taskId, crac, utcInstant);
        String raoParametersUrl = uploadRaoParameters(taskId, utcInstant);
        RaoRequest raoRequest = new RaoRequest(taskId, networkFileUrl, cracFileUrl, raoParametersUrl);
        asynchronousRaoRunnerClient.runRaoAsynchronously(raoRequest).get();
        return ResponseEntity.accepted().build();
    }

    private MultipartFile zipDataFiles(CsaRequest csaRequest, Instant utcInstant) throws IOException {
        String zipName = String.format("csaProfileData_%s", HOURLY_NAME_FORMATTER.format(utcInstant).concat(".zip"));
        FileOutputStream fos = new FileOutputStream(zipName);
        ZipOutputStream zipOut = new ZipOutputStream(fos);
        zipDataFiles(csaRequest, zipOut);
        zipOut.close();
        fos.close();
        return new MockMultipartFile(zipName, new FileInputStream(zipName));
    }

    private void zipDataFiles(CsaRequest csaRequest, ZipOutputStream zipOut) throws IOException {
        zipDataFile(csaRequest.getCommonProfiles().getSvProfileUri(), zipOut);
        zipDataFile(csaRequest.getCommonProfiles().getEqbdProfileUri(), zipOut);
        zipDataFile(csaRequest.getCommonProfiles().getTpbdProfileUri(), zipOut);
        zipDataProfilesFiles(csaRequest.getEsProfiles(), zipOut);
        zipDataProfilesFiles(csaRequest.getFrProfiles(), zipOut);
        zipDataProfilesFiles(csaRequest.getPtProfiles(), zipOut);
    }

    private void zipDataProfilesFiles(CsaRequest.Profiles profiles, ZipOutputStream zipOut) throws IOException {
        zipDataFile(profiles.getSshProfileUri(), zipOut);
        zipDataFile(profiles.getTpProfileUri(), zipOut);
        zipDataFile(profiles.getEqProfileUri(), zipOut);
        zipDataFile(profiles.getAeProfileUri(), zipOut);
        zipDataFile(profiles.getCoProfileUri(), zipOut);
        zipDataFile(profiles.getRaProfileUri(), zipOut);
        zipDataFile(profiles.getErProfileUri(), zipOut);
        zipDataFile(profiles.getSsiProfileUri(), zipOut);
        zipDataFile(profiles.getSisProfileUri(), zipOut);
        zipDataFile(profiles.getMaProfileUri(), zipOut);
        zipDataFile(profiles.getSmProfileUri(), zipOut);
        zipDataFile(profiles.getAsProfileUri(), zipOut);
    }

    private void zipDataFile(String uriStr, ZipOutputStream zipOut) throws IOException {
        File fileToZip = new File(URI.create(uriStr));
        FileInputStream fis = new FileInputStream(fileToZip);
        ZipEntry zipEntry = new ZipEntry(fileToZip.getName());
        zipOut.putNextEntry(zipEntry);
        byte[] bytes = new byte[1024];
        int length;
        while ((length = fis.read(bytes)) >= 0) {
            zipOut.write(bytes, 0, length);
        }
        fis.close();
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

    public void setResultBytes(byte[] resultBytes) {
        this.resultBytes = resultBytes;
    }
}
