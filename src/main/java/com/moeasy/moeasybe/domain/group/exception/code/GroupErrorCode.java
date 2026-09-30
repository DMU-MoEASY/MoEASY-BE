package com.moeasy.moeasybe.domain.group.exception.code;

import com.moeasy.moeasybe.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum GroupErrorCode implements BaseErrorCode {
    GROUP_CONFIRMATION_TEXT_MISMATCH(HttpStatus.BAD_REQUEST, "GROUP400_1", "모임 이름 확인 문구가 일치하지 않습니다."),
    GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "GROUP404_1", "모임을 찾을 수 없습니다."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "GROUP404_2", "카테고리를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
