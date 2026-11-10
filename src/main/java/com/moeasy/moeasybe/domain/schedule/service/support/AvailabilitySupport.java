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
import java.util.*;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AvailabilitySupport {
    private final ScheduleRepository schedules;
    private final GroupRepository groups;
    private final GroupMemberRepository members;
    private final AvailabilityPollRepository polls;
    private final AvailabilitySlotRepository slots;
    private final AvailabilityResponseRepository responses;
    private final AvailabilityVoteRepository votes;
    private final Clock clock;

    public record Access(Schedule schedule, Group group, GroupMember actor) { }
    public record Snapshot(Access access, AvailabilityPoll poll, List<AvailabilitySlot> slots,
            Set<Long> responders, Map<LocalDateTime, Set<Long>> attendees, int targetCount,
            String token, List<AvailabilityRecommendation.Window> recommendations, LocalDateTime now) { }

    public LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Seoul")));
    }
    public Access access(Long scheduleId, Long memberId, boolean write, boolean manager, boolean owner) {
        if (scheduleId == null || memberId == null) throw new GeneralException(GeneralErrorCode.BAD_REQUEST);
        Long groupId = schedules.findGroupId(scheduleId)
                .orElseThrow(() -> new ScheduleException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));
        Group group = (write ? groups.findByIdForUpdate(groupId) : groups.findById(groupId))
                .filter(candidateGroup -> candidateGroup.getDeletedAt() == null && candidateGroup.getStatus() != GroupStatus.CLOSED)
                .orElseThrow(() -> new ScheduleException(ScheduleErrorCode.GROUP_NOT_FOUND));
        GroupMember actor = members.findByGroupIdAndMemberIdAndStatusAndDeletedAtIsNullAndMemberDeletedAtIsNull(
                groupId, memberId, GroupMemberStatus.ACTIVE)
                .filter(membership -> membership.getMember().getStatus() == MemberStatus.ACTIVE)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.FORBIDDEN));
        if (owner && actor.getRole() != GroupMemberRole.OWNER
                || manager && actor.getRole() != GroupMemberRole.OWNER && actor.getRole() != GroupMemberRole.MANAGER) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        }
        if (write && group.getStatus() == GroupStatus.ARCHIVED) throw new ScheduleException(ScheduleErrorCode.GROUP_ARCHIVED);
        Schedule schedule = (write ? schedules.findByIdForUpdate(scheduleId) : schedules.findByIdAndDeletedAtIsNull(scheduleId))
                .orElseThrow(() -> new ScheduleException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));
        return new Access(schedule, group, actor);
    }
    public void requireEditable(Schedule schedule) {
        if (schedule.getStatus() != ScheduleStatus.SCHEDULED || !schedule.getStartsAt().isAfter(now()))
            throw new ScheduleException(ScheduleErrorCode.SCHEDULE_NOT_EDITABLE);
    }
    public AvailabilityPoll poll(Long scheduleId) {
        return polls.findByScheduleId(scheduleId).orElseThrow(() -> new AvailabilityException(AvailabilityErrorCode.NOT_FOUND));
    }
    public Snapshot snapshot(Access access) {
        AvailabilityPoll poll = poll(access.schedule().getId());
        LocalDateTime now = now();
        List<GroupMember> active = members.findAllByGroupIdAndStatusAndDeletedAtIsNullAndMemberDeletedAtIsNull(
                access.group().getId(), GroupMemberStatus.ACTIVE).stream()
                .filter(membership -> membership.getMember().getStatus() == MemberStatus.ACTIVE)
                .sorted(Comparator.comparing(GroupMember::getId)).toList();
        Map<Long, LocalDateTime> joined = active.stream().collect(Collectors.toMap(GroupMember::getId, GroupMember::getJoinedAt));
        Set<Long> responders = responses.findAllByAvailabilityPollId(poll.getId()).stream()
                .filter(response -> Objects.equals(joined.get(response.getGroupMemberId()), response.getMembershipJoinedAt()))
                .map(AvailabilityResponse::getGroupMemberId).collect(Collectors.toSet());
        List<AvailabilitySlot> candidateSlots = slots.findAllByAvailabilityPollIdOrderBySlotAt(poll.getId());
        Map<Long, LocalDateTime> times = candidateSlots.stream().collect(Collectors.toMap(AvailabilitySlot::getId, AvailabilitySlot::getSlotAt));
        Map<LocalDateTime, Set<Long>> attendees = new HashMap<>();
        for (AvailabilityVote vote : votes.findForPoll(poll.getId())) {
            if (vote.isAvailable() && responders.contains(vote.getGroupMemberId()))
                attendees.computeIfAbsent(times.get(vote.getAvailabilitySlotId()), ignored -> new HashSet<>()).add(vote.getGroupMemberId());
        }
        String roster = active.stream().map(membership -> membership.getId() + ":" + membership.getJoinedAt() + ":" + membership.getUpdatedAt() + ":" + membership.getMember().getUpdatedAt()).collect(Collectors.joining(","));
        Schedule schedule = access.schedule();
        String token = digest(poll.getId() + ":" + poll.getRevision() + ":" + poll.effectiveStatus(now)
                + ":" + schedule.getTimeRevision() + ":" + schedule.getStartsAt() + ":" + schedule.getEndsAt() + ":" + schedule.getStatus()
                + ":" + access.group().getStatus() + ":" + roster);
        var recommendations = AvailabilityRecommendation.calculate(candidateSlots.stream().map(AvailabilitySlot::getSlotAt).toList(),
                attendees, Duration.between(schedule.getStartsAt(), schedule.getEndsAt()), poll.getStartHour(), poll.getEndHour(), now);
        return new Snapshot(access, poll, candidateSlots, responders, attendees, active.size(), token, recommendations, now);
    }
    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
