package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.swe_csa.api.exception.CsaInternalException;
import com.farao_community.farao.swe_csa.app.s3.S3ArtifactsAdapter;
import com.farao_community.farao.swe_csa.app.utils.TmpFile;
import com.powsybl.commons.datasource.MemDataSource;
import com.powsybl.iidm.network.Network;
import com.powsybl.openrao.data.crac.api.Crac;
import com.powsybl.openrao.data.raoresult.api.RaoResult;
import com.powsybl.openrao.raoapi.json.JsonRaoParameters;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.io.IOException;
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
        try (var tmp = TmpFile.create("network", memDataSource.newInputStream("", IIDM_EXTENSION))) {
            s3ArtifactsAdapter.uploadFile(networkFilePath, tmp);
        } catch (IOException e) {
            throw new CsaInternalException(taskId, "Error while trying to save network to artifacts", e);
        }
        return s3ArtifactsAdapter.generatePreSignedUrl(networkFilePath);
    }

    @WithSpan("saveRaoResultInArtifact")
    public void saveRaoResultInArtifact(@SpanAttribute("destinationPath") String destinationPath, RaoResult raoResult, Crac crac) {
        try (var tmp = TmpFile.create("rao")) {
            Properties propertiesAmperes = new Properties();
            propertiesAmperes.setProperty("rao-result.export.json.flows-in-amperes", "true");
            raoResult.write("JSON", crac, propertiesAmperes, tmp.getWriteStream());
            s3ArtifactsAdapter.uploadFile(destinationPath, tmp);
        } catch (IOException e) {
            throw new RuntimeException("Error uploading Rao results", e);
        }
    }

    @WithSpan("uploadRaoParameters")
    public String uploadRaoParameters(@SpanAttribute("instant") Instant utcInstant) {
        String raoParametersFilePath = String.format("configurations/rao-parameters-%s", HOURLY_NAME_FORMATTER.format(utcInstant).concat(".json"));
        RaoParameters raoParameters = RaoParameters.load();
        try (var tmp = TmpFile.create("rao-params")) {
            JsonRaoParameters.write(raoParameters, tmp.getWriteStream());
            s3ArtifactsAdapter.uploadFile(raoParametersFilePath, tmp);
            return s3ArtifactsAdapter.generatePreSignedUrl(raoParametersFilePath);
        } catch (IOException e) {
            throw new RuntimeException("Error uploading Rao parameters", e);
        }
    }

}
