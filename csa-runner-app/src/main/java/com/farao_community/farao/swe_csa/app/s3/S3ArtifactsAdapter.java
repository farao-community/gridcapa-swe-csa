package com.farao_community.farao.swe_csa.app.s3;

import com.farao_community.farao.swe_csa.app.utils.TmpFile;
import io.minio.MinioClient;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import java.io.InputStream;
import java.time.OffsetDateTime;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class S3ArtifactsAdapter {

    private final MinioClient minioClient;
    private final String bucket;
    private final String basePath;

    public S3ArtifactsAdapter(@Qualifier("getArtifactsClient") MinioClient minioClient,
        S3ClientsConfigurations s3ClientsConfigurations) {
        this.minioClient = minioClient;
        this.bucket = s3ClientsConfigurations.getArtifactsBucket();
        this.basePath = s3ClientsConfigurations.getArtifactsBasePath();
    }

    public void createBucketIfDoesNotExist() {
        S3AdapterUtil.createBucketIfDoesNotExist(minioClient, bucket);
    }

    /**
     * File content must be smaller than 5 MB. Just call if you are absolutely sure about that,
     * otherwise call uploadFile passing TmpFile instead of InputStream
     */
    @WithSpan("uploadFileToS3")
    public void uploadSmallFile(@SpanAttribute("pathDestination") String pathDestination,
        InputStream sourceInputStream) {
        S3AdapterUtil.uploadFile(minioClient, basePath + "/" + pathDestination, sourceInputStream,
            bucket, -1);
    }

    @WithSpan("uploadFileToS3")
    public void uploadFile(@SpanAttribute("pathDestination") String pathDestination,
        TmpFile source) {
        S3AdapterUtil.uploadFile(minioClient, basePath + "/" + pathDestination,
            source.getReadStream(), bucket, source.getTempFile().length());
    }

    @WithSpan("generatePreSignedUrl")
    public String generatePreSignedUrl(@SpanAttribute("minioPath") String minioPath) {
        return S3AdapterUtil.generatePreSignedUrl(minioClient, basePath + "/" + minioPath, bucket);
    }

    public String createRaoResultDestination(String timestamp, String borderName) {
        OffsetDateTime offsetDateTime = OffsetDateTime.parse(timestamp);
        return "artifacts" + "/" + offsetDateTime.getYear() + "/" + offsetDateTime.getMonthValue()
            + "/" + offsetDateTime.getDayOfMonth() + "/" + offsetDateTime.getHour() + "_"
            + offsetDateTime.getMinute() + "/" + borderName + "-rao-result.json";
    }

}
