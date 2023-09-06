package com.farao_community.farao.gridcapa.swe_csa;

import io.minio.*;
import io.minio.http.Method;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
public class MinioAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(MinioAdapter.class);
    private static final int DEFAULT_DOWNLOAD_LINK_EXPIRY_IN_DAYS = 7;
    private final MinioClient minioClient;
    private final String defaultBucket;

    public MinioAdapter(MinioConfiguration minioConfiguration, MinioClient minioClient) {
        this.minioClient = minioClient;
        this.defaultBucket = minioConfiguration.getDefaultBucket();
    }

    public String getDefaultBucket() {
        return defaultBucket;
    }

    public void createBucketIfDoesNotExist() {
        try {
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(defaultBucket).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(defaultBucket).build());
            }
        } catch (Exception e) {
            LOGGER.error(String.format("Exception occurred while creating bucket: %s", defaultBucket));
            throw new RuntimeException(String.format("Exception occurred while creating bucket: %s", defaultBucket));
        }
    }

    public void createBucketIfDoesNotExist(String bucketName) {
        try {
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            }
        } catch (Exception e) {
            LOGGER.error(String.format("Exception occurred while creating bucket: %s", bucketName));
            throw new RuntimeException(String.format("Exception occurred while creating bucket: %s", bucketName));
        }
    }

    public void uploadFile(String pathDestination, InputStream sourceInputStream) {
        try {
            createBucketIfDoesNotExist();
            minioClient.putObject(PutObjectArgs.builder().bucket(defaultBucket).object(pathDestination).stream(sourceInputStream, -1, 50000000).build());
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(String.format("Exception occurred while uploading file: %s, to minio server", pathDestination));
        }
    }

    public void uploadFile(String bucket, String pathDestination, InputStream sourceInputStream) {
        try {
            createBucketIfDoesNotExist(bucket);
            minioClient.putObject(PutObjectArgs.builder().bucket(bucket).object(pathDestination).stream(sourceInputStream, -1, 50000000).build());
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(String.format("Exception occurred while uploading file: %s, to minio server", pathDestination));
        }
    }

    public String generatePreSignedUrl(String minioPath) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().bucket(defaultBucket).object(minioPath).expiry(DEFAULT_DOWNLOAD_LINK_EXPIRY_IN_DAYS, TimeUnit.DAYS).method(Method.GET).build());
        } catch (Exception e) {
            throw new RuntimeException("Exception in MinIO connection.", e);
        }
    }

    public InputStream getInputStreamFromUrl(String url) {
        try {
            return new URL(url).openStream(); //NOSONAR
        } catch (IOException e) {
            throw new RuntimeException(String.format("Exception occurred while retrieving file content from : %s Cause: %s ", url, e.getMessage()));
        }
    }

    public String getFileNameFromUrl(String stringUrl) {
        try {
            URL url = new URL(stringUrl);
            return FilenameUtils.getName(url.getPath());
        } catch (IOException e) {
            throw new RuntimeException(String.format("Exception occurred while retrieving file name from : %s Cause: %s ", stringUrl, e.getMessage()));
        }
    }

    public void copyObject(String sourceObject, String destinationObject, String sourceBucket, String destinationBucket) {
        try {
            minioClient.copyObject(
                    CopyObjectArgs.builder()
                            .bucket(destinationBucket)
                            .object(destinationObject)
                            .source(CopySource.builder().object(sourceObject).bucket(sourceBucket).build())
                            .build());
        } catch (Exception e) {
            throw new RuntimeException(String.format("Exception occurred while copying object '%s' from bucket: '%s' to bucket: '%s', Cause: %s ", sourceObject, sourceBucket, destinationBucket, e.getMessage()), e);
        }
    }

    public Path copyFileInTargetSystemPath(String minioObjectName, Path targetTempPath) {
        try (InputStream raoRequestInputStream = Optional.of(minioClient.getObject(GetObjectArgs
                .builder()
                .bucket(defaultBucket)
                .object(minioObjectName)
                .build())).get()) {
            String shortFileName = FilenameUtils.getName(minioObjectName);
            File file = new File(targetTempPath.toString(), shortFileName); //NOSONAR
            Files.copy(raoRequestInputStream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return file.toPath();
        } catch (Exception e) {
            String message = String.format("Cannot retrieve file '%s'", minioObjectName);
            throw new RuntimeException(message, e);
        }
    }

}
