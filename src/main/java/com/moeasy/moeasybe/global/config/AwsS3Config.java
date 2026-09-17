package com.moeasy.moeasybe.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** 애플리케이션에서 사용하는 S3 클라이언트와 Presigner를 구성합니다. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AwsS3Properties.class)
public class AwsS3Config {

    /** S3 인프라 설정을 생성합니다. */
    public AwsS3Config() {
    }

    /**
     * AWS SDK 기본 자격 증명 제공자 체인을 통해 AWS 자격 증명을 제공합니다.
     *
     * @return AWS 자격 증명 제공자
     */
    @Bean
    public AwsCredentialsProvider awsCredentialsProvider() {
        return DefaultCredentialsProvider.create();
    }

    /**
     * 검증된 애플리케이션 설정으로 AWS Region을 생성합니다.
     *
     * @param properties 검증된 AWS S3 설정
     * @return 구성된 AWS Region
     */
    @Bean
    public Region awsRegion(AwsS3Properties properties) {
        return Region.of(properties.getRegion());
    }

    /**
     * 객체 작업에 사용하는 S3 클라이언트를 생성합니다.
     *
     * @param region 구성된 AWS Region
     * @param credentialsProvider AWS 자격 증명 제공자
     * @return 객체 작업에 사용하는 S3 클라이언트
     */
    @Bean(destroyMethod = "close")
    public S3Client s3Client(Region region, AwsCredentialsProvider credentialsProvider) {
        return S3Client.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();
    }

    /**
     * 임시 PUT 및 GET URL 생성에 사용하는 S3 Presigner를 생성합니다.
     *
     * @param region 구성된 AWS Region
     * @param credentialsProvider AWS 자격 증명 제공자
     * @return 임시 PUT 및 GET URL 생성에 사용하는 S3 Presigner
     */
    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner(Region region, AwsCredentialsProvider credentialsProvider) {
        return S3Presigner.builder()
                .region(region)
                .credentialsProvider(credentialsProvider)
                .build();
    }
}
