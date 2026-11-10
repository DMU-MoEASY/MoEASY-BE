package com.moeasy.moeasybe.domain.schedule.dto.request;
import com.moeasy.moeasybe.domain.schedule.dto.deserializer.AvailabilityIntegerDeserializer;
import com.moeasy.moeasybe.domain.schedule.dto.deserializer.ScheduleTimeDeserializer;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonDeserialize;
public final class AvailabilityReqDTO {
    private AvailabilityReqDTO() { }
    @Schema(name = "AvailabilityConfigure")
    public record Configure(
            @NotNull(message = "시작 날짜는 필수입니다.") LocalDate startDate,
            @NotNull(message = "후보 일수는 필수입니다.") @JsonDeserialize(using = AvailabilityIntegerDeserializer.class) Integer dayCount,
            @NotNull(message = "시작 시간은 필수입니다.") @Min(0) @Max(23) @JsonDeserialize(using = AvailabilityIntegerDeserializer.class) Integer startHour,
            @NotNull(message = "종료 시간은 필수입니다.") @Min(1) @Max(24) @JsonDeserialize(using = AvailabilityIntegerDeserializer.class) Integer endHour,
            @NotNull(message = "마감은 필수입니다.") @JsonDeserialize(using = ScheduleTimeDeserializer.class)
            LocalDateTime voteDeadlineAt) { }
    @Schema(name = "AvailabilitySubmit")
    public record Submit(@NotNull(message = "슬롯 배열은 필수입니다.") @Size(max = 672)
            @JsonDeserialize(contentUsing = ScheduleTimeDeserializer.class) List<@NotNull LocalDateTime> slots) { }
    @Schema(name = "AvailabilityConfirm")
    public record Confirm(
            @NotNull(message = "시작 시각은 필수입니다.") @JsonDeserialize(using = ScheduleTimeDeserializer.class)
            LocalDateTime startsAt,
            @NotBlank(message = "검증 토큰은 필수입니다.") @Size(max = 64) String token) { }
}
