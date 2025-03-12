package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.swe_csa.api.exception.CsaInvalidDataException;
import com.farao_community.farao.swe_csa.app.s3.S3ArtifactsAdapter;
import com.farao_community.farao.swe_csa.app.shift.SweCsaZonalData;
import com.powsybl.glsk.api.GlskDocument;
import com.powsybl.glsk.api.io.GlskDocumentImporter;
import com.powsybl.glsk.api.io.GlskDocumentImporters;
import com.powsybl.glsk.commons.ZonalData;
import com.powsybl.iidm.modification.scalable.Scalable;
import com.powsybl.iidm.network.Country;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.commons.OpenRaoException;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.raoapi.json.JsonRaoParameters;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import com.powsybl.sensitivity.SensitivityVariableSet;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

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

    ZonalData<SensitivityVariableSet> importGlsk(String taskId, String instant, String glskUrl, Network network) throws CsaInvalidDataException {
        try {
            final InputStream glskFileInputStream = openUrlStream(taskId, glskUrl);
            final GlskDocument ucteGlskProvider = GlskDocumentImporters.importGlsk(glskFileInputStream);
            final OffsetDateTime offsetDateTime = OffsetDateTime.parse(instant);
            return ucteGlskProvider.getZonalGlsks(network, offsetDateTime.toInstant());
        } catch (Exception e) {
            final String message = String.format("Error occurred during GLSK Provider creation for timestamp %s, using GLSK file %s, and CGM network file %s",
                instant,
                FilenameUtils.getName(glskUrl),
                network.getNameOrId());
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

    public ZonalData<Scalable> getZonalData(String taskId, String glskUri, Network network, boolean filteredForSweCountries) {
        try {
            GlskDocumentImporter glskDocumentImporter = GlskDocumentImporters.findImporter(openUrlStream(taskId, glskUri));
            GlskDocument glskDocument = glskDocumentImporter.importGlsk(openUrlStream(taskId, glskUri));
            businessLogger.info("Glsk document imported");
            // TODO MBR check if we need to do the same workaround here for angle monitoring by filtering for swe countries only
            return glskDocument.getZonalScalable(network);
        } catch (OpenRaoException e) {
            businessLogger.error("Glsk document couldn't be imported, as a backup solution Scalable proportional to network generators will be used");
            if (filteredForSweCountries) {
                Set<Country> sweCountries = new HashSet<>(Arrays.asList(Country.FR, Country.PT, Country.ES));
                return SweCsaZonalData.getZonalData(network, sweCountries);
            } else {
                return SweCsaZonalData.getZonalData(network);

            }
        }
    }
}
