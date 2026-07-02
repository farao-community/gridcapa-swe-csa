package com.farao_community.farao.swe_csa.app.s3;

import com.farao_community.farao.swe_csa.api.exception.CsaInternalException;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.MinioException;
import io.minio.http.Method;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class S3AdapterUtil {

    private S3AdapterUtil() {
        //util shouldn't be constructed
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(S3AdapterUtil.class);

    private static final DateTimeFormatter UPLOAD_TMP_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final long UPLOAD_PART_SIZE = 5 * 1024L * 1024L; // 5 MB
    private static final int DEFAULT_DOWNLOAD_LINK_EXPIRY_IN_DAYS = 7;

    public static void createBucketIfDoesNotExist(MinioClient minioClient, String bucket) {
        try {
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            LOGGER.error(String.format("Exception occurred while creating bucket: %s", bucket));
            throw new CsaInternalException(MDC.get("gridcapaTaskId"), String.format("Exception occurred while creating bucket: %s", bucket));
        }
    }

    /**
     * Note: sourceInputStream will be closed after uploading
     */
    public static void uploadFile(MinioClient minioClient, String pathDestination,
                                  InputStream sourceInputStream, String bucket, long fileSize) {

        var startedAt = System.currentTimeMillis();
        LOGGER.debug("Uploading file. Bucket={}. Path={}. Size={}", bucket, pathDestination,
                fileSize);

        final String tmpKey = getUploadTmpPath(pathDestination);

        createBucketIfDoesNotExist(minioClient, bucket);

        try {

            try {

                // Upload into temporary bucket. this is parallel-safe
                LOGGER.debug("Using TMP file: {}", tmpKey);
                try (InputStream in = sourceInputStream) {
                    var uploadOperation = PutObjectArgs.builder().bucket(bucket).object(tmpKey)
                            .stream(in, fileSize, UPLOAD_PART_SIZE).build();
                    minioClient.putObject(uploadOperation);
                }

                // Copy tmp object to final bucket. this is atomic, last writer wins
                var tmpSource = CopySource.builder().bucket(bucket).object(tmpKey).build();
                var copyOperation = CopyObjectArgs.builder().bucket(bucket).object(pathDestination)
                        .source(tmpSource).build();
                safeCopy(minioClient, copyOperation, pathDestination);

            } finally {
                try {
                    // Delete temp object
                    var deleteOperation = RemoveObjectArgs.builder().bucket(bucket).object(tmpKey).build();
                    minioClient.removeObject(deleteOperation);
                } catch (Exception e) {
                    LOGGER.warn("Error removing TMP file", e);
                }
            }

            LOGGER.debug("Upload done. Destination={}. Size={}. Time={}", pathDestination, fileSize,
                    System.currentTimeMillis() - startedAt);

        } catch (Exception e) {
            LOGGER.error("Error uploading to {}: {}", pathDestination, e.getMessage(), e);
            throw new CsaInternalException(MDC.get("gridcapaTaskId"),
                    String.format("Exception occurred while uploading file: %s, to minio server",
                            pathDestination));
        }
    }

    private static void safeCopy(MinioClient minioClient, CopyObjectArgs copyArgs, String key)
            throws MinioException, IOException, NoSuchAlgorithmException, InvalidKeyException {

        long maxWaitMillis = 3 * 60 * 1000;
        long waitMillis = 500;
        long startTime = System.currentTimeMillis();
        long numRetries = 0;

        while (true) {
            try {
                if (numRetries > 0) {
                    LOGGER.debug("Retrying copy operation for file {}. RetryCount={}", key, numRetries);
                }
                minioClient.copyObject(copyArgs);
                break;
            } catch (ErrorResponseException e) {
                if ("OperationAborted".equalsIgnoreCase(e.errorResponse().code())) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    if (elapsed >= maxWaitMillis) {
                        throw new RuntimeException("Copy timeout", e);
                    }
                    LOGGER.debug("OperationAborted for file {}. Will retry in {} ms", key, waitMillis);
                    numRetries++;
                    sleep(waitMillis);
                } else {
                    throw e;
                }
            }
        }

    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    private static String getUploadTmpPath(String pathDestination) {
        String now = LocalDateTime.now().format(UPLOAD_TMP_NAME_FORMAT);
        var rand = UUID.randomUUID().toString().replace("-", "").substring(12);
        return String.format("tmp/uploads/%s/%s.%s", now, rand, pathDestination);
    }

    public static String generatePreSignedUrl(MinioClient minioClient, String minioPath, String bucket) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().bucket(bucket).object(minioPath).expiry(DEFAULT_DOWNLOAD_LINK_EXPIRY_IN_DAYS, TimeUnit.DAYS).method(Method.GET).build());
        } catch (Exception e) {
            throw new CsaInternalException(MDC.get("gridcapaTaskId"), "Exception in MinIO connection.", e);
        }
    }

    public static Path copyFileInTargetSystemPath(MinioClient minioClient, String minioObjectName, Path targetTempPath, String bucket) {
        try (InputStream raoRequestInputStream = Optional.of(minioClient.getObject(GetObjectArgs
            .builder()
            .bucket(bucket)
            .object(minioObjectName)
            .build())).get()) {
            String shortFileName = FilenameUtils.getName(minioObjectName);
            File file = new File(targetTempPath.toString(), shortFileName); //NOSONAR
            Files.copy(raoRequestInputStream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return file.toPath();
        } catch (Exception e) {
            String message = String.format("Cannot retrieve file '%s'", minioObjectName);
            throw new CsaInternalException(MDC.get("gridcapaTaskId"), message, e);
        }
    }

}
