package com.moeasy.moeasybe.domain.schedule.exception.code;
import com.moeasy.moeasybe.global.apiPayload.code.BaseErrorCode;
import lombok.*;
import org.springframework.http.HttpStatus;
@Getter @AllArgsConstructor
public enum AvailabilityErrorCode implements BaseErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND, "가용시간 투표가 없습니다.", "AVAILABILITY404_1"),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "후보 설정 또는 선택 슬롯이 올바르지 않습니다.", "AVAILABILITY400_1"),
    DUPLICATE_POLL(HttpStatus.CONFLICT, "이미 투표가 존재합니다.", "AVAILABILITY409_1"),
    NOT_OPEN(HttpStatus.CONFLICT, "응답을 변경할 수 없는 투표입니다.", "AVAILABILITY409_2"),
    STALE_RESULT(HttpStatus.CONFLICT, "변경된 결과를 다시 조회해 주세요.", "AVAILABILITY409_3"),
    RESTORE_CONFLICT(HttpStatus.CONFLICT, "원래 시간이 과거이거나 일정 시간이 별도로 변경되어 복원할 수 없습니다.", "AVAILABILITY409_4");
    private final HttpStatus status;
    private final String message;
    private final String code;
}
