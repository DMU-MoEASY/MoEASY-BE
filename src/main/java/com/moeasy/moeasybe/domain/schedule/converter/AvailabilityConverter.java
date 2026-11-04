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
    public static AvailabilityPoll toEntity(Long scheduleId, AvailabilityReqDTO.Configure r) {
        return AvailabilityPoll.builder().scheduleId(scheduleId).startDate(r.startDate()).dayCount(r.dayCount())
                .startHour(r.startHour()).endHour(r.endHour()).voteDeadlineAt(r.voteDeadlineAt()).build();
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
    public static AvailabilityResDTO.Poll toPoll(Snapshot s) {
        AvailabilityPoll p = s.poll();
        return AvailabilityResDTO.Poll.builder().pollId(p.getId()).startDate(p.getStartDate()).dayCount(p.getDayCount())
                .startHour(p.getStartHour()).endHour(p.getEndHour()).voteDeadlineAt(p.getVoteDeadlineAt())
                .status(p.effectiveStatus(s.now())).slotMinutes(p.getSlotMinutes()).token(s.token())
                .confirmed(p.getStatus() == AvailabilityStatus.CONFIRMED)
                .confirmedStartsAt(p.getConfirmedStartAt()).confirmedEndsAt(p.getConfirmedEndAt()).build();
    }
    public static AvailabilityResDTO.Me toMe(Snapshot s, boolean editable) {
        Long actor = s.access().actor().getId();
        return AvailabilityResDTO.Me.builder().responded(s.responders().contains(actor))
                .slots(s.slots().stream().map(AvailabilitySlot::getSlotAt)
                        .filter(at -> s.attendees().getOrDefault(at, Set.of()).contains(actor)).toList())
                .editable(editable).build();
    }
    public static AvailabilityResDTO.Detail toDetail(Snapshot s, boolean editable) {
        Long actor = s.access().actor().getId();
        return AvailabilityResDTO.Detail.builder().poll(toPoll(s)).targetCount(s.targetCount())
                .respondedCount(s.responders().size()).pendingCount(s.targetCount() - s.responders().size())
                .slots(s.slots().stream().map(slot -> AvailabilityResDTO.Slot.builder().startsAt(slot.getSlotAt())
                        .availableCount(s.attendees().getOrDefault(slot.getSlotAt(), Set.of()).size())
                        .selected(s.attendees().getOrDefault(slot.getSlotAt(), Set.of()).contains(actor)).build()).toList())
                .me(toMe(s, editable)).recommendations(s.recommendations().stream().map(w -> AvailabilityResDTO.Recommendation.builder()
                        .startsAt(w.startsAt()).endsAt(w.endsAt()).availableCount(w.availableCount()).targetCount(s.targetCount())
                        .ratio((double) w.availableCount() / s.targetCount()).build()).toList()).build();
    }
}
