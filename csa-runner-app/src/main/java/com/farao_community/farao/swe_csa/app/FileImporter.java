package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.rao_runner.api.exceptions.RaoRunnerException;
import com.farao_community.farao.swe_csa.app.s3.S3ArtifactsAdapter;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.commons.OpenRaoException;
import com.powsybl.openrao.data.cracapi.Crac;
import com.powsybl.openrao.data.cracioapi.CracImporters;
import com.powsybl.openrao.raoapi.json.JsonRaoParameters;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
public class FileImporter {
    private final S3ArtifactsAdapter s3ArtifactsAdapter;

    private static final DateTimeFormatter HOURLY_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'_'HHmm").withZone(ZoneId.of("UTC"));

    public FileImporter(S3ArtifactsAdapter s3ArtifactsAdapter) {
        this.s3ArtifactsAdapter = s3ArtifactsAdapter;
    }

    Crac importCrac(String cracFileUrl) {
        try {
            return CracImporters.importCrac(getFileNameFromUrl(cracFileUrl), openUrlStream(cracFileUrl));
        } catch (OpenRaoException | RaoRunnerException e) {
            String message = String.format("Exception occurred while importing CRAC file %s", getFileNameFromUrl(cracFileUrl));
            throw new RaoRunnerException(message, e);
        }
    }

    Network importNetwork(String networkFileUrl) {
        try {
            return Network.read(getFileNameFromUrl(networkFileUrl), openUrlStream(networkFileUrl));
        } catch (Exception e) {
            String message = String.format("Exception occurred while importing network %s", getFileNameFromUrl(networkFileUrl));
            throw new RaoRunnerException(message, e);
        }
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

    private InputStream openUrlStream(String urlString) {
        try {
            URL url = new URL(urlString);
            return url.openStream(); // NOSONAR
        } catch (IOException e) {
            throw new RaoRunnerException(String.format("Exception occurred while retrieving file content from : %s", urlString), e);
        }
    }

    private String getFileNameFromUrl(String stringUrl) {
        try {
            URL url = new URL(stringUrl);
            return FilenameUtils.getName(url.getPath());
        } catch (IOException e) {
            throw new RaoRunnerException(String.format("Exception occurred while retrieving file name from : %s", stringUrl), e);
        }
    }
}
