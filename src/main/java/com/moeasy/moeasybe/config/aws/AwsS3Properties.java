package com.moeasy.moeasybe.config.aws;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "aws")
public class AwsS3Properties {

    private String region;
    private S3 s3 = new S3();

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public S3 getS3() {
        return s3;
    }

    public void setS3(S3 s3) {
        this.s3 = s3;
    }

    public static class S3 {

        private String bucket;
        private Duration uploadPresignedUrlExpiration;
        private Duration downloadPresignedUrlExpiration;

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public Duration getUploadPresignedUrlExpiration() {
            return uploadPresignedUrlExpiration;
        }

        public void setUploadPresignedUrlExpiration(Duration uploadPresignedUrlExpiration) {
            this.uploadPresignedUrlExpiration = uploadPresignedUrlExpiration;
        }

        public Duration getDownloadPresignedUrlExpiration() {
            return downloadPresignedUrlExpiration;
        }

        public void setDownloadPresignedUrlExpiration(Duration downloadPresignedUrlExpiration) {
            this.downloadPresignedUrlExpiration = downloadPresignedUrlExpiration;
        }
    }
}
