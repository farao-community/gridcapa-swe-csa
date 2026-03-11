package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.swe_csa.api.exception.CsaInvalidDataException;
import com.farao_community.farao.swe_csa.app.shift.SweCsaZonalData;
import com.powsybl.glsk.api.GlskDocument;
import com.powsybl.glsk.api.io.GlskDocumentImporter;
import com.powsybl.glsk.api.io.GlskDocumentImporters;
import com.powsybl.glsk.commons.ZonalData;
import com.powsybl.iidm.modification.scalable.Scalable;
import com.powsybl.iidm.network.Network;
import com.powsybl.loadflow.LoadFlowParameters;
import com.powsybl.loadflow.json.JsonLoadFlowParameters;
import com.powsybl.openrao.commons.OpenRaoException;
import com.powsybl.openrao.data.crac.api.Crac;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Instant;

@Service
public class FileImporter {
    private final Logger businessLogger;

    public FileImporter(Logger businessLogger) {
        this.businessLogger = businessLogger;
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

    public LoadFlowParameters getLoadFlowParameters(String taskId, String loadFlowParametersUri) {
        if (loadFlowParametersUri == null || loadFlowParametersUri.isEmpty()) {
            businessLogger.info("No load flow parameters URI provided, using default parameters");
            return LoadFlowParameters.load();
        }
        try {
            return JsonLoadFlowParameters.read(openUrlStream(taskId, loadFlowParametersUri));

        } catch (Exception e) {
            String message = String.format("Exception occurred while importing load flow parameters %s", getFileNameFromUrl(taskId, loadFlowParametersUri));
            throw new CsaInvalidDataException(taskId, message, e);
        }
    }
}
