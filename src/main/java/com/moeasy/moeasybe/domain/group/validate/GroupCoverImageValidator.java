package com.moeasy.moeasybe.domain.group.validate;

import com.moeasy.moeasybe.domain.group.entity.GroupCoverUpload;
import com.moeasy.moeasybe.domain.group.exception.GroupException;
import com.moeasy.moeasybe.domain.group.exception.code.GroupErrorCode;
import com.moeasy.moeasybe.domain.group.repository.GroupCoverUploadRepository;
import com.moeasy.moeasybe.global.apiPayload.code.GeneralErrorCode;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;
import com.moeasy.moeasybe.storage.S3StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GroupCoverImageValidator {
    private final S3StorageService storageService;
    private final GroupCoverUploadRepository uploadRepository;

    public void validateIssueRequest(Long memberId, String contentType) {
        if (memberId == null || memberId <= 0) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST);
        }
        if (!isSupportedType(contentType)) {
            throw new GroupException(GroupErrorCode.UNSUPPORTED_COVER_IMAGE_TYPE);
        }
    }

    public void verifyOwnedAndExists(Long memberId, String key) {
        if (key == null || key.isBlank()
                || key.length() > 500 || !key.matches("group-covers/" + memberId + "/[0-9a-fA-F-]{36}")) {
            throw new GroupException(GroupErrorCode.INVALID_OR_UNOWNED_COVER_IMAGE_KEY);
        }
        GroupCoverUpload issuance = uploadRepository.findByObjectKeyAndMemberId(key, memberId)
                .orElseThrow(() -> new GroupException(GroupErrorCode.INVALID_OR_UNOWNED_COVER_IMAGE_KEY));
        var object = storageService.getObjectMetadata(key)
                .orElseThrow(() -> new GroupException(GroupErrorCode.COVER_IMAGE_OBJECT_NOT_FOUND));
        if (!issuance.getContentType().equals(object.contentType())) {
            throw new GroupException(GroupErrorCode.COVER_IMAGE_CONTENT_TYPE_MISMATCH);
        }
    }

    private boolean isSupportedType(String type) {
        return "image/jpeg".equals(type) || "image/png".equals(type) || "image/webp".equals(type);
    }
}
