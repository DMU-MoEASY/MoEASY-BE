package com.moeasy.moeasybe.global.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AwsS3PropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void bindsS3Properties() {
        withValidProperties("PT5M", "PT15M").run(context -> {
            AwsS3Properties properties = context.getBean(AwsS3Properties.class);

            assertEquals("ap-northeast-2", properties.getRegion());
            assertEquals("test-bucket", properties.getS3().getBucket());
            assertEquals(Duration.ofMinutes(5), properties.getS3().getUploadPresignedUrlExpiration());
            assertEquals(Duration.ofMinutes(15), properties.getS3().getDownloadPresignedUrlExpiration());
        });
    }

    @Test
    void rejectsZeroUploadExpiration() {
        assertContextFails("PT0S", "PT15M");
    }

    @Test
    void rejectsNegativeDownloadExpiration() {
        assertContextFails("PT5M", "PT-1S");
    }

    @Test
    void rejectsExpirationLongerThanSevenDays() {
        assertContextFails("P8D", "PT15M");
    }

    @Test
    void rejectsMissingBucket() {
        contextRunner.withPropertyValues(
                "aws.region=ap-northeast-2",
                "aws.s3.upload-presigned-url-expiration=PT5M",
                "aws.s3.download-presigned-url-expiration=PT15M"
        ).run(context -> assertTrue(context.getStartupFailure() != null));
    }

    @Test
    void rejectsMissingRegion() {
        contextRunner.withPropertyValues(
                "aws.s3.bucket=test-bucket",
                "aws.s3.upload-presigned-url-expiration=PT5M",
                "aws.s3.download-presigned-url-expiration=PT15M"
        ).run(context -> assertTrue(context.getStartupFailure() != null));
    }

    @Test
    void rejectsMissingExpiration() {
        contextRunner.withPropertyValues(
                "aws.region=ap-northeast-2",
                "aws.s3.bucket=test-bucket"
        ).run(context -> assertTrue(context.getStartupFailure() != null));
    }

    @Test
    void acceptsMinimumExpirationBoundary() {
        withValidProperties("PT1S", "PT1S").run(context -> assertTrue(context.isRunning()));
    }

    @Test
    void acceptsMaximumExpirationBoundary() {
        withValidProperties("P7D", "P7D").run(context -> assertTrue(context.isRunning()));
    }

    private void assertContextFails(String uploadExpiration, String downloadExpiration) {
        withValidProperties(uploadExpiration, downloadExpiration)
                .run(context -> assertTrue(context.getStartupFailure() != null));
    }

    private ApplicationContextRunner withValidProperties(
            String uploadExpiration,
            String downloadExpiration
    ) {
        return contextRunner.withPropertyValues(
                "aws.region=ap-northeast-2",
                "aws.s3.bucket=test-bucket",
                "aws.s3.upload-presigned-url-expiration=" + uploadExpiration,
                "aws.s3.download-presigned-url-expiration=" + downloadExpiration
        );
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AwsS3Properties.class)
    static class PropertiesConfiguration {
    }
}
