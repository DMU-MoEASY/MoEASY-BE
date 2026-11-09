package com.moeasy.moeasybe.domain.bank.exception;

import com.moeasy.moeasybe.domain.bank.exception.code.BankErrorCode;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;

public class BankException extends GeneralException {
    public BankException(BankErrorCode code) {
        super(code);
    }
}
