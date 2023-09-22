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
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class CsaRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(CsaRunner.class);
    private static final DateTimeFormatter HOURLY_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'_'HHmm").withZone(ZoneId.of("UTC"));

    private final RaoRunnerClient raoRunnerClient;
    private final MinioAdapter minioAdapter;
    private final JsonApiConverter jsonApiConverter = new JsonApiConverter();
    private final StreamBridge streamBridge;
    private byte[] resultBytes;

    public CsaRunner(RaoRunnerClient raoRunnerClient, MinioAdapter minioAdapter, StreamBridge streamBridge) {
        this.raoRunnerClient = raoRunnerClient;
        this.minioAdapter = minioAdapter;
        this.streamBridge = streamBridge;
    }

    public byte[] launchCsaRequest(byte[] req) {
        try {
            CsaRequest csaRequest = jsonApiConverter.fromJsonMessage(req, CsaRequest.class);
            LOGGER.info("Csa request received : {}", csaRequest);

            FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));
            Path archiveTempPath = Files.createTempFile("csa-temp-inputs", "inputs.zip", attr);

            Instant utcInstant = Instant.parse(csaRequest.getBusinessTimestamp());
            zipDataCsaRequestFiles(csaRequest, archiveTempPath);
            String requestId = csaRequest.getId();

            Network network = importNetwork(archiveTempPath);
            Crac crac = importCrac(archiveTempPath, network, utcInstant);
            String networkFileUrl = uploadIidmNetworkToMinio(requestId, network, utcInstant);
            String cracFileUrl = uploadJsonCrac(requestId, crac, utcInstant);
            String raoParametersUrl = uploadRaoParameters(requestId, utcInstant);
            RaoRequest raoRequest = new RaoRequest(requestId, networkFileUrl, cracFileUrl, raoParametersUrl);

            // send ack message
            streamBridge.send("acknowledgement", new CsaResponse(requestId, Status.ACCEPTED.toString()));

            try {
                RaoResponse raoResponse = raoRunnerClient.runRao(raoRequest);
                LOGGER.info("RAO computation answer received  for TimeStamp: '{}'", raoRequest.getInstant());
                // TODO create rao schedule and send to minio/sds
                CsaResponse csaResponse = new CsaResponse(raoResponse.getId(), Status.FINISHED.toString());
                setResultBytes(jsonApiConverter.toJsonMessage(csaResponse, CsaResponse.class));
            } catch (Exception raoException) {
                AbstractCsaException csaException = new CsaInternalException("Error during rao", raoException);
                LOGGER.error(csaException.getDetails(), csaException);
                setResultBytes(jsonApiConverter.toJsonMessage(csaException));
            }

        } catch (Exception e) {
            AbstractCsaException csaException = new CsaInvalidDataException("Couldn't convert Csa data to farao data", e);
            LOGGER.error(csaException.getDetails(), csaException);
            setResultBytes(jsonApiConverter.toJsonMessage(csaException));
        }
        return resultBytes;
    }

    public ResponseEntity runRao(MultipartFile inputFilesArchive, Instant utcInstant) throws IOException, ExecutionException, InterruptedException {
        String taskId = UUID.randomUUID().toString();
        Path tempFilePath = Path.of(System.getProperty("java.io.tmpdir"), inputFilesArchive.getOriginalFilename());
        saveFileToDirectory(inputFilesArchive, tempFilePath);
        Network network = importNetwork(tempFilePath);
        Crac crac = importCrac(tempFilePath, network, utcInstant);
        String networkFileUrl = uploadIidmNetworkToMinio(taskId, network, utcInstant);
        String cracFileUrl = uploadJsonCrac(taskId, crac, utcInstant);
        String raoParametersUrl = uploadRaoParameters(taskId, utcInstant);
        RaoRequest raoRequest = new RaoRequest(taskId, networkFileUrl, cracFileUrl, raoParametersUrl);
        raoRunnerClient.runRao(raoRequest);
        return ResponseEntity.accepted().build();
    }

    public void zipDataCsaRequestFiles(CsaRequest csaRequest, Path archiveTempPath) throws IOException {
        FileOutputStream fos = new FileOutputStream(archiveTempPath.toFile());
        ZipOutputStream zipOut = new ZipOutputStream(fos);
        zipDataFile(csaRequest.getCommonProfiles().getSvProfileUri(), zipOut);
        zipDataFile(csaRequest.getCommonProfiles().getEqbdProfileUri(), zipOut);
        zipDataFile(csaRequest.getCommonProfiles().getTpbdProfileUri(), zipOut);
        zipDataProfilesFiles(csaRequest.getEsProfiles(), zipOut);
        zipDataProfilesFiles(csaRequest.getFrProfiles(), zipOut);
        zipDataProfilesFiles(csaRequest.getPtProfiles(), zipOut);
        zipOut.close();
        fos.close();
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

    public void zipDataFile(String uriStr, ZipOutputStream zipOut) throws IOException {
        if (uriStr != null) {
            URL url = new URL(uriStr);
            try (InputStream fis = url.openStream()) {
                ZipEntry zipEntry = new ZipEntry(FilenameUtils.getName(url.getPath()));
                zipOut.putNextEntry(zipEntry);
                byte[] bytes = new byte[1024];
                int length;
                while ((length = fis.read(bytes)) >= 0) {
                    zipOut.write(bytes, 0, length);
                }
            }
        }
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

    public Network importNetwork(Path archiveTempPath) {
        return Network.read(archiveTempPath, LocalComputationManager.getDefault(), Suppliers.memoize(ImportConfig::load).get(), new Properties());
    }

    public void saveFileToDirectory(MultipartFile inputFilesArchive, Path directory) {
        try {
            inputFilesArchive.transferTo(directory);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save the file to the temporary directory.", e);
        }
    }

    public Crac importCrac(Path archiveTempPath, Network network, Instant utcInstant) {
        CsaProfileCracImporter cracImporter = new CsaProfileCracImporter();
        CsaProfileCrac nativeCrac;
        try {
            nativeCrac = cracImporter.importNativeCrac(new FileInputStream(archiveTempPath.toFile()));
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
