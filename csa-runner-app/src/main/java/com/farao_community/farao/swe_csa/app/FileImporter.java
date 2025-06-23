package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.swe_csa.api.exception.CsaInvalidDataException;
import com.farao_community.farao.swe_csa.app.s3.S3ArtifactsAdapter;
import com.farao_community.farao.swe_csa.app.shift.SweCsaZonalData;
import com.powsybl.glsk.api.GlskDocument;
import com.powsybl.glsk.api.io.GlskDocumentImporter;
import com.powsybl.glsk.api.io.GlskDocumentImporters;
import com.powsybl.glsk.commons.ZonalData;
import com.powsybl.iidm.modification.scalable.Scalable;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.commons.OpenRaoException;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.raoapi.json.JsonRaoParameters;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
public class FileImporter {
    private final Logger businessLogger;

    private final S3ArtifactsAdapter s3ArtifactsAdapter;

    private static final DateTimeFormatter HOURLY_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'_'HHmm").withZone(ZoneId.of("UTC"));

    public FileImporter(Logger businessLogger, S3ArtifactsAdapter s3ArtifactsAdapter) {
        this.businessLogger = businessLogger;
        this.s3ArtifactsAdapter = s3ArtifactsAdapter;
    }

    public Crac importCrac(String taskId, String cracFileUrl, Network network) {
        try {
            return Crac.read(getFileNameFromUrl(taskId, cracFileUrl), openUrlStream(taskId, cracFileUrl), network);
        } catch (OpenRaoException | CsaInvalidDataException | IOException e) {
            String message = String.format("Exception occurred while importing CRAC file %s", getFileNameFromUrl(taskId, cracFileUrl));
            throw new CsaInvalidDataException(taskId, message, e);
        }
    }

    public Network importNetwork(String taskId, String networkFileUrl) {
        try {
            return Network.read(getFileNameFromUrl(taskId, networkFileUrl), openUrlStream(taskId, networkFileUrl));
        } catch (Exception e) {
            String message = String.format("Exception occurred while importing network %s", getFileNameFromUrl(taskId, networkFileUrl));
            throw new CsaInvalidDataException(taskId, message, e);
        }
    }

    public String uploadRaoParameters(Instant utcInstant) {
        String raoParametersFilePath = String.format("configurations/rao-parameters-%s", HOURLY_NAME_FORMATTER.format(utcInstant).concat(".json"));
        RaoParameters raoParameters = RaoParameters.load();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonRaoParameters.write(raoParameters, baos);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        s3ArtifactsAdapter.uploadFile(raoParametersFilePath, bais);
        return s3ArtifactsAdapter.generatePreSignedUrl(raoParametersFilePath);
    }

    private InputStream openUrlStream(String taskId, String urlString) {
        try {
            URL url = new URI(urlString).toURL();
            return url.openStream(); // NOSONAR
        } catch (IOException | URISyntaxException | IllegalArgumentException e) {
            throw new CsaInvalidDataException(taskId, String.format("Exception occurred while retrieving file content from : %s", urlString), e);
        }
    }

    private String getFileNameFromUrl(String taskId, String stringUrl) {
        try {
            URL url = new URI(stringUrl).toURL();
            return FilenameUtils.getName(url.getPath());
        } catch (IOException | URISyntaxException | IllegalArgumentException e) {
            throw new CsaInvalidDataException(taskId, String.format("Exception occurred while retrieving file name from : %s", stringUrl), e);
        }
    }

    public ZonalData<Scalable> getZonalData(String taskId, Instant instant, String glskUri, Network network) {
        try {
            GlskDocumentImporter glskDocumentImporter = GlskDocumentImporters.findImporter(openUrlStream(taskId, glskUri));
            GlskDocument glskDocument = glskDocumentImporter.importGlsk(openUrlStream(taskId, glskUri));
            businessLogger.info("Glsk document imported");
            return glskDocument.getZonalScalable(network, instant);
        } catch (Exception e) {
            businessLogger.error("Glsk document couldn't be imported, as a backup solution Scalable proportional to network generators will be used");
            return SweCsaZonalData.getZonalData(network);
        }
    }
}
