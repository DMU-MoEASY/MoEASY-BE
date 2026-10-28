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
    INVALID_OR_UNOWNED_COVER_IMAGE_KEY(HttpStatus.BAD_REQUEST, "GROUP400_3", "사용할 수 없는 커버 이미지 키입니다."),
    COVER_IMAGE_OBJECT_NOT_FOUND(HttpStatus.BAD_REQUEST, "GROUP400_4", "업로드된 커버 이미지 객체를 찾을 수 없습니다."),
    COVER_IMAGE_CONTENT_TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "GROUP400_5", "업로드한 커버 이미지 형식이 발급 정보와 다릅니다."),
    UNSUPPORTED_COVER_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "GROUP400_6", "지원하지 않는 커버 이미지 형식입니다."),
    GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "GROUP404_1", "모임을 찾을 수 없습니다."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "GROUP404_2", "카테고리를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
