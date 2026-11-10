package com.moeasy.moeasybe.domain.schedule.exception.code;
import com.moeasy.moeasybe.global.apiPayload.code.BaseSuccessCode;
import lombok.*;
import org.springframework.http.HttpStatus;
@Getter @AllArgsConstructor
public enum AvailabilitySuccessCode implements BaseSuccessCode {
    CREATED(HttpStatus.CREATED, "투표를 생성했습니다.", "AVAILABILITY201_1"),
    FETCHED(HttpStatus.OK, "가용시간을 조회했습니다.", "AVAILABILITY200_1"),
    UPDATED(HttpStatus.OK, "가용시간을 변경했습니다.", "AVAILABILITY200_2");
    private final HttpStatus status;
    private final String message;
    private final String code;
}
