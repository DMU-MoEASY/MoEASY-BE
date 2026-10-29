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
    GROUP_CLOSED(HttpStatus.OK, "GROUP200_3", "모임을 폐쇄했습니다."),
    GROUP_COVER_UPLOAD_ISSUED(HttpStatus.CREATED, "GROUP201_2", "커버 이미지 업로드 URL을 발급했습니다."),
    JOIN_REQUEST_CREATED(HttpStatus.CREATED, "GROUP201_3", "가입 신청을 등록했습니다."),
    JOIN_REQUEST_CANCELLED(HttpStatus.OK, "GROUP200_4", "가입 신청을 취소했습니다."),
    JOIN_REQUESTS_FETCHED(HttpStatus.OK, "GROUP200_5", "대기 신청을 조회했습니다."),
    JOIN_REQUEST_FETCHED(HttpStatus.OK, "GROUP200_6", "최근 가입 신청을 조회했습니다."),
    JOIN_REQUEST_APPROVED(HttpStatus.OK, "GROUP200_7", "가입 신청을 승인했습니다."),
    JOIN_REQUEST_REJECTED(HttpStatus.OK, "GROUP200_8", "가입 신청을 거절했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
