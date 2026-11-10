package com.moeasy.moeasybe.domain.bank.exception.code;

import com.moeasy.moeasybe.global.apiPayload.code.BaseSuccessCode;

import lombok.AllArgsConstructor;
import lombok.Getter;

import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum BankSuccessCode implements BaseSuccessCode {
    CONNECTED(HttpStatus.CREATED, "BANK201_1", "모임 계좌를 연결했습니다."),
    FETCHED(HttpStatus.OK, "BANK200_1", "모임 계좌 정보를 조회했습니다."),
    DISCONNECTED(HttpStatus.OK, "BANK200_2", "모임 계좌 연결을 해제했습니다."),
    SYNCED(HttpStatus.OK, "BANK200_3", "계좌 거래내역을 동기화했습니다."),
    TRANSACTIONS_FETCHED(HttpStatus.OK, "BANK200_4", "저장된 거래내역을 조회했습니다.");
    private final HttpStatus status;
    private final String code;
    private final String message;
}
