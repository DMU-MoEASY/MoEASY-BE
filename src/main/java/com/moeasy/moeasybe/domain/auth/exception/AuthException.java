package com.moeasy.moeasybe.domain.auth.exception;

import com.moeasy.moeasybe.domain.auth.code.AuthErrorCode;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;

public class AuthException extends GeneralException {

    public AuthException(AuthErrorCode code) {
        super(code);
    }
}
