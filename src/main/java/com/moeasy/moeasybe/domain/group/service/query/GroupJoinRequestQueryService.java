package com.moeasy.moeasybe.domain.group.service.query;

import com.moeasy.moeasybe.domain.group.converter.GroupJoinRequestConverter;
import com.moeasy.moeasybe.domain.group.dto.response.GroupJoinRequestResDTO;
import com.moeasy.moeasybe.domain.group.enums.GroupJoinRequestStatus;
import com.moeasy.moeasybe.domain.group.enums.GroupStatus;
import com.moeasy.moeasybe.domain.group.exception.GroupException;
import com.moeasy.moeasybe.domain.group.exception.code.GroupErrorCode;
import com.moeasy.moeasybe.domain.group.repository.GroupJoinRequestRepository;
import com.moeasy.moeasybe.domain.group.repository.GroupRepository;
import com.moeasy.moeasybe.domain.group.validate.GroupAccessValidator;
import com.moeasy.moeasybe.domain.group.validate.GroupRequestValidator;
import com.moeasy.moeasybe.global.apiPayload.code.GeneralErrorCode;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GroupJoinRequestQueryService {
    private final GroupRepository groupRepository;
    private final GroupJoinRequestRepository joinRequestRepository;
    private final GroupAccessValidator accessValidator;

    @Transactional(readOnly = true)
    public GroupJoinRequestResDTO.PendingList getPending(Long groupId, int page, int size, Long memberId) {
        GroupRequestValidator.requireMemberId(memberId);
        if ((long) page * size > Integer.MAX_VALUE) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST);
        }
        requireGroup(groupId);
        accessValidator.requireJoinManager(groupId, memberId);
        return GroupJoinRequestConverter.toPendingList(joinRequestRepository
                .findByGroupIdAndStatusOrderByCreatedAtAscIdAsc(groupId, GroupJoinRequestStatus.PENDING,
                        PageRequest.of(page, size)));
    }

    @Transactional(readOnly = true)
    public GroupJoinRequestResDTO.Detail getMine(Long groupId, Long memberId) {
        GroupRequestValidator.requireMemberId(memberId);
        requireGroup(groupId);
        return joinRequestRepository.findFirstByGroupIdAndMemberIdOrderByCreatedAtDescIdDesc(groupId, memberId)
                .map(GroupJoinRequestConverter::toDetail).orElse(null);
    }

    private void requireGroup(Long groupId) {
        groupRepository.findByIdAndStatusNotAndDeletedAtIsNull(groupId, GroupStatus.CLOSED)
                .orElseThrow(() -> new GroupException(GroupErrorCode.GROUP_NOT_FOUND));
        accessValidator.requireNoDeletionHistory(groupId);
    }
}
