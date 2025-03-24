package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.swe_csa.api.exception.CsaInternalException;
import com.farao_community.farao.swe_csa.app.s3.S3ArtifactsAdapter;
import com.powsybl.commons.datasource.MemDataSource;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.Properties;

@Service
public class FileExporter {

    private static final String IIDM_EXPORT_FORMAT = "XIIDM";
    private static final String IIDM_EXTENSION = "xiidm";

    private final S3ArtifactsAdapter s3ArtifactsAdapter;

    public FileExporter(S3ArtifactsAdapter s3ArtifactsAdapter) {
        this.s3ArtifactsAdapter = s3ArtifactsAdapter;
    }

    public String saveNetworkInArtifact(String taskId, Network network, String networkFilePath) {
        MemDataSource memDataSource = new MemDataSource();
        network.write(IIDM_EXPORT_FORMAT, new Properties(), memDataSource);
        try (InputStream is = memDataSource.newInputStream("", IIDM_EXTENSION)) {
            s3ArtifactsAdapter.uploadFile(networkFilePath, is);
        } catch (IOException e) {
            throw new CsaInternalException(taskId, "Error while trying to save network to artifacts", e);
        }
        return s3ArtifactsAdapter.generatePreSignedUrl(networkFilePath);
    }

    public void saveRaoResultInArtifact(String destinationPath, RaoResult raoResult, Crac crac) {
        ByteArrayOutputStream outputStreamRaoResult = new ByteArrayOutputStream();
        Properties propertiesAmperes = new Properties();
        propertiesAmperes.setProperty("rao-result.export.json.flows-in-amperes", "true");
        raoResult.write("JSON", crac, propertiesAmperes, outputStreamRaoResult);
        s3ArtifactsAdapter.uploadFile(destinationPath, new ByteArrayInputStream(outputStreamRaoResult.toByteArray()));
    }

}
