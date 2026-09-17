package com.moeasy.moeasybe.storage;

import java.net.URI;

import org.springframework.stereotype.Service;

import com.moeasy.moeasybe.global.config.AwsS3Properties;
import com.moeasy.moeasybe.storage.exception.StorageValidationException;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/** Provides presigned S3 URLs and object deletion for application storage. */
@Service
public class S3StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final AwsS3Properties properties;

    /**
     * Creates a storage service backed by S3.
     *
     * @param s3Client client used for object deletion
     * @param s3Presigner presigner used for temporary PUT and GET URLs
     * @param properties validated S3 configuration
     */
    public S3StorageService(
            S3Client s3Client,
            S3Presigner s3Presigner,
            AwsS3Properties properties
    ) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.properties = properties;
    }

    /**
     * Creates a temporary PUT URL whose request includes the given content type.
     *
     * @param objectKey key of the object to upload
     * @param contentType content type to sign into the PUT request
     * @return temporary S3 PUT URL
     * @throws StorageValidationException if the object key or content type is null or blank
     */
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

    /**
     * Creates a temporary GET URL for an object.
     *
     * @param objectKey key of the object to read
     * @return temporary S3 GET URL
     * @throws StorageValidationException if the object key is null or blank
     */
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

    /**
     * Requests deletion of an object by key.
     *
     * @param objectKey key of the object to delete
     * @throws StorageValidationException if the object key is null or blank
     */
    public void deleteObject(String objectKey) {
        String key = requireText(objectKey, "objectKey");

        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.getS3().getBucket())
                .key(key)
                .build());
    }

    private String requireText(String value, String fieldName) {
        if (value == null) {
            throw new StorageValidationException(fieldName + " must not be null");
        }
        if (value.isBlank()) {
            throw new StorageValidationException(fieldName + " must not be blank");
        }
        return value;
    }
}
