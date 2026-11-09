package com.moeasy.moeasybe.domain.bank.service.support;

import com.moeasy.moeasybe.domain.group.entity.GroupMember;
import com.moeasy.moeasybe.domain.group.enums.GroupMemberRole;
import com.moeasy.moeasybe.domain.group.enums.GroupMemberStatus;
import com.moeasy.moeasybe.domain.group.enums.GroupStatus;
import com.moeasy.moeasybe.domain.group.exception.GroupException;
import com.moeasy.moeasybe.domain.group.exception.code.GroupErrorCode;
import com.moeasy.moeasybe.domain.group.repository.GroupDeletionHistoryRepository;
import com.moeasy.moeasybe.domain.group.repository.GroupMemberRepository;
import com.moeasy.moeasybe.domain.group.repository.GroupRepository;
import com.moeasy.moeasybe.global.apiPayload.code.GeneralErrorCode;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BankAccess {
    private final GroupRepository groups;
    private final GroupMemberRepository members;
    private final GroupDeletionHistoryRepository deletions;

    public GroupMember requireMember(Long groupId, Long memberId) {
        groups.findByIdAndStatusNotAndDeletedAtIsNull(groupId, GroupStatus.CLOSED)
                .orElseThrow(() -> new GroupException(GroupErrorCode.GROUP_NOT_FOUND));
        if (deletions.existsByGroupId(groupId))
            throw new GroupException(GroupErrorCode.GROUP_NOT_FOUND);
        return members.findByGroupIdAndMemberIdAndStatusAndDeletedAtIsNullAndMemberDeletedAtIsNull(
                        groupId, memberId, GroupMemberStatus.ACTIVE)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.FORBIDDEN));
    }

    public void requireManager(Long groupId, Long memberId) {
        GroupMember member = requireMember(groupId, memberId);
        if (member.getRole() != GroupMemberRole.OWNER
                && member.getRole() != GroupMemberRole.MANAGER)
            throw new GeneralException(GeneralErrorCode.FORBIDDEN);
    }
}
