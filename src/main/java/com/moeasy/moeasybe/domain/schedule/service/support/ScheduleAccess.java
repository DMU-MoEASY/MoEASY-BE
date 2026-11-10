package com.moeasy.moeasybe.domain.schedule.service.support;

import com.moeasy.moeasybe.domain.group.entity.Group;
import com.moeasy.moeasybe.domain.group.entity.GroupMember;
import com.moeasy.moeasybe.domain.group.enums.GroupMemberRole;
import com.moeasy.moeasybe.domain.group.enums.GroupMemberStatus;
import com.moeasy.moeasybe.domain.group.enums.GroupStatus;
import com.moeasy.moeasybe.domain.group.repository.GroupMemberRepository;
import com.moeasy.moeasybe.domain.group.repository.GroupRepository;
import com.moeasy.moeasybe.domain.member.enums.MemberStatus;
import com.moeasy.moeasybe.domain.schedule.exception.ScheduleException;
import com.moeasy.moeasybe.domain.schedule.exception.code.ScheduleErrorCode;
import com.moeasy.moeasybe.global.apiPayload.code.GeneralErrorCode;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScheduleAccess {
    private final GroupRepository groups;
    private final GroupMemberRepository members;

    public Group require(Long groupId, Long memberId, boolean write, boolean ownerOnly) {
        if (groupId == null || memberId == null) throw new GeneralException(GeneralErrorCode.BAD_REQUEST);
        Group group = (write ? groups.findByIdForUpdate(groupId) : groups.findById(groupId))
                .filter(g -> g.getDeletedAt() == null && g.getStatus() != GroupStatus.CLOSED)
                .orElseThrow(() -> new ScheduleException(ScheduleErrorCode.GROUP_NOT_FOUND));
        GroupMember member = members
                .findByGroupIdAndMemberIdAndStatusAndDeletedAtIsNullAndMemberDeletedAtIsNull(
                        groupId, memberId, GroupMemberStatus.ACTIVE)
                .filter(membership -> membership.getMember().getStatus() == MemberStatus.ACTIVE)
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
}
