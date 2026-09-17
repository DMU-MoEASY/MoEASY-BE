package com.moeasy.moeasybe.storage.exception;

import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;
import com.moeasy.moeasybe.storage.code.StorageErrorCode;

/** S3 스토리지 작업에 잘못된 입력이 전달되었음을 나타내는 예외입니다. */
public class StorageValidationException extends GeneralException {

    /**
     * 유효하지 않은 스토리지 입력에 대한 예외를 생성합니다.
     *
     * @param code 스토리지 전용 오류 코드
     */
    public StorageValidationException(StorageErrorCode code) {
        super(code);
    }
}
