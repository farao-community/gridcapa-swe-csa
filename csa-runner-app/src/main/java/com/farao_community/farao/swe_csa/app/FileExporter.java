package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.gridcapa_swe_commons.exception.SweInternalException;
import com.farao_community.farao.swe_csa.app.s3.S3ArtifactsAdapter;
import com.powsybl.commons.datasource.MemDataSource;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.commons.Unit;
import com.powsybl.openrao.data.cracapi.Crac;
import com.powsybl.openrao.data.raoresultapi.RaoResult;
import com.powsybl.openrao.data.raoresultjson.RaoResultExporter;
import org.springframework.stereotype.Service;

import java.io.*;
import java.time.OffsetDateTime;
import java.util.Properties;
import java.util.Set;

@Service
public class FileExporter {

    private static final String RAO_RESULT = "raoResult-with-counter-trading.json";
    private static final String IIDM_EXPORT_FORMAT = "XIIDM";
    private static final String IIDM_EXTENSION = "xiidm";

    private final S3ArtifactsAdapter s3ArtifactsAdapter;

    public FileExporter(S3ArtifactsAdapter s3ArtifactsAdapter) {
        this.s3ArtifactsAdapter = s3ArtifactsAdapter;
    }

    public String saveNetworkInArtifact(Network network, String networkFilePath) {
        MemDataSource memDataSource = new MemDataSource();
        network.write(IIDM_EXPORT_FORMAT, new Properties(), memDataSource);
        try (InputStream is = memDataSource.newInputStream("", IIDM_EXTENSION)) {
            s3ArtifactsAdapter.uploadFile(networkFilePath, is);
        } catch (IOException e) {
            throw new SweInternalException("Error while trying to save network to artifacts", e);
        }
        return s3ArtifactsAdapter.generatePreSignedUrl(networkFilePath);
    }

    public void saveRaoResultInArtifact(RaoResult raoResult, Crac crac, Unit unit, String timestamp) {
        ByteArrayOutputStream outputStreamRaoResult = new ByteArrayOutputStream();
        new RaoResultExporter().export(raoResult, crac, Set.of(unit), outputStreamRaoResult);
        s3ArtifactsAdapter.uploadFile(generateArtifactsFolder(timestamp), new ByteArrayInputStream(outputStreamRaoResult.toByteArray()));
    }

    private String generateArtifactsFolder(String timestamp) {
        OffsetDateTime offsetDateTime = OffsetDateTime.parse(timestamp);
        return "artifacts" + "/" + offsetDateTime.getYear() + "/" + offsetDateTime.getMonthValue() + "/" + offsetDateTime.getDayOfMonth() + "/" + offsetDateTime.getHour() + "_" + offsetDateTime.getMinute() + "/"  + "rao-result-with-counter-trading.json";
    }

}
