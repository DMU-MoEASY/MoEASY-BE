package com.moeasy.moeasybe.config.aws;

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

@ConfigurationProperties(prefix = "aws")
@Validated
@Getter
@Setter
public class AwsS3Properties {

    @NotBlank
    private String region;

    @Valid
    private S3 s3 = new S3();

    @Getter
    @Setter
    public static class S3 {

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
