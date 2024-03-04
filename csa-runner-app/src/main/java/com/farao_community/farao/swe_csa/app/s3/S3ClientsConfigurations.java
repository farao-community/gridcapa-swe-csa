package com.farao_community.farao.swe_csa.app.s3;

import com.farao_community.farao.swe_csa.api.exception.CsaInternalException;
import io.minio.MinioClient;
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

    @Value("${s3.artifacts.user}")
    private String artifactsAccessKey;
    @Value("${s3.artifacts.secret}")
    private String artifactsAccessSecret;
    @Value("${s3.artifacts.url}")
    private String artifactsS3Url;
    @Value("${s3.artifacts.bucket}")
    private String artifactsBucket;

    @Value("${s3.outputs.user}")
    private String outputsAccessKey;
    @Value("${s3.outputs.secret}")
    private String outputsAccessSecret;
    @Value("${s3.outputs.url}")
    private String outputsS3Url;
    @Value("${s3.outputs.bucket}")
    private String outputsBucket;

    @Bean
    public MinioClient getInputsClient() {
        try {
            return MinioClient.builder().endpoint(inputsS3Url).credentials(inputsAccessKey, inputsAccessSecret).build();
        } catch (Exception e) {
            throw new CsaInternalException("Exception in MinIO client", e);
        }
    }

    @Bean
    public MinioClient getArtifactsClient() {
        try {
            return MinioClient.builder().endpoint(artifactsS3Url).credentials(artifactsAccessKey, artifactsAccessSecret).build();
        } catch (Exception e) {
            throw new CsaInternalException("Exception in MinIO client", e);
        }
    }

    @Bean
    public MinioClient getOutputsClient() {
        try {
            return MinioClient.builder().endpoint(outputsS3Url).credentials(outputsAccessKey, outputsAccessSecret).build();
        } catch (Exception e) {
            throw new CsaInternalException("Exception in MinIO client", e);
        }
    }

    public String getInputsBucket() {
        return inputsBucket;
    }

    public String getOutputsBucket() {
        return outputsBucket;
    }

    public String getArtifactsBucket() {
        return artifactsBucket;
    }

}
