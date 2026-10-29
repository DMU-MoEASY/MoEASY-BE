package com.moeasy.moeasybe.domain.settlement.exception;

import com.moeasy.moeasybe.domain.settlement.exception.code.SettlementErrorCode;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;

public class SettlementException extends GeneralException {

    public SettlementException(SettlementErrorCode code) {
        super(code);
    }
}
