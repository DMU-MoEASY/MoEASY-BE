package com.moeasy.moeasybe.storage;

import java.net.URI;

import org.springframework.stereotype.Service;

import com.moeasy.moeasybe.global.config.AwsS3Properties;
import com.moeasy.moeasybe.storage.code.StorageErrorCode;
import com.moeasy.moeasybe.storage.exception.StorageValidationException;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/** 애플리케이션 스토리지를 위한 Presigned S3 URL 생성 및 객체 삭제를 제공합니다. */
@Service
public class S3StorageService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final AwsS3Properties properties;

    /**
     * S3 기반 스토리지 서비스를 생성합니다.
     *
     * @param s3Client 객체 삭제에 사용하는 S3 클라이언트
     * @param s3Presigner 임시 PUT/GET URL 생성에 사용하는 Presigner
     * @param properties 검증된 S3 설정
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
     * 주어진 Content-Type을 요청에 포함한 임시 PUT URL을 생성합니다.
     *
     * @param objectKey 업로드할 객체의 키
     * @param contentType PUT 요청에 서명할 Content-Type
     * @return 임시 S3 PUT URL
     * @throws StorageValidationException object key 또는 content type이 null이거나 공백인 경우
     */
    public URI createPresignedPutUrl(String objectKey, String contentType) {
        String key = requireText(objectKey, StorageErrorCode.INVALID_OBJECT_KEY);
        String type = requireText(contentType, StorageErrorCode.INVALID_CONTENT_TYPE);

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
     * 객체에 대한 임시 GET URL을 생성합니다.
     *
     * @param objectKey 조회할 객체의 키
     * @return 임시 S3 GET URL
     * @throws StorageValidationException object key가 null이거나 공백인 경우
     */
    public URI createPresignedGetUrl(String objectKey) {
        String key = requireText(objectKey, StorageErrorCode.INVALID_OBJECT_KEY);

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
     * 객체 키를 기준으로 삭제를 요청합니다.
     *
     * @param objectKey 삭제할 객체의 키
     * @throws StorageValidationException object key가 null이거나 공백인 경우
     */
    public void deleteObject(String objectKey) {
        String key = requireText(objectKey, StorageErrorCode.INVALID_OBJECT_KEY);

        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.getS3().getBucket())
                .key(key)
                .build());
    }

    private String requireText(String value, StorageErrorCode errorCode) {
        if (value == null) {
            throw new StorageValidationException(errorCode);
        }
        if (value.isBlank()) {
            throw new StorageValidationException(errorCode);
        }
        return value;
    }
}
