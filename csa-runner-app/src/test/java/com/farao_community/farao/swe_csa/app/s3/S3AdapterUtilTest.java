package com.farao_community.farao.swe_csa.app.s3;

import com.farao_community.farao.swe_csa.api.exception.CsaInternalException;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

class S3AdapterUtilTest {
    @Test
    void checkBucketCreatedCorrectly() {
        try {
            MinioClient minioClient = Mockito.mock(MinioClient.class);
            Mockito.when(minioClient.bucketExists(Mockito.any())).thenReturn(true);
            Assertions.assertDoesNotThrow(() -> S3AdapterUtil.createBucketIfDoesNotExist(minioClient, "bucket"));
        } catch (Exception e) {
            // should not happen
        }
        try {
            MinioClient badMinioClient = Mockito.mock(MinioClient.class);
            Mockito.when(badMinioClient.bucketExists(Mockito.any())).thenThrow(IOException.class);
            Assertions.assertThrows(CsaInternalException.class, () -> S3AdapterUtil.createBucketIfDoesNotExist(badMinioClient, "bucket"));
        } catch (Exception e) {
            // should not happen
        }
    }

    @Test
    void checkUpload() {
        try {
            MinioClient minioClient = Mockito.mock(MinioClient.class);
            Mockito.when(minioClient.bucketExists(Mockito.argThat(assertBucketExistsArgs()))).thenReturn(false);

            S3AdapterUtil.uploadFile(minioClient, "/path", new ByteArrayInputStream("content".getBytes()), "bucket");
            InOrder inOrder = Mockito.inOrder(minioClient);

            inOrder.verify(minioClient, Mockito.times(1))
                .bucketExists(Mockito.argThat(
                    assertBucketExistsArgs()
                ));

            inOrder.verify(minioClient, Mockito.times(1))
                .makeBucket(Mockito.argThat(
                    assertMakeBucketArgs()
                ));
            inOrder.verify(minioClient, Mockito.times(1))
                .putObject(Mockito.argThat(
                    assertPutObjectArgs())
                );
        } catch (Exception e) {
            // should not happen

        }
    }

    private ArgumentMatcher<BucketExistsArgs> assertBucketExistsArgs() {
        return bucketExistsArgs -> bucketExistsArgs.bucket().equals("bucket");
    }

    private ArgumentMatcher<MakeBucketArgs> assertMakeBucketArgs() {
        return makeBucketArgs -> makeBucketArgs.bucket().equals("bucket");
    }

    private ArgumentMatcher<PutObjectArgs> assertPutObjectArgs() {
        return putObjectArgs ->
            putObjectArgs.bucket().equals("bucket") &&
                putObjectArgs.object().equals("/path") &&
                streamContentEquals(putObjectArgs.stream());
    }

    private boolean streamContentEquals(InputStream inputStream) {
        try {
            inputStream.mark(-1);
            boolean contentIsAsExpected = new String(inputStream.readAllBytes()).equals("content");
            inputStream.reset();
            return contentIsAsExpected;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
