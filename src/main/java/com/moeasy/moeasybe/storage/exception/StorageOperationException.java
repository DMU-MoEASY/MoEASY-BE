package com.moeasy.moeasybe.storage.exception;

import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;
import com.moeasy.moeasybe.storage.code.StorageErrorCode;

/** 저장소 작업 중 발생한 장애를 나타냅니다. */
public class StorageOperationException extends GeneralException {

    public StorageOperationException(StorageErrorCode code) {
        super(code);
    }
}
