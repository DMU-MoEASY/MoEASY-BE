package com.moeasy.moeasybe.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** Configures the S3 client and presigner used by the application. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AwsS3Properties.class)
public class AwsS3Config {

    /** Creates the S3 infrastructure configuration. */
    public AwsS3Config() {
    }

    /**
     * Provides credentials through the AWS SDK default provider chain.
     *
     * @return AWS credential provider
     */
    @Bean
    public AwsCredentialsProvider awsCredentialsProvider() {
        return DefaultCredentialsProvider.create();
    }

    /**
     * Creates the AWS region from the validated application properties.
     *
     * @param properties validated AWS S3 properties
     * @return configured AWS region
     */
    @Bean
    public Region awsRegion(AwsS3Properties properties) {
        return Region.of(properties.getRegion());
    }

    /**
     * Creates the S3 client used for direct object operations.
     *
     * @param region configured AWS region
     * @param credentialsProvider AWS credential provider
     * @return S3 client for object operations
     */
    @Bean(destroyMethod = "close")
    public S3Client s3Client(Region region, AwsCredentialsProvider credentialsProvider) {
        return S3Client.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();
    }

    /**
     * Creates the S3 presigner used for temporary PUT and GET URLs.
     *
     * @param region configured AWS region
     * @param credentialsProvider AWS credential provider
     * @return S3 presigner for temporary PUT and GET URLs
     */
    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner(Region region, AwsCredentialsProvider credentialsProvider) {
        return S3Presigner.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();
    }
}
