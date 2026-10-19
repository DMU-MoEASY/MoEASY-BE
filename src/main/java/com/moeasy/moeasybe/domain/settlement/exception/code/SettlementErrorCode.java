package com.moeasy.moeasybe.domain.settlement.exception.code;

import com.moeasy.moeasybe.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum SettlementErrorCode implements BaseErrorCode {

    INVALID_SCHEDULE(HttpStatus.BAD_REQUEST, "SETTLEMENT400_1", "선택할 수 없는 일정입니다."),
    INVALID_TARGET_MEMBERS(HttpStatus.BAD_REQUEST, "SETTLEMENT400_2", "정산 대상은 유효한 모임 회원으로 중복 없이 선택해야 합니다."),
    INVALID_SPLIT_MODE_FIELDS(HttpStatus.BAD_REQUEST, "SETTLEMENT400_3", "분할 방식에 맞는 대상·금액 필드를 전달해야 합니다."),
    INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "SETTLEMENT400_4", "정산 금액 또는 회원별 청구액 합계가 올바르지 않습니다."),
    INVALID_DUE_AT(HttpStatus.BAD_REQUEST, "SETTLEMENT400_5", "납부 기한은 정산 생성 시각 이후여야 합니다."),
    SETTLEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "SETTLEMENT404_1", "정산을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
