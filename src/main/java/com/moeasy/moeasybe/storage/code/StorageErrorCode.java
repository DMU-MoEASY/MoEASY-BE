package com.moeasy.moeasybe.storage.code;

import com.moeasy.moeasybe.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/** 스토리지 작업의 입력 및 장애에 대한 오류 코드입니다. */
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
    ),
    /** 저장소에 접근할 수 없음을 나타냅니다. */
    STORAGE_UNAVAILABLE(
            HttpStatus.SERVICE_UNAVAILABLE,
            "STORAGE503_1",
            "저장소를 일시적으로 사용할 수 없습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
