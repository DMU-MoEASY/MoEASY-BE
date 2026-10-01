package com.moeasy.moeasybe.domain.member.exception.code;

import com.moeasy.moeasybe.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum MemberErrorCode implements BaseErrorCode {

    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER404_1", "회원을 찾을 수 없습니다."),
    MEMBER_UNAVAILABLE(HttpStatus.FORBIDDEN, "MEMBER403_1", "온보딩을 진행할 수 없는 회원입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "MEMBER409_1", "이미 사용 중인 닉네임입니다."),
    ONBOARDING_ALREADY_COMPLETED(HttpStatus.CONFLICT, "MEMBER409_2", "이미 온보딩을 완료했습니다."),
    INVALID_REGION(HttpStatus.BAD_REQUEST, "MEMBER400_1", "선택할 수 없는 지역입니다."),
    INVALID_CATEGORIES(HttpStatus.BAD_REQUEST, "MEMBER400_2", "관심사는 선택 가능한 항목으로 중복 없이 선택해야 합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
