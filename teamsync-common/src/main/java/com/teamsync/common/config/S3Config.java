package com.teamsync.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@ConditionalOnProperty(name = "teamsync.storage.provider", havingValue = "s3")
@Slf4j
public class S3Config {

    @Value("${teamsync.storage.s3.region:us-east-1}")
    private String region;

    @Value("${teamsync.storage.s3.access-key:}")
    private String accessKey;

    @Value("${teamsync.storage.s3.secret-key:}")
    private String secretKey;

    @Bean
    public S3Client s3Client() {
        log.info("Configuring S3 client for region: {}", region);

        var builder = S3Client.builder()
                .region(Region.of(region));

        // Use explicit credentials if provided, otherwise use default credential chain
        if (accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)));
        }

        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        var builder = S3Presigner.builder()
                .region(Region.of(region));

        if (accessKey != null && !accessKey.isBlank() && secretKey != null && !secretKey.isBlank()) {
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)));
        }

        return builder.build();
    }
}
