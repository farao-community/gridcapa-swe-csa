package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.swe_csa.api.exception.CsaInternalException;
import com.farao_community.farao.swe_csa.app.s3.S3ArtifactsAdapter;
import com.powsybl.commons.datasource.MemDataSource;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import com.powsybl.openrao.raoapi.json.JsonRaoParameters;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import org.springframework.stereotype.Service;

@Service
public class FileExporter {

    private static final String IIDM_EXPORT_FORMAT = "XIIDM";
    private static final String IIDM_EXTENSION = "xiidm";
    private static final DateTimeFormatter HOURLY_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'_'HHmm").withZone(
        ZoneId.of("UTC"));

    private final S3ArtifactsAdapter s3ArtifactsAdapter;

    public FileExporter(S3ArtifactsAdapter s3ArtifactsAdapter) {
        this.s3ArtifactsAdapter = s3ArtifactsAdapter;
    }

    @WithSpan("saveNetworkInArtifact")
    public String saveNetworkInArtifact(@SpanAttribute("taskId") String taskId, Network network, @SpanAttribute("networkFilePath") String networkFilePath) {
        MemDataSource memDataSource = new MemDataSource();
        network.write(IIDM_EXPORT_FORMAT, new Properties(), memDataSource);
        try (InputStream is = memDataSource.newInputStream("", IIDM_EXTENSION)) {
            s3ArtifactsAdapter.uploadFile(networkFilePath, is);
        } catch (IOException e) {
            throw new CsaInternalException(taskId, "Error while trying to save network to artifacts", e);
        }
        return s3ArtifactsAdapter.generatePreSignedUrl(networkFilePath);
    }

    @WithSpan("saveRaoResultInArtifact")
    public void saveRaoResultInArtifact(@SpanAttribute("destinationPath") String destinationPath, RaoResult raoResult, Crac crac) {
        ByteArrayOutputStream outputStreamRaoResult = new ByteArrayOutputStream();
        Properties propertiesAmperes = new Properties();
        propertiesAmperes.setProperty("rao-result.export.json.flows-in-amperes", "true");
        raoResult.write("JSON", crac, propertiesAmperes, outputStreamRaoResult);
        s3ArtifactsAdapter.uploadFile(destinationPath, new ByteArrayInputStream(outputStreamRaoResult.toByteArray()));
    }

    @WithSpan("uploadRaoParameters")
    public String uploadRaoParameters(@SpanAttribute("instant") Instant utcInstant) {
        String raoParametersFilePath = String.format("configurations/rao-parameters-%s", HOURLY_NAME_FORMATTER.format(utcInstant).concat(".json"));
        RaoParameters raoParameters = RaoParameters.load();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonRaoParameters.write(raoParameters, baos);
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        s3ArtifactsAdapter.uploadFile(raoParametersFilePath, bais);
        return s3ArtifactsAdapter.generatePreSignedUrl(raoParametersFilePath);
    }

}
