package com.moeasy.moeasybe.domain.schedule.converter;

import com.moeasy.moeasybe.domain.schedule.dto.request.AvailabilityReqDTO;
import com.moeasy.moeasybe.domain.schedule.dto.response.AvailabilityResDTO;
import com.moeasy.moeasybe.domain.schedule.entity.*;
import com.moeasy.moeasybe.domain.schedule.enums.AvailabilityStatus;
import com.moeasy.moeasybe.domain.schedule.service.support.AvailabilitySupport.Snapshot;
import java.time.LocalDateTime;
import java.util.*;

public final class AvailabilityConverter {
    private AvailabilityConverter() { }
    public static AvailabilityPoll toEntity(Long scheduleId, AvailabilityReqDTO.Configure request) {
        return AvailabilityPoll.builder().scheduleId(scheduleId).startDate(request.startDate()).dayCount(request.dayCount())
                .startHour(request.startHour()).endHour(request.endHour()).voteDeadlineAt(request.voteDeadlineAt()).build();
    }
    public static AvailabilitySlot toSlot(Long pollId, LocalDateTime at) {
        return AvailabilitySlot.builder().availabilityPollId(pollId).slotAt(at).build();
    }
    public static AvailabilityResponse toResponse(Long pollId, Long groupMemberId, LocalDateTime joinedAt) {
        return AvailabilityResponse.builder().availabilityPollId(pollId).groupMemberId(groupMemberId)
                .membershipJoinedAt(joinedAt).build();
    }
    public static AvailabilityVote toVote(Long slotId, Long groupMemberId) {
        return AvailabilityVote.builder().availabilitySlotId(slotId).groupMemberId(groupMemberId).isAvailable(true).build();
    }
    public static AvailabilityResDTO.Poll toPoll(Snapshot snapshot) {
        AvailabilityPoll poll = snapshot.poll();
        return AvailabilityResDTO.Poll.builder().pollId(poll.getId()).startDate(poll.getStartDate()).dayCount(poll.getDayCount())
                .startHour(poll.getStartHour()).endHour(poll.getEndHour()).voteDeadlineAt(poll.getVoteDeadlineAt())
                .status(poll.effectiveStatus(snapshot.now())).slotMinutes(poll.getSlotMinutes()).token(snapshot.token())
                .confirmed(poll.getStatus() == AvailabilityStatus.CONFIRMED)
                .confirmedStartsAt(poll.getConfirmedStartAt()).confirmedEndsAt(poll.getConfirmedEndAt()).build();
    }
    public static AvailabilityResDTO.Me toMe(Snapshot snapshot, boolean editable) {
        Long actor = snapshot.access().actor().getId();
        return AvailabilityResDTO.Me.builder().responded(snapshot.responders().contains(actor))
                .slots(snapshot.slots().stream().map(AvailabilitySlot::getSlotAt)
                        .filter(at -> snapshot.attendees().getOrDefault(at, Set.of()).contains(actor)).toList())
                .editable(editable).build();
    }
    public static AvailabilityResDTO.Detail toDetail(Snapshot snapshot, boolean editable) {
        Long actor = snapshot.access().actor().getId();
        return AvailabilityResDTO.Detail.builder().poll(toPoll(snapshot)).targetCount(snapshot.targetCount())
                .respondedCount(snapshot.responders().size()).pendingCount(snapshot.targetCount() - snapshot.responders().size())
                .slots(snapshot.slots().stream().map(slot -> AvailabilityResDTO.Slot.builder().startsAt(slot.getSlotAt())
                        .availableCount(snapshot.attendees().getOrDefault(slot.getSlotAt(), Set.of()).size())
                        .selected(snapshot.attendees().getOrDefault(slot.getSlotAt(), Set.of()).contains(actor)).build()).toList())
                .me(toMe(snapshot, editable)).recommendations(snapshot.recommendations().stream().map(window -> AvailabilityResDTO.Recommendation.builder()
                        .startsAt(window.startsAt()).endsAt(window.endsAt()).availableCount(window.availableCount()).targetCount(snapshot.targetCount())
                        .ratio((double) window.availableCount() / snapshot.targetCount()).build()).toList()).build();
    }
}
