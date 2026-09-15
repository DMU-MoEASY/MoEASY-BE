package com.moeasy.moeasybe.config.aws;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AwsS3PropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfiguration.class)
            .withPropertyValues(
                    "aws.region=ap-northeast-2",
                    "aws.s3.bucket=test-bucket",
                    "aws.s3.upload-presigned-url-expiration=PT5M",
                    "aws.s3.download-presigned-url-expiration=PT15M"
            );

    @Test
    void bindsS3Properties() {
        contextRunner.run(context -> {
            AwsS3Properties properties = context.getBean(AwsS3Properties.class);

            assertEquals("ap-northeast-2", properties.getRegion());
            assertEquals("test-bucket", properties.getS3().getBucket());
            assertEquals(Duration.ofMinutes(5), properties.getS3().getUploadPresignedUrlExpiration());
            assertEquals(Duration.ofMinutes(15), properties.getS3().getDownloadPresignedUrlExpiration());
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AwsS3Properties.class)
    static class PropertiesConfiguration {
    }
}
