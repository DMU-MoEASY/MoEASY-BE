package com.moeasy.moeasybe.domain.settlement.exception.code;

import com.moeasy.moeasybe.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum SettlementSuccessCode implements BaseSuccessCode {

    SETTLEMENT_CREATED(HttpStatus.CREATED, "SETTLEMENT201_1", "정산 생성에 성공했습니다."),
    SPLIT_PREVIEWED(HttpStatus.OK, "SETTLEMENT200_1", "정산 분할 미리보기에 성공했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
