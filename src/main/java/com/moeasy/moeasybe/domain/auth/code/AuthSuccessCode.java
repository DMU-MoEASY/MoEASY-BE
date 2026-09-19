package com.moeasy.moeasybe.domain.auth.code;

import com.moeasy.moeasybe.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AuthSuccessCode implements BaseSuccessCode {

    OAUTH_STATE_ISSUED(HttpStatus.CREATED, "AUTH201_1", "소셜 로그인 state를 발급했습니다."),
    KAKAO_LOGIN_SUCCEEDED(HttpStatus.OK, "AUTH200_1", "카카오 로그인에 성공했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
