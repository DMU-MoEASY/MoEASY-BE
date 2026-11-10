package com.moeasy.moeasybe.domain.schedule.exception.code;
import com.moeasy.moeasybe.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ScheduleErrorCode implements BaseErrorCode {
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "SCHEDULE404_1", "일정을 찾을 수 없습니다."),
    GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "SCHEDULE404_2", "모임을 찾을 수 없습니다."),
    SCHEDULE_NOT_EDITABLE(HttpStatus.CONFLICT, "SCHEDULE409_1", "이미 시작했거나 취소된 일정입니다."),
    GROUP_ARCHIVED(HttpStatus.CONFLICT, "SCHEDULE409_2", "보관된 모임의 일정은 변경할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
