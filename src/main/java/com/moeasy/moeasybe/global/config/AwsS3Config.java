package com.moeasy.moeasybe.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AwsS3Properties.class)
public class AwsS3Config {

    @Bean
    public AwsCredentialsProvider awsCredentialsProvider() {
        return DefaultCredentialsProvider.create();
    }

    @Bean
    public Region awsRegion(AwsS3Properties properties) {
        return Region.of(properties.getRegion());
    }

    @Bean(destroyMethod = "close")
    public S3Client s3Client(Region region, AwsCredentialsProvider credentialsProvider) {
        return S3Client.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner(Region region, AwsCredentialsProvider credentialsProvider) {
        return S3Presigner.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();
    }
}
