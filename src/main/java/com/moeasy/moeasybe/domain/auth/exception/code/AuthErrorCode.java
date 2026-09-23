package com.moeasy.moeasybe.domain.auth.exception.code;

import com.moeasy.moeasybe.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AuthErrorCode implements BaseErrorCode {

    UNSUPPORTED_OAUTH_PROVIDER(HttpStatus.BAD_REQUEST, "AUTH400_1", "지원하지 않는 소셜 로그인 제공자입니다."),
    OAUTH_STATE_ISSUANCE_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "AUTH503_1", "소셜 로그인 요청을 준비할 수 없습니다."),
    INVALID_OAUTH_STATE(HttpStatus.UNAUTHORIZED, "AUTH401_2", "유효하지 않거나 만료된 소셜 로그인 요청입니다."),
    KAKAO_AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "AUTH401_3", "카카오 인증에 실패했습니다."),
    GOOGLE_AUTHENTICATION_FAILED(HttpStatus.UNAUTHORIZED, "AUTH401_4", "구글 인증에 실패했습니다."),
    KAKAO_SERVER_ERROR(HttpStatus.BAD_GATEWAY, "AUTH502_1", "카카오 로그인 서버와 통신하지 못했습니다."),
    GOOGLE_SERVER_ERROR(HttpStatus.BAD_GATEWAY, "AUTH502_2", "구글 로그인 서버와 통신하지 못했습니다."),
    OAUTH_STATE_VERIFICATION_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "AUTH503_2", "소셜 로그인 요청을 확인할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
