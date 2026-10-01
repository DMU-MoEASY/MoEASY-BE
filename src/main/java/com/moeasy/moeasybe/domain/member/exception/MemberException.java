package com.moeasy.moeasybe.domain.member.exception;

import com.moeasy.moeasybe.domain.member.exception.code.MemberErrorCode;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;

public class MemberException extends GeneralException {

    public MemberException(MemberErrorCode code) {
        super(code);
    }
}
