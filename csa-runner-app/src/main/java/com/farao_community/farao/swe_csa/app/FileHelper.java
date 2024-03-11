package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.swe_csa.api.exception.CsaInternalException;
import com.farao_community.farao.swe_csa.app.s3.S3ArtifactsAdapter;
import com.google.common.base.Suppliers;
import com.powsybl.computation.local.LocalComputationManager;
import com.powsybl.iidm.network.Exporter;
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

    public String exportCracToFile(Crac crac, String pathName) throws IOException {
        String fileName = pathName.concat("/crac.json");
        FileOutputStream outputStream = new FileOutputStream(fileName);
        CracExporters.exportCrac(crac, "Json", outputStream);
        outputStream.close();
        return fileName;
    }

    public String exportNetworkToFile(Network network, String pathName) throws IOException {
        String fileName = pathName.concat("/network.iidm");
        FileOutputStream outputStream = new FileOutputStream(fileName);
        Exporter exporter = Exporter.find("iidm");

        outputStream.close();
        return fileName;
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
