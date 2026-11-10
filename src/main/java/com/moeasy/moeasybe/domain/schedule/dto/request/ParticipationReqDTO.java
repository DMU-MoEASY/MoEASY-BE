package com.moeasy.moeasybe.domain.schedule.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public final class ParticipationReqDTO {
    private ParticipationReqDTO() {}
    public enum ResponseStatus { ATTENDING, NOT_ATTENDING, UNDECIDED }
    public enum Mark { PRESENT, LATE, ABSENT }
    @Schema(name = "ParticipationRsvpRequest")
    public record Rsvp(@NotNull ResponseStatus status) {}
    @Schema(name = "ParticipationVersionRequest")
    public record Version(@NotNull @PositiveOrZero Long version) {}
    @Schema(name = "ParticipationEntryRequest")
    public record Entry(@NotNull @Positive Long groupMemberId, @NotNull Mark status) {}
    @Schema(name = "ParticipationReplaceRequest")
    public record Replace(@NotNull @PositiveOrZero Long version, @NotNull List<@NotNull @Valid Entry> entries) {}
}
