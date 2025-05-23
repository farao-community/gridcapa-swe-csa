package com.farao_community.farao.swe_csa.app.s3;

import io.minio.*;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.beans.factory.annotation.Qualifier;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class S3ArtifactsAdapterTest {

    @Mock
    @Qualifier("getArtifactsClient")
    private MinioClient minioClient;

    @Mock
    private S3ClientsConfigurations s3ClientsConfigurations;

    private S3ArtifactsAdapter s3ArtifactsAdapter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(s3ClientsConfigurations.getArtifactsBucket()).thenReturn("test-bucket");
        when(s3ClientsConfigurations.getArtifactsBasePath()).thenReturn("test-base-path");
        s3ArtifactsAdapter = new S3ArtifactsAdapter(minioClient, s3ClientsConfigurations);
    }

    @Test
    void testCreateBucketIfDoesNotExist() throws Exception {
        String bucketName = "test-bucket";
        when(minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())).thenReturn(false);
        s3ArtifactsAdapter.createBucketIfDoesNotExist();
        verify(minioClient, times(1)).makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
    }

    @Test
    void testCreateRaoResultDestination() {
        String timestamp = "2025-05-23T11:29:11+02:00";
        String borderName = "PT-ES";
        String expected = "artifacts/2025/5/23/11_29/PT-ES-rao-result.json";
        String result = s3ArtifactsAdapter.createRaoResultDestination(timestamp, borderName);
        assertEquals(expected, result);
    }
}
