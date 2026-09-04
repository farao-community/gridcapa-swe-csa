package com.farao_community.farao.swe_csa.app.s3;

import com.farao_community.farao.swe_csa.app.utils.TmpFile;
import io.minio.MinioClient;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class S3ArtifactsAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3ArtifactsAdapter.class);

    private final MinioClient minioClient;
    private final String bucket;
    private final String basePath;

    public S3ArtifactsAdapter(@Qualifier("getArtifactsClient") MinioClient minioClient, S3ClientsConfigurations s3ClientsConfigurations) {
        this.minioClient = minioClient;
        this.bucket = s3ClientsConfigurations.getArtifactsBucket();
        this.basePath = s3ClientsConfigurations.getArtifactsBasePath();
    }

    public void createBucketIfDoesNotExist() {
        S3AdapterUtil.createBucketIfDoesNotExist(minioClient, bucket);
    }

    public void uploadFile(String pathDestination, TmpFile source) {
        S3AdapterUtil.uploadFile(minioClient, basePath + "/" + pathDestination,
                source.getReadStream(), bucket, source.getTempFile().length());
    }

    public String generatePreSignedUrl(String minioPath) {
        return S3AdapterUtil.generatePreSignedUrl(minioClient, basePath + "/" + minioPath, bucket);
    }

    public String createRaoResultDestination(String timestamp, String borderName) {
        OffsetDateTime offsetDateTime = OffsetDateTime.parse(timestamp);
        return "artifacts" + "/" + offsetDateTime.getYear() + "/" + offsetDateTime.getMonthValue() + "/" + offsetDateTime.getDayOfMonth() + "/" + offsetDateTime.getHour() + "_" + offsetDateTime.getMinute() + "/"  + borderName + "-rao-result.json";
    }

}
