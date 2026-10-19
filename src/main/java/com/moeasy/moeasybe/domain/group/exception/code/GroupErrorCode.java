package com.moeasy.moeasybe.domain.group.exception.code;

import com.moeasy.moeasybe.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum GroupErrorCode implements BaseErrorCode {
    GROUP_CONFIRMATION_TEXT_MISMATCH(HttpStatus.BAD_REQUEST, "GROUP400_1", "모임 이름 확인 문구가 일치하지 않습니다."),
    GROUP_COORDINATE_PAIR_INVALID(HttpStatus.BAD_REQUEST, "GROUP400_2", "위도와 경도를 함께 입력해야 합니다."),
    GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "GROUP404_1", "모임을 찾을 수 없습니다."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "GROUP404_2", "카테고리를 찾을 수 없습니다."),
    GROUP_APPROVAL_REQUIRED(HttpStatus.BAD_REQUEST, "GROUP400_3", "승인제 모임에만 가입 신청할 수 있습니다."),
    JOIN_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "GROUP404_3", "가입 신청을 찾을 수 없습니다."),
    GROUP_ARCHIVED(HttpStatus.CONFLICT, "GROUP409_1", "보관된 모임에서는 처리할 수 없습니다."),
    GROUP_ALREADY_JOINED(HttpStatus.CONFLICT, "GROUP409_2", "이미 가입한 모임입니다."),
    JOIN_REQUEST_DUPLICATE(HttpStatus.CONFLICT, "GROUP409_3", "이미 대기 중인 가입 신청이 있습니다."),
    GROUP_CAPACITY_EXCEEDED(HttpStatus.CONFLICT, "GROUP409_4", "모임 정원이 가득 찼습니다."),
    GROUP_MEMBER_KICKED(HttpStatus.CONFLICT, "GROUP409_5", "강퇴된 모임에는 가입 신청할 수 없습니다."),
    JOIN_REQUEST_ALREADY_PROCESSED(HttpStatus.CONFLICT, "GROUP409_6", "이미 처리된 가입 신청입니다."),
    JOIN_REQUEST_PENDING_NOT_FOUND(HttpStatus.CONFLICT, "GROUP409_7", "취소할 대기 신청이 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
