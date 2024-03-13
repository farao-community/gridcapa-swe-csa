package com.farao_community.farao.swe_csa.app;

import com.farao_community.farao.swe_csa.api.resource.CsaRequest;
import com.farao_community.farao.swe_csa.app.s3.S3ArtifactsAdapter;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class MockCsaRequest {
    private static final DateTimeFormatter HOURLY_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd'_'HHmm").withZone(ZoneId.of("UTC"));

    private final S3ArtifactsAdapter s3ArtifactsAdapter;

    public MockCsaRequest(S3ArtifactsAdapter s3ArtifactsAdapter) {
        this.s3ArtifactsAdapter = s3ArtifactsAdapter;
    }

    public CsaRequest makeRequest(MultipartFile cracJson, MultipartFile networkIidm, Instant utcInstant) throws IOException {
        String taskId = UUID.randomUUID().toString();
        String cracDestinationPath = "/inputs/" + HOURLY_NAME_FORMATTER.format(utcInstant).concat(".json");
        s3ArtifactsAdapter.uploadFile(cracDestinationPath, cracJson.getInputStream());
        String iidmNetworkDestinationPath = "/inputs/" + HOURLY_NAME_FORMATTER.format(utcInstant).concat(".xiidm");
        s3ArtifactsAdapter.uploadFile(iidmNetworkDestinationPath, networkIidm.getInputStream());
        return new CsaRequest(taskId, utcInstant.toString(), s3ArtifactsAdapter.generatePreSignedUrl(iidmNetworkDestinationPath), s3ArtifactsAdapter.generatePreSignedUrl(cracDestinationPath), s3ArtifactsAdapter.generatePreSignedUrl(String.format("%s/result/rao-schedule.json", taskId)));
    }

}
