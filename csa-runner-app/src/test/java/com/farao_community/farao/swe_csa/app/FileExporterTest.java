package com.farao_community.farao.swe_csa.app;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.farao_community.farao.swe_csa.app.s3.S3ArtifactsAdapter;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class FileExporterTest {

    @Autowired
    FileExporter fileExporter;

    @MockitoBean
    S3ArtifactsAdapter s3ArtifactsAdapter;

    @Test
    void saveRaoParametersTest() {
        Mockito.when(s3ArtifactsAdapter.generatePreSignedUrl(
            "configurations/rao-parameters-19990101_1230.json")).thenReturn("url");
        String result = fileExporter.uploadRaoParameters(
            OffsetDateTime.parse("1999-01-01T12:30Z").toInstant());
        assertEquals("url", result);
    }
}
