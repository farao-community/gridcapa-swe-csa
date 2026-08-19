package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.swe_csa.app.s3.S3ArtifactsAdapter;
import com.powsybl.commons.report.ReportNode;
import com.powsybl.openrao.raoapi.parameters.RaoParameters;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class FileExporterTest {

    @Autowired
    FileExporter fileExporter;

    @MockitoBean
    S3ArtifactsAdapter s3ArtifactsAdapter;

    @Test
    void saveRaoParametersTest() {
        Mockito.when(s3ArtifactsAdapter.generatePreSignedUrl("configurations/rao-parameters-19990101_1230.json")).thenReturn("url");
        String result = fileExporter.uploadRaoParameters(OffsetDateTime.parse("1999-01-01T12:30Z").toInstant(), RaoParameters.load(ReportNode.NO_OP));
        assertEquals("url", result);
    }
}
