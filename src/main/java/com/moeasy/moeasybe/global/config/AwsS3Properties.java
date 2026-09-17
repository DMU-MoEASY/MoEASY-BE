package com.moeasy.moeasybe.global.config;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;

/** Binds and validates the AWS S3 settings used by the application. */
@ConfigurationProperties(prefix = "aws")
@Validated
@Getter
@Setter
public class AwsS3Properties {

    /** Creates the AWS S3 properties with its nested settings container. */
    public AwsS3Properties() {
    }

    @NotBlank
    private String region;

    @Valid
    private S3 s3 = new S3();

    /** Contains the bucket and presigned URL expiration settings. */
    @Getter
    @Setter
    public static class S3 {

        /** Creates the nested S3 settings. */
        public S3() {
        }

        @NotBlank
        private String bucket;

        @NotNull
        @DurationMin(seconds = 1)
        @DurationMax(days = 7)
        private Duration uploadPresignedUrlExpiration;

        @NotNull
        @DurationMin(seconds = 1)
        @DurationMax(days = 7)
        private Duration downloadPresignedUrlExpiration;
    }
}
