package com.farao_community.farao.gridcapa.swe_csa;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfiguration {

    @Value("${minio.access.name}")
    private String accessKey;
    @Value("${minio.access.secret}")
    private String accessSecret;
    @Value("${minio.url}")
    private String minioUrl;
    @Value("${minio.default-bucket}")
    private String defaultBucket;

    @Bean
    public MinioClient generateMinioClient() {
        try {
            return MinioClient.builder().endpoint(minioUrl).credentials(accessKey, accessSecret).build();
        } catch (Exception e) {
            throw new RuntimeException("Exception in MinIO client", e);
        }
    }

    public String getDefaultBucket() {
        return defaultBucket;
    }

}
