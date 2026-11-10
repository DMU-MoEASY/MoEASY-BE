package com.moeasy.moeasybe.domain.bank.exception.code;

import com.moeasy.moeasybe.global.apiPayload.code.BaseErrorCode;

import lombok.AllArgsConstructor;
import lombok.Getter;

import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum BankErrorCode implements BaseErrorCode {
    INVALID_QUERY(HttpStatus.BAD_REQUEST, "BANK400_1", "은행 조회 기간 또는 인증 입력을 확인해 주세요."),
    BANK_REJECTED(
            HttpStatus.UNPROCESSABLE_CONTENT,
            "BANK422_1",
            "은행이 조회를 거절했습니다. 계좌와 조회용 인증정보를 확인해 주세요."),
    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "BANK404_1", "연결된 모임 계좌가 없습니다."),
    ACCOUNT_CONFLICT(HttpStatus.CONFLICT, "BANK409_1", "이미 연결된 계좌가 있거나 다른 모임에서 사용하는 계좌입니다."),
    SYNC_IN_PROGRESS(HttpStatus.CONFLICT, "BANK409_2", "계좌 동기화가 진행 중입니다."),
    RATE_LIMITED(
            HttpStatus.TOO_MANY_REQUESTS,
            "BANK429_1",
            "조회 제한 중입니다. 계좌 정보의 nextSyncAt 이후 다시 시도해 주세요."),
    INVALID_API_KEY(HttpStatus.UNPROCESSABLE_CONTENT, "BANK422_2", "Bank API 인증정보를 확인해 주세요."),
    UPSTREAM_FAILED(HttpStatus.BAD_GATEWAY, "BANK502_1", "은행 거래내역 조회에 실패했습니다. 저장된 내역은 유지됩니다."),
    UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "BANK503_1", "은행 연동 기능을 사용할 수 없습니다.");
    private final HttpStatus status;
    private final String code;
    private final String message;
}
