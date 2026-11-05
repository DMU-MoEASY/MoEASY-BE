package com.moeasy.moeasybe.domain.settlement.exception.code;

import com.moeasy.moeasybe.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum SettlementSuccessCode implements BaseSuccessCode {

    SETTLEMENT_CREATED(HttpStatus.CREATED, "SETTLEMENT201_1", "정산 생성에 성공했습니다."),
    PAYMENT_REGISTERED(HttpStatus.CREATED, "SETTLEMENT201_2", "납부 기록 등록에 성공했습니다."),
    SPLIT_PREVIEWED(HttpStatus.OK, "SETTLEMENT200_1", "정산 분할 미리보기에 성공했습니다."),
    SETTLEMENT_LIST_FETCHED(HttpStatus.OK, "SETTLEMENT200_2", "정산 목록 조회에 성공했습니다."),
    SETTLEMENT_DETAIL_FETCHED(HttpStatus.OK, "SETTLEMENT200_3", "정산 상세 조회에 성공했습니다."),
    PAYMENT_CANCELLED(HttpStatus.OK, "SETTLEMENT200_4", "납부 기록 취소에 성공했습니다."),
    PAYMENT_HISTORY_FETCHED(HttpStatus.OK, "SETTLEMENT200_5", "납부 이력 조회에 성공했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
