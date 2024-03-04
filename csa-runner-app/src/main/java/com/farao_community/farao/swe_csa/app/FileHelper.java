package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.swe_csa.api.exception.CsaInternalException;
import com.farao_community.farao.swe_csa.app.s3.S3ArtifactsAdapter;
import com.google.common.base.Suppliers;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.iidm.network.ImportConfig;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.cracapi.Crac;
import com.powsybl.openrao.data.craccreation.creator.api.parameters.CracCreationParameters;
import com.powsybl.openrao.data.craccreation.creator.csaprofile.CsaProfileCrac;
import com.powsybl.openrao.data.craccreation.creator.csaprofile.craccreator.CsaProfileCracCreationContext;
import com.powsybl.openrao.data.craccreation.creator.csaprofile.craccreator.CsaProfileCracCreator;
import com.powsybl.openrao.data.craccreation.creator.csaprofile.importer.CsaProfileCracImporter;
import com.powsybl.openrao.data.cracioapi.CracExporters;
import com.powsybl.openrao.raoapi.json.JsonRaoParameters;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

@Service
public class FileHelper {
    private final S3ArtifactsAdapter s3ArtifactsAdapter;

    private static final DateTimeFormatter HOURLY_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'_'HHmm").withZone(ZoneId.of("UTC"));

    public FileHelper(S3ArtifactsAdapter s3ArtifactsAdapter) {
        this.s3ArtifactsAdapter = s3ArtifactsAdapter;
    }

    String uploadIidmNetworkToMinio(String taskId, Network network, Instant utcInstant) throws IOException {
        Path iidmTmpPath = new File(System.getProperty("java.io.tmpdir"), "network.xiidm").toPath();
        network.write("XIIDM", null, iidmTmpPath);
        String iidmNetworkDestinationPath = String.format("%s/networks/%s", taskId, HOURLY_NAME_FORMATTER.format(utcInstant).concat(".xiidm"));
        try (FileInputStream iidmNetworkInputStream = new FileInputStream(iidmTmpPath.toString())) {
            s3ArtifactsAdapter.uploadFile(iidmNetworkDestinationPath, iidmNetworkInputStream);
        }
        return s3ArtifactsAdapter.generatePreSignedUrl(iidmNetworkDestinationPath);
    }

    String uploadJsonCrac(String taskId, Crac crac, Instant utcInstant) {
        String jsonCracFilePath = String.format("%s/cracs/%s", taskId, HOURLY_NAME_FORMATTER.format(utcInstant).concat(".json"));
        ByteArrayOutputStream cracByteArrayOutputStream = new ByteArrayOutputStream();
        CracExporters.exportCrac(crac, "Json", cracByteArrayOutputStream);
        try (InputStream is = new ByteArrayInputStream(cracByteArrayOutputStream.toByteArray())) {
            s3ArtifactsAdapter.uploadFile(jsonCracFilePath, is);
        } catch (IOException e) {
            throw new CsaInternalException(e.getMessage());
        }
        return s3ArtifactsAdapter.generatePreSignedUrl(jsonCracFilePath);
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
            throw new CsaInternalException(e.getMessage());
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
        s3ArtifactsAdapter.uploadFile(raoParametersFilePath, bais);
        return s3ArtifactsAdapter.generatePreSignedUrl(raoParametersFilePath);
    }

}
