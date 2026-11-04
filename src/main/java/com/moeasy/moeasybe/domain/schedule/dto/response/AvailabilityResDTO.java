package com.moeasy.moeasybe.domain.schedule.dto.response;
import com.moeasy.moeasybe.domain.schedule.enums.AvailabilityStatus;
import java.time.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.List;
import lombok.Builder;
import io.swagger.v3.oas.annotations.media.Schema;
public final class AvailabilityResDTO {
    private AvailabilityResDTO() { }
    @Builder
    @Schema(name = "AvailabilityPoll")
    public record Poll(Long pollId, LocalDate startDate, int dayCount, int startHour, int endHour,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime voteDeadlineAt, AvailabilityStatus status, int slotMinutes, String token,
            boolean confirmed, @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime confirmedStartsAt, @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime confirmedEndsAt) { }
    @Builder
    @Schema(name = "AvailabilityMe")
    public record Me(boolean responded, @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") List<LocalDateTime> slots, boolean editable) { }
    @Builder
    @Schema(name = "AvailabilitySlot")
    public record Slot(@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime startsAt, int availableCount, boolean selected) { }
    @Builder
    @Schema(name = "AvailabilityRecommendation")
    public record Recommendation(@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime startsAt, @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime endsAt,
            int availableCount, int targetCount, double ratio) { }
    @Builder
    @Schema(name = "AvailabilityDetail")
    public record Detail(Poll poll, int targetCount, int respondedCount, int pendingCount,
            List<Slot> slots, Me me, List<Recommendation> recommendations) { }
}
