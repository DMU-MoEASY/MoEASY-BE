package com.moeasy.moeasybe.domain.schedule.service.support;

import com.moeasy.moeasybe.domain.group.entity.*;
import com.moeasy.moeasybe.domain.group.enums.*;
import com.moeasy.moeasybe.domain.group.repository.*;
import com.moeasy.moeasybe.domain.member.enums.MemberStatus;
import com.moeasy.moeasybe.domain.schedule.entity.*;
import com.moeasy.moeasybe.domain.schedule.enums.*;
import com.moeasy.moeasybe.domain.schedule.exception.*;
import com.moeasy.moeasybe.domain.schedule.exception.code.*;
import com.moeasy.moeasybe.domain.schedule.repository.*;
import com.moeasy.moeasybe.global.apiPayload.code.GeneralErrorCode;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import com.moeasy.moeasybe.domain.schedule.service.support.ScheduleAccess.Access;
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class AvailabilitySupport {
    private final ScheduleAccess scheduleAccess;
    private final GroupMemberRepository members;
    private final AvailabilityPollRepository polls;
    private final AvailabilitySlotRepository slots;
    private final AvailabilityResponseRepository responses;
    private final AvailabilityVoteRepository votes;
    private final Clock clock;

    public record Snapshot(Access access, AvailabilityPoll poll, List<AvailabilitySlot> slots,
            Set<Long> responders, Map<LocalDateTime, Set<Long>> attendees, int targetCount,
            String token, List<AvailabilityRecommendation.Window> recommendations, LocalDateTime now) { }

    public LocalDateTime now() { return LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Seoul"))); }
    public Access access(Long scheduleId, Long memberId, boolean write, boolean manager, boolean owner) {
        return scheduleAccess.access(scheduleId, memberId, write, manager, owner);
    }
    public void requireEditable(Schedule s) {
        if (s.getStatus() != ScheduleStatus.SCHEDULED || !s.getStartsAt().isAfter(now()))
            throw new ScheduleException(ScheduleErrorCode.SCHEDULE_NOT_EDITABLE);
    }
    public AvailabilityPoll poll(Long scheduleId) {
        return polls.findByScheduleId(scheduleId).orElseThrow(() -> new AvailabilityException(AvailabilityErrorCode.NOT_FOUND));
    }
    public Snapshot snapshot(Access access) {
        AvailabilityPoll p = poll(access.schedule().getId());
        LocalDateTime now = now();
        List<GroupMember> active = members.findAllByGroupIdAndStatusAndDeletedAtIsNullAndMemberDeletedAtIsNull(
                access.group().getId(), GroupMemberStatus.ACTIVE).stream()
                .filter(m -> m.getMember().getStatus() == MemberStatus.ACTIVE)
                .sorted(Comparator.comparing(GroupMember::getId)).toList();
        Map<Long, LocalDateTime> joined = active.stream().collect(Collectors.toMap(GroupMember::getId, GroupMember::getJoinedAt));
        Set<Long> responders = responses.findAllByAvailabilityPollId(p.getId()).stream()
                .filter(r -> Objects.equals(joined.get(r.getGroupMemberId()), r.getMembershipJoinedAt()))
                .map(AvailabilityResponse::getGroupMemberId).collect(Collectors.toSet());
        List<AvailabilitySlot> candidateSlots = slots.findAllByAvailabilityPollIdOrderBySlotAt(p.getId());
        Map<Long, LocalDateTime> times = candidateSlots.stream().collect(Collectors.toMap(AvailabilitySlot::getId, AvailabilitySlot::getSlotAt));
        Map<LocalDateTime, Set<Long>> attendees = new HashMap<>();
        for (AvailabilityVote v : votes.findForPoll(p.getId())) {
            if (v.isAvailable() && responders.contains(v.getGroupMemberId()))
                attendees.computeIfAbsent(times.get(v.getAvailabilitySlotId()), ignored -> new HashSet<>()).add(v.getGroupMemberId());
        }
        String roster = active.stream().map(m -> m.getId() + ":" + m.getJoinedAt() + ":" + m.getUpdatedAt() + ":" + m.getMember().getUpdatedAt()).collect(Collectors.joining(","));
        Schedule s = access.schedule();
        String token = digest(p.getId() + ":" + p.getRevision() + ":" + p.effectiveStatus(now)
                + ":" + s.getTimeRevision() + ":" + s.getStartsAt() + ":" + s.getEndsAt() + ":" + s.getStatus()
                + ":" + access.group().getStatus() + ":" + roster);
        var recommendations = AvailabilityRecommendation.calculate(candidateSlots.stream().map(AvailabilitySlot::getSlotAt).toList(),
                attendees, Duration.between(s.getStartsAt(), s.getEndsAt()), p.getStartHour(), p.getEndHour(), now);
        return new Snapshot(access, p, candidateSlots, responders, attendees, active.size(), token, recommendations, now);
    }
    private String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
