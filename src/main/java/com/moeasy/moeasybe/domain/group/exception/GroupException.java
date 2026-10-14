package com.moeasy.moeasybe.domain.group.exception;

import com.moeasy.moeasybe.domain.group.exception.code.GroupErrorCode;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;

public class GroupException extends GeneralException {
    public GroupException(GroupErrorCode code) {
        super(code);
    }
}
