package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.gridcapa_swe_commons.exception.SweInternalException;
import com.farao_community.farao.swe_csa.app.s3.S3ArtifactsAdapter;
import com.powsybl.commons.datasource.MemDataSource;
import com.powsybl.iidm.network.Network;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Service
public class FileExporter {
    private final S3ArtifactsAdapter s3ArtifactsAdapter;

    public FileExporter(S3ArtifactsAdapter s3ArtifactsAdapter) {
        this.s3ArtifactsAdapter = s3ArtifactsAdapter;
    }

    public String saveNetworkInArtifact(Network network, String networkFilePath) {
        MemDataSource memDataSource = new MemDataSource();
        network.write("XIIDM", new Properties(), memDataSource);
        try (InputStream is = memDataSource.newInputStream("", "xiidm")) {
            s3ArtifactsAdapter.uploadFile(networkFilePath, is);
        } catch (IOException e) {
            throw new SweInternalException("Error while trying to save network to artifacts", e);
        }
        return s3ArtifactsAdapter.generatePreSignedUrl(networkFilePath);
    }

}
