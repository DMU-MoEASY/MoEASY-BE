package com.moeasy.moeasybe.domain.group.exception.code;

import com.moeasy.moeasybe.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum GroupSuccessCode implements BaseSuccessCode {
    GROUP_CREATED(HttpStatus.CREATED, "GROUP201_1", "모임을 생성했습니다."),
    GROUP_FETCHED(HttpStatus.OK, "GROUP200_1", "모임을 조회했습니다."),
    GROUP_UPDATED(HttpStatus.OK, "GROUP200_2", "모임을 수정했습니다."),
    GROUP_CLOSED(HttpStatus.OK, "GROUP200_3", "모임을 폐쇄했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
