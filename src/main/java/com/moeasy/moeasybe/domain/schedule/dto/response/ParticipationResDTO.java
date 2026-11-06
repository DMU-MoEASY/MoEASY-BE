package com.moeasy.moeasybe.domain.schedule.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Builder;

public final class ParticipationResDTO {
    private ParticipationResDTO() {}
    @Builder @Schema(name = "ParticipationResponseResult")
    public record Response(String status) {}
    @Builder @Schema(name = "ParticipationMemberResult")
    public record Member(long groupMemberId, String status) {}
    @Builder @Schema(name = "ParticipationRsvpResult")
    public record Rsvp(long scheduleId, @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime startsAt,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime endsAt,
            boolean canRespond, String myStatus, Map<String, Long> counts, List<Member> members) {}
    @Builder @Schema(name = "ParticipationRecordResult")
    public record Record(long groupMemberId, String status, boolean preRegistered, boolean credited) {}
    @Builder @Schema(name = "ParticipationAttendanceResult")
    public record Attendance(long scheduleId, String collectionStatus, long version, boolean canEdit,
            boolean canComplete, boolean canReopen, boolean rosterFixed, boolean recoverable, List<Record> members) {}
}
