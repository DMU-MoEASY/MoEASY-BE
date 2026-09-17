package com.moeasy.moeasybe.storage.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.moeasy.moeasybe.global.apiPayload.ApiResponse;
import com.moeasy.moeasybe.global.apiPayload.handler.GeneralExceptionAdvice;
import com.moeasy.moeasybe.storage.code.StorageErrorCode;

class StorageValidationExceptionTest {

    @Test
    void isHandledAsCommonValidationResponse() {
        StorageValidationException exception = new StorageValidationException(
                StorageErrorCode.INVALID_OBJECT_KEY
        );

        ResponseEntity<ApiResponse<Void>> response = new GeneralExceptionAdvice()
                .handleGeneralException(exception);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ApiResponse<Void> body = response.getBody();
        assertNotNull(body);
        assertFalse(body.getIsSuccess());
        assertEquals(StorageErrorCode.INVALID_OBJECT_KEY.getCode(), body.getCode());
        assertEquals(StorageErrorCode.INVALID_OBJECT_KEY.getMessage(), body.getMessage());
    }
}
