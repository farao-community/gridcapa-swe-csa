package com.farao_community.farao.swe_csa.app.s3;

import com.farao_community.farao.swe_csa.api.exception.CsaInternalException;
import io.minio.*;
import io.minio.http.Method;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class S3AdapterUtil {

    private S3AdapterUtil() {
        //util shouldn't be constructed
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(S3AdapterUtil.class);

    private static final int DEFAULT_DOWNLOAD_LINK_EXPIRY_IN_DAYS = 7;

    public static void createBucketIfDoesNotExist(MinioClient minioClient, String bucket) {
        try {
            if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            LOGGER.error(String.format("Exception occurred while creating bucket: %s", bucket));
            throw new CsaInternalException(String.format("Exception occurred while creating bucket: %s", bucket));
        }
    }

    public static void uploadFile(MinioClient minioClient, String pathDestination, InputStream sourceInputStream, String bucket) {
        try {
            createBucketIfDoesNotExist(minioClient, bucket);
            minioClient.putObject(PutObjectArgs.builder().bucket(bucket).object(pathDestination).stream(sourceInputStream, -1, 50000000).build());
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            throw new CsaInternalException(String.format("Exception occurred while uploading file: %s, to minio server", pathDestination));
        }
    }

    public static String generatePreSignedUrl(MinioClient minioClient, String minioPath, String bucket) {
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().bucket(bucket).object(minioPath).expiry(DEFAULT_DOWNLOAD_LINK_EXPIRY_IN_DAYS, TimeUnit.DAYS).method(Method.GET).build());
        } catch (Exception e) {
            throw new CsaInternalException("Exception in MinIO connection.", e);
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
            throw new CsaInternalException(message, e);
        }
    }

}
