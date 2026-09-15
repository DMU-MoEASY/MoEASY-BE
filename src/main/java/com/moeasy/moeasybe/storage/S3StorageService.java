package com.moeasy.moeasybe.storage;

import java.net.URI;

import org.springframework.stereotype.Service;

import com.moeasy.moeasybe.config.aws.AwsS3Properties;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
public class S3StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final AwsS3Properties properties;

    public S3StorageService(
            S3Client s3Client,
            S3Presigner s3Presigner,
            AwsS3Properties properties
    ) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.properties = properties;
    }

    public URI createPresignedPutUrl(String objectKey, String contentType) {
        String key = requireText(objectKey, "objectKey");
        String type = requireText(contentType, "contentType");

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.getS3().getBucket())
                .key(key)
                .contentType(type)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(properties.getS3().getUploadPresignedUrlExpiration())
                .putObjectRequest(putObjectRequest)
                .build();

        return URI.create(s3Presigner.presignPutObject(presignRequest).url().toString());
    }

    public URI createPresignedGetUrl(String objectKey) {
        String key = requireText(objectKey, "objectKey");

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(properties.getS3().getBucket())
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(properties.getS3().getDownloadPresignedUrlExpiration())
                .getObjectRequest(getObjectRequest)
                .build();

        return URI.create(s3Presigner.presignGetObject(presignRequest).url().toString());
    }

    public void deleteObject(String objectKey) {
        String key = requireText(objectKey, "objectKey");

        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.getS3().getBucket())
                .key(key)
                .build());
    }

    private String requireText(String value, String fieldName) {
        if (value == null) {
            throw new NullPointerException(fieldName + " must not be null");
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
