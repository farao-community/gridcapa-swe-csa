package com.farao_community.farao.swe_csa.app.s3;

import io.minio.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class S3InputsAdapter {

    private final MinioClient minioClient;
    private final String bucket;
    private final String basePath;

    public S3InputsAdapter(@Qualifier("getInputsClient") MinioClient minioClient, S3ClientsConfigurations s3ClientsConfigurations) {
        this.minioClient = minioClient;
        this.bucket = s3ClientsConfigurations.getInputsBucket();
        this.basePath = s3ClientsConfigurations.getInputsBasePath();
    }

    public void createBucketIfDoesNotExist() {
        S3AdapterUtil.createBucketIfDoesNotExist(minioClient, bucket);
    }

    public void uploadFile(String pathDestination, InputStream sourceInputStream) {
        createBucketIfDoesNotExist();
        S3AdapterUtil.uploadFile(minioClient, basePath + "/" + pathDestination, sourceInputStream, bucket);
    }

    public String generatePreSignedUrl(String minioPath) {
        return S3AdapterUtil.generatePreSignedUrl(minioClient, basePath + "/" + minioPath, bucket);
    }

}
