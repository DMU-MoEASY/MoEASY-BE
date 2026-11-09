package com.moeasy.moeasybe.domain.schedule.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.moeasy.moeasybe.domain.schedule.enums.PlaceProvider;
import com.moeasy.moeasybe.domain.schedule.enums.ScheduleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;

public final class ScheduleResDTO {
    private ScheduleResDTO() { }
    @Builder
    @Schema(name = "ScheduleCreated")
    public record Created(Long scheduleId) { }
    @Builder
    @Schema(name = "ScheduleSummary")
    public record Summary(Long scheduleId, Long groupId, String title,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime startsAt, @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime endsAt,
            String locationName, ScheduleStatus status) { }
    @Builder
    @Schema(name = "ScheduleDetail")
    public record Detail(Long scheduleId, Long groupId, String title, String description,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime startsAt, @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime endsAt,
            String locationName, String locationAddress, BigDecimal latitude, BigDecimal longitude,
            PlaceProvider placeProvider, String placeId, ScheduleStatus status,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime createdAt, @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime updatedAt) { }
    @Builder
    @Schema(name = "SchedulePage")
    public record Page(@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime from, @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime to,
            List<Summary> items, String nextCursor, boolean hasNext) { }
}
