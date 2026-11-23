package com.razor.api.service.storage;

import com.razor.api.config.RazorProperties;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * Cliente S3 so quando {@code razor.storage.type=s3}. Com {@code db} (padrao) nada daqui e
 * criado e a aplicacao nao precisa de AWS nenhuma para rodar.
 *
 * <p>Credenciais: {@code RAZOR_S3_ACCESS_KEY}/{@code RAZOR_S3_SECRET_KEY} quando definidas; senao
 * a cadeia padrao da AWS (variaveis {@code AWS_*}, perfil, IAM role da maquina). Nunca no repo.
 */
@Configuration
@ConditionalOnProperty(prefix = "razor.storage", name = "type", havingValue = "s3")
public class S3StorageConfig {

    @Bean(destroyMethod = "close")
    public S3Client razorS3Client(RazorProperties properties) {
        RazorProperties.Storage.S3 cfg = properties.getStorage().getS3();
        if (cfg.getBucket() == null || cfg.getBucket().isBlank()) {
            // Falha no boot e melhor que "foto nao subiu" no primeiro upload do dono.
            throw new IllegalStateException("razor.storage.type=s3 exige razor.storage.s3.bucket (RAZOR_S3_BUCKET).");
        }
        S3ClientBuilder builder = S3Client.builder()
                .httpClient(UrlConnectionHttpClient.create())
                .region(Region.of(cfg.getRegion()))
                .credentialsProvider(credentials(cfg))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(cfg.isPathStyle()).build());
        if (cfg.getEndpoint() != null && !cfg.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(cfg.getEndpoint().trim()));
        }
        return builder.build();
    }

    @Bean
    public S3ImageStorage s3ImageStorage(S3Client razorS3Client, RazorProperties properties) {
        RazorProperties.Storage.S3 cfg = properties.getStorage().getS3();
        return new S3ImageStorage(razorS3Client, cfg.getBucket().trim(), cfg.getPrefix());
    }

    private static AwsCredentialsProvider credentials(RazorProperties.Storage.S3 cfg) {
        boolean explicit = cfg.getAccessKey() != null && !cfg.getAccessKey().isBlank()
                && cfg.getSecretKey() != null && !cfg.getSecretKey().isBlank();
        return explicit
                ? StaticCredentialsProvider.create(AwsBasicCredentials.create(cfg.getAccessKey(), cfg.getSecretKey()))
                : DefaultCredentialsProvider.create();
    }
}
