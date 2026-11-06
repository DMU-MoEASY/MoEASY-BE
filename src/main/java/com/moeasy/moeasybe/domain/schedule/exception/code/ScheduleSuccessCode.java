package com.moeasy.moeasybe.domain.schedule.exception.code;
import com.moeasy.moeasybe.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ScheduleSuccessCode implements BaseSuccessCode {
    CREATED(HttpStatus.CREATED, "SCHEDULE201_1", "일정을 생성했습니다."),
    LIST_FETCHED(HttpStatus.OK, "SCHEDULE200_1", "일정 목록을 조회했습니다."),
    DETAIL_FETCHED(HttpStatus.OK, "SCHEDULE200_2", "일정을 조회했습니다."),
    UPDATED(HttpStatus.OK, "SCHEDULE200_3", "일정을 수정했습니다."),
    CANCELLED(HttpStatus.OK, "SCHEDULE200_4", "일정을 취소했습니다."),
    PARTICIPATION(HttpStatus.OK, "SCHEDULE200_6", "참석 정보를 처리했습니다."),
    DELETED(HttpStatus.OK, "SCHEDULE200_5", "일정을 삭제했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
