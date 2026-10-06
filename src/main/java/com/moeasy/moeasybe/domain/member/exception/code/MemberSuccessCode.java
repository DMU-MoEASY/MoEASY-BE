package com.moeasy.moeasybe.domain.member.exception.code;

import com.moeasy.moeasybe.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum MemberSuccessCode implements BaseSuccessCode {

    NICKNAME_AVAILABILITY_FETCH_SUCCESS(HttpStatus.OK, "MEMBER200_1", "닉네임 사용 가능 여부 조회에 성공했습니다."),
    ONBOARDING_COMPLETE_SUCCESS(HttpStatus.OK, "MEMBER200_2", "온보딩을 완료했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
