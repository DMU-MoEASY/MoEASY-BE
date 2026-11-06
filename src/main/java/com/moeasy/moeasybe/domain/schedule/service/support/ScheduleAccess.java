package com.moeasy.moeasybe.domain.schedule.service.support;

import java.time.LocalDateTime;
import java.time.Clock;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import com.moeasy.moeasybe.domain.group.entity.Group;
import com.moeasy.moeasybe.domain.group.entity.GroupMember;
import com.moeasy.moeasybe.domain.group.enums.GroupMemberRole;
import com.moeasy.moeasybe.domain.group.enums.GroupMemberStatus;
import com.moeasy.moeasybe.domain.group.enums.GroupStatus;
import com.moeasy.moeasybe.domain.group.repository.GroupMemberRepository;
import com.moeasy.moeasybe.domain.group.repository.GroupRepository;
import com.moeasy.moeasybe.domain.schedule.exception.ScheduleException;
import com.moeasy.moeasybe.domain.schedule.exception.code.ScheduleErrorCode;
import com.moeasy.moeasybe.global.apiPayload.code.GeneralErrorCode;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;
import com.moeasy.moeasybe.domain.schedule.entity.Schedule;
import com.moeasy.moeasybe.domain.schedule.repository.ScheduleRepository;
import com.moeasy.moeasybe.domain.member.enums.MemberStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScheduleAccess {
    private final Clock clock;
    private final ScheduleRepository schedules;
    private final GroupRepository groups;
    private final GroupMemberRepository members;

    public LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Seoul")))
                .truncatedTo(ChronoUnit.MICROS);
    }

    public Group require(Long groupId, Long memberId, boolean write, boolean ownerOnly) {
        if (groupId == null || memberId == null) throw new GeneralException(GeneralErrorCode.BAD_REQUEST);
        Group group = (write ? groups.findByIdForUpdate(groupId) : groups.findById(groupId))
                .filter(g -> g.getDeletedAt() == null && g.getStatus() != GroupStatus.CLOSED)
                .orElseThrow(() -> new ScheduleException(ScheduleErrorCode.GROUP_NOT_FOUND));
        GroupMember member = members
                .findByGroupIdAndMemberIdAndStatusAndDeletedAtIsNullAndMemberDeletedAtIsNull(
                        groupId, memberId, GroupMemberStatus.ACTIVE)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.FORBIDDEN));
        if (write && (ownerOnly ? member.getRole() != GroupMemberRole.OWNER
                : member.getRole() != GroupMemberRole.OWNER && member.getRole() != GroupMemberRole.MANAGER)) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        }
        if (write && group.getStatus() == GroupStatus.ARCHIVED) {
            throw new ScheduleException(ScheduleErrorCode.GROUP_ARCHIVED);
        }
        return group;
    }

    public record Access(Schedule schedule, Group group, GroupMember actor) {}

    public Access access(Long scheduleId, Long memberId, boolean write, boolean manager, boolean owner) {
        if (scheduleId == null || memberId == null) throw new GeneralException(GeneralErrorCode.BAD_REQUEST);
        Long groupId = schedules.findGroupId(scheduleId)
                .orElseThrow(() -> new ScheduleException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));
        Group group = (write ? groups.findByIdForUpdate(groupId) : groups.findById(groupId))
                .filter(g -> g.getDeletedAt() == null && g.getStatus() != GroupStatus.CLOSED)
                .orElseThrow(() -> new ScheduleException(ScheduleErrorCode.GROUP_NOT_FOUND));
        GroupMember actor = members.findByGroupIdAndMemberIdAndStatusAndDeletedAtIsNullAndMemberDeletedAtIsNull(
                groupId, memberId, GroupMemberStatus.ACTIVE)
                .filter(m -> m.getMember().getStatus() == MemberStatus.ACTIVE)
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
}
