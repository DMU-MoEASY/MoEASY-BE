package com.moeasy.moeasybe.domain.group.validate;

import com.moeasy.moeasybe.domain.group.enums.GroupMemberRole;
import com.moeasy.moeasybe.domain.group.enums.GroupMemberStatus;
import com.moeasy.moeasybe.domain.group.exception.GroupException;
import com.moeasy.moeasybe.domain.group.exception.code.GroupErrorCode;
import com.moeasy.moeasybe.domain.group.repository.GroupDeletionHistoryRepository;
import com.moeasy.moeasybe.domain.group.repository.GroupMemberRepository;
import com.moeasy.moeasybe.global.apiPayload.code.GeneralErrorCode;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GroupAccessValidator {
    private final GroupMemberRepository groupMemberRepository;
    private final GroupDeletionHistoryRepository deletionHistoryRepository;

    public void requireOwner(Long groupId, Long memberId) {
        if (!groupMemberRepository.existsByGroupIdAndMemberIdAndRoleAndStatus(
                groupId, memberId, GroupMemberRole.OWNER, GroupMemberStatus.ACTIVE)) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        }
    }

    public void requireNoDeletionHistory(Long groupId) {
        if (deletionHistoryRepository.existsByGroupId(groupId)) {
            throw new GroupException(GroupErrorCode.GROUP_NOT_FOUND);
        }
    }
}
