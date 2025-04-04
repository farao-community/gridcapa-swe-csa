package com.farao_community.farao.swe_csa.app.s3;

import com.farao_community.farao.swe_csa.api.exception.CsaInternalException;
import io.minio.MinioClient;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class S3ClientsConfigurations {

    @Value("${s3.inputs.user}")
    private String inputsAccessKey;
    @Value("${s3.inputs.secret}")
    private String inputsAccessSecret;
    @Value("${s3.inputs.url}")
    private String inputsS3Url;
    @Value("${s3.inputs.bucket}")
    private String inputsBucket;
    @Value("${s3.inputs.base-path}")
    private String inputsBasePath;

    @Value("${s3.artifacts.user}")
    private String artifactsAccessKey;
    @Value("${s3.artifacts.secret}")
    private String artifactsAccessSecret;
    @Value("${s3.artifacts.url}")
    private String artifactsS3Url;
    @Value("${s3.artifacts.bucket}")
    private String artifactsBucket;
    @Value("${s3.artifacts.base-path}")
    private String artifactsBasePath;

    @Value("${s3.outputs.user}")
    private String outputsAccessKey;
    @Value("${s3.outputs.secret}")
    private String outputsAccessSecret;
    @Value("${s3.outputs.url}")
    private String outputsS3Url;
    @Value("${s3.outputs.bucket}")
    private String outputsBucket;
    @Value("${s3.outputs.base-path}")
    private String outputsBasePath;

    private final String minioClientException = "Exception in MinIO client";
    private final String gridcapaTaskId = "gridcapaTaskId";

    @Bean
    public MinioClient getInputsClient() {
        try {
            return MinioClient.builder().endpoint(inputsS3Url).credentials(inputsAccessKey, inputsAccessSecret).build();
        } catch (Exception e) {
            throw new CsaInternalException(MDC.get(gridcapaTaskId), minioClientException, e);
        }
    }

    @Bean
    public MinioClient getArtifactsClient() {
        try {
            return MinioClient.builder().endpoint(artifactsS3Url).credentials(artifactsAccessKey, artifactsAccessSecret).build();
        } catch (Exception e) {
            throw new CsaInternalException(MDC.get(gridcapaTaskId), minioClientException, e);
        }
    }

    @Bean
    public MinioClient getOutputsClient() {
        try {
            return MinioClient.builder().endpoint(outputsS3Url).credentials(outputsAccessKey, outputsAccessSecret).build();
        } catch (Exception e) {
            throw new CsaInternalException(MDC.get(gridcapaTaskId), minioClientException, e);
        }
    }

    public String getInputsBucket() {
        return inputsBucket;
    }

    public String getInputsBasePath() {
        return inputsBasePath;
    }

    public String getOutputsBucket() {
        return outputsBucket;
    }

    public String getArtifactsBasePath() {
        return artifactsBasePath;
    }

    public String getArtifactsBucket() {
        return artifactsBucket;
    }

    public String getOutputsBasePath() {
        return outputsBasePath;
    }

}
