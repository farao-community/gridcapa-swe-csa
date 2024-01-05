package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.data.crac_api.Crac;
import com.farao_community.farao.data.crac_creation.creator.api.parameters.CracCreationParameters;
import com.farao_community.farao.data.crac_creation.creator.csa_profile.CsaProfileCrac;
import com.farao_community.farao.data.crac_creation.creator.csa_profile.crac_creator.CsaProfileCracCreationContext;
import com.farao_community.farao.data.crac_creation.creator.csa_profile.crac_creator.CsaProfileCracCreator;
import com.farao_community.farao.data.crac_creation.creator.csa_profile.importer.CsaProfileCracImporter;
import com.farao_community.farao.data.crac_io_api.CracExporters;
import com.farao_community.farao.minio_adapter.starter.MinioAdapter;
import com.farao_community.farao.rao_api.json.JsonRaoParameters;
import com.farao_community.farao.rao_api.parameters.RaoParameters;
import com.farao_community.farao.rao_runner.api.resource.RaoRequest;
import com.farao_community.farao.rao_runner.api.resource.RaoResponse;
import com.farao_community.farao.rao_runner.starter.RaoRunnerClient;
import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
import com.farao_community.farao.swe_csa.api.resource.CsaResponse;
import com.farao_community.farao.swe_csa.api.resource.Status;
import com.google.common.base.Suppliers;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.iidm.network.ImportConfig;
import com.powsybl.iidm.network.Network;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class SweCsaRunner {

    private final RaoRunnerClient raoRunnerClient;
    private final MinioAdapter minioAdapter;
    private static final Logger LOGGER = LoggerFactory.getLogger(SweCsaRunner.class);
    private static final DateTimeFormatter HOURLY_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'_'HHmm").withZone(ZoneId.of("UTC"));

    public SweCsaRunner(RaoRunnerClient raoRunnerClient, MinioAdapter minioAdapter) {
        this.raoRunnerClient = raoRunnerClient;
        this.minioAdapter = minioAdapter;
    }

    @Threadable
    public CsaResponse run(CsaRequest csaRequest) throws IOException {

        String requestId = csaRequest.getId();
        LOGGER.info("Csa request received : {}", csaRequest);
        FileAttribute<Set<PosixFilePermission>> attr = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));
        Path archiveTempPath = Files.createTempFile("csa-temp-inputs", "inputs.zip", attr);

        Instant utcInstant = Instant.parse(csaRequest.getBusinessTimestamp());
        zipDataCsaRequestFiles(csaRequest, archiveTempPath);
        Network network = importNetwork(archiveTempPath);
        Crac crac = importCrac(archiveTempPath, network, utcInstant);
        String networkFileUrl = uploadIidmNetworkToMinio(requestId, network, utcInstant);
        String cracFileUrl = uploadJsonCrac(requestId, crac, utcInstant);
        String raoParametersUrl = uploadRaoParameters(requestId, utcInstant);
        RaoRequest raoRequest = new RaoRequest(requestId, networkFileUrl, cracFileUrl, raoParametersUrl);

        RaoResponse raoResponse = raoRunnerClient.runRao(raoRequest);
        LOGGER.info("RAO computation answer received for TimeStamp: '{}'", raoRequest.getInstant());
        return new CsaResponse(raoResponse.getId(), Status.FINISHED.toString());
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

}
