package com.moeasy.moeasybe.domain.auth.exception.code;

import com.moeasy.moeasybe.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AuthSuccessCode implements BaseSuccessCode {

    OAUTH_STATE_ISSUED(HttpStatus.CREATED, "AUTH201_1", "소셜 로그인 state를 발급했습니다."),
    KAKAO_LOGIN_SUCCEEDED(HttpStatus.OK, "AUTH200_1", "카카오 로그인에 성공했습니다."),
    GOOGLE_LOGIN_SUCCEEDED(HttpStatus.OK, "AUTH200_2", "구글 로그인에 성공했습니다."),
    TOKEN_REISSUED(HttpStatus.OK, "AUTH200_3", "토큰을 재발급했습니다."),
    LOGOUT_SUCCEEDED(HttpStatus.OK, "AUTH200_4", "로그아웃에 성공했습니다."),
    CSRF_TOKEN_ISSUED(HttpStatus.OK, "AUTH200_5", "CSRF 토큰을 발급했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
