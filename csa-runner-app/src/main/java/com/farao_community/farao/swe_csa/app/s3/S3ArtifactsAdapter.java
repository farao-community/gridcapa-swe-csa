package com.farao_community.farao.swe_csa.app.s3;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.file.Path;

@Component
public class S3ArtifactsAdapter {

    private final MinioClient minioClient;
    private final String bucket;

    public S3ArtifactsAdapter(@Qualifier("getArtifactsClient") MinioClient minioClient, S3ClientsConfigurations s3ClientsConfigurations) {
        this.minioClient = minioClient;
        this.bucket = s3ClientsConfigurations.getArtifactsBucket();
    }

    public void createBucketIfDoesNotExist() {
        S3AdapterUtil.createBucketIfDoesNotExist(minioClient, bucket);
    }

    public void uploadFile(String pathDestination, InputStream sourceInputStream) {
        createBucketIfDoesNotExist();
        S3AdapterUtil.uploadFile(minioClient, pathDestination, sourceInputStream, bucket);
    }

    public String generatePreSignedUrl(String minioPath) {
        return S3AdapterUtil.generatePreSignedUrl(minioClient, minioPath, bucket);
    }

    public Path copyFileInTargetSystemPath(String minioObjectName, Path targetTempPath) {
        return S3AdapterUtil.copyFileInTargetSystemPath(minioClient, minioObjectName, targetTempPath, bucket);
    }

}
