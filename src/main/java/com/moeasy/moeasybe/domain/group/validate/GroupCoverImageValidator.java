package com.moeasy.moeasybe.domain.group.validate;

import com.moeasy.moeasybe.domain.group.entity.GroupCoverUpload;
import com.moeasy.moeasybe.domain.group.repository.GroupCoverUploadRepository;
import com.moeasy.moeasybe.global.apiPayload.code.GeneralErrorCode;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;
import com.moeasy.moeasybe.global.config.AwsS3Properties;
import com.moeasy.moeasybe.storage.code.StorageErrorCode;
import com.moeasy.moeasybe.storage.exception.StorageOperationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
@RequiredArgsConstructor
@Slf4j
public class GroupCoverImageValidator {
    private final S3Client s3Client;
    private final AwsS3Properties properties;
    private final GroupCoverUploadRepository uploadRepository;

    public void validateIssueRequest(Long memberId, String contentType) {
        if (memberId == null || memberId <= 0 || !isSupportedType(contentType)) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST);
        }
    }

    public void verifyOwnedAndExists(Long memberId, String key) {
        if (memberId == null || memberId <= 0 || key == null || key.isBlank()
                || key.length() > 500 || !key.matches("group-covers/" + memberId + "/[0-9a-fA-F-]{36}")) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST);
        }
        GroupCoverUpload issuance = uploadRepository.findByObjectKeyAndMemberId(key, memberId)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.BAD_REQUEST));
        try {
            var object = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(properties.getS3().getBucket())
                    .key(key)
                    .build());
            if (!issuance.getContentType().equals(object.contentType())) {
                throw new GeneralException(GeneralErrorCode.BAD_REQUEST);
            }
        } catch (NoSuchKeyException e) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST);
        } catch (NoSuchBucketException e) {
            log.error("S3 커버 이미지 객체 조회에 실패했습니다.", e);
            throw new StorageOperationException(StorageErrorCode.STORAGE_UNAVAILABLE);
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new GeneralException(GeneralErrorCode.BAD_REQUEST);
            }
            log.error("S3 커버 이미지 객체 조회에 실패했습니다.", e);
            throw new StorageOperationException(StorageErrorCode.STORAGE_UNAVAILABLE);
        } catch (SdkException e) {
            log.error("S3 커버 이미지 객체 조회에 실패했습니다.", e);
            throw new StorageOperationException(StorageErrorCode.STORAGE_UNAVAILABLE);
        }
    }

    private boolean isSupportedType(String type) {
        return "image/jpeg".equals(type) || "image/png".equals(type) || "image/webp".equals(type);
    }
}
