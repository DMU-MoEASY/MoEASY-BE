package com.moeasy.moeasybe.storage.code;

import com.moeasy.moeasybe.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/** 유효하지 않은 스토리지 작업 입력에 대한 오류 코드입니다. */
@Getter
@AllArgsConstructor
public enum StorageErrorCode implements BaseErrorCode {

    /** object key가 null이거나 공백임을 나타냅니다. */
    INVALID_OBJECT_KEY(
            HttpStatus.BAD_REQUEST,
            "STORAGE400_1",
            "Object key가 올바르지 않습니다."
    ),
    /** content type이 null이거나 공백임을 나타냅니다. */
    INVALID_CONTENT_TYPE(
            HttpStatus.BAD_REQUEST,
            "STORAGE400_2",
            "Content type이 올바르지 않습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
