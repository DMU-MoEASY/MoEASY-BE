package com.moeasy.moeasybe.storage;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import com.moeasy.moeasybe.global.config.AwsS3Properties;
import com.moeasy.moeasybe.storage.code.StorageErrorCode;
import com.moeasy.moeasybe.storage.exception.StorageOperationException;
import com.moeasy.moeasybe.storage.exception.StorageValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/** 애플리케이션 스토리지를 위한 Presigned S3 URL 생성 및 객체 삭제를 제공합니다. */
@Service
@Slf4j
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
    public PresignedUpload createPresignedPutUrl(String objectKey, String contentType) {
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

        try {
            var presignedRequest = s3Presigner.presignPutObject(presignRequest);
            return new PresignedUpload(
                    URI.create(presignedRequest.url().toString()),
                    presignedRequest.expiration(),
                    requiredHeaders(presignedRequest.signedHeaders())
            );
        } catch (SdkException e) {
            throw unavailable(e);
        }
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

        try {
            return URI.create(s3Presigner.presignGetObject(presignRequest).url().toString());
        } catch (SdkException e) {
            throw unavailable(e);
        }
    }

    /**
     * 객체 키를 기준으로 삭제를 요청합니다.
     *
     * @param objectKey 삭제할 객체의 키
     * @throws StorageValidationException object key가 null이거나 공백인 경우
     */
    public void deleteObject(String objectKey) {
        String key = requireText(objectKey, StorageErrorCode.INVALID_OBJECT_KEY);

        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.getS3().getBucket())
                    .key(key)
                    .build());
        } catch (SdkException e) {
            throw unavailable(e);
        }
    }

    /** Returns object metadata, empty only when absence is confirmed, and maps other S3 failures to 503. */
    public Optional<ObjectMetadata> getObjectMetadata(String objectKey) {
        String key = requireText(objectKey, StorageErrorCode.INVALID_OBJECT_KEY);
        try {
            var response = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.getS3().getBucket()).key(key).build());
            return Optional.of(new ObjectMetadata(response.contentType()));
        } catch (NoSuchBucketException e) {
            throw unavailable(e);
        } catch (NoSuchKeyException e) {
            return confirmAbsenceOrFail(key, e);
        } catch (S3Exception e) {
            if (e.statusCode() == 403 || e.statusCode() == 404) {
                return confirmAbsenceOrFail(key, e);
            }
            throw unavailable(e);
        } catch (SdkException e) {
            throw unavailable(e);
        }
    }

    private Optional<ObjectMetadata> confirmAbsenceOrFail(String key, S3Exception headFailure) {
        try {
            var listing = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(properties.getS3().getBucket()).prefix(key).maxKeys(1).build());
            boolean exactKeyExists = listing.contents().stream().anyMatch(object -> key.equals(object.key()));
            if (!exactKeyExists && !Boolean.TRUE.equals(listing.isTruncated())) {
                return Optional.empty();
            }
        } catch (SdkException e) {
            throw unavailable(e);
        }
        throw unavailable(headFailure);
    }

    private StorageOperationException unavailable(Exception cause) {
        log.error("S3 저장소 작업에 실패했습니다.", cause);
        return new StorageOperationException(StorageErrorCode.STORAGE_UNAVAILABLE);
    }

    public record ObjectMetadata(String contentType) {
    }

    public record PresignedUpload(URI url, Instant expiresAt, Map<String, String> requiredHeaders) {
    }

    private Map<String, String> requiredHeaders(Map<String, List<String>> signedHeaders) {
        Map<String, String> headers = new LinkedHashMap<>();
        signedHeaders.forEach((name, values) -> {
            if (!"host".equals(name.toLowerCase(Locale.ROOT))) {
                headers.put(toHeaderName(name), String.join(", ", values));
            }
        });
        return Map.copyOf(headers);
    }

    private String toHeaderName(String name) {
        String[] parts = name.split("-");
        for (int index = 0; index < parts.length; index++) {
            if (!parts[index].isEmpty()) {
                parts[index] = parts[index].substring(0, 1).toUpperCase(Locale.ROOT)
                        + parts[index].substring(1).toLowerCase(Locale.ROOT);
            }
        }
        return String.join("-", parts);
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
