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

/** 애플리케이션에서 사용하는 AWS S3 설정을 바인딩하고 검증합니다. */
@ConfigurationProperties(prefix = "aws")
@Validated
@Getter
@Setter
public class AwsS3Properties {

    /** 중첩 설정 객체를 포함한 AWS S3 설정을 생성합니다. */
    public AwsS3Properties() {
    }

    @NotBlank
    private String region;

    @Valid
    private S3 s3 = new S3();

    /** Bucket과 Presigned URL 만료 시간 설정을 포함합니다. */
    @Getter
    @Setter
    public static class S3 {

        /** 중첩된 S3 설정을 생성합니다. */
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
