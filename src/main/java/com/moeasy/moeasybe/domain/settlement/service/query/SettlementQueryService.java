package com.moeasy.moeasybe.domain.settlement.service.query;

import com.moeasy.moeasybe.domain.group.entity.GroupMember;
import com.moeasy.moeasybe.domain.group.enums.GroupMemberRole;
import com.moeasy.moeasybe.domain.group.enums.GroupMemberStatus;
import com.moeasy.moeasybe.domain.group.enums.GroupStatus;
import com.moeasy.moeasybe.domain.group.exception.GroupException;
import com.moeasy.moeasybe.domain.group.exception.code.GroupErrorCode;
import com.moeasy.moeasybe.domain.group.repository.GroupMemberRepository;
import com.moeasy.moeasybe.domain.group.repository.GroupRepository;
import com.moeasy.moeasybe.domain.settlement.converter.SettlementConverter;
import com.moeasy.moeasybe.domain.settlement.dto.request.SettlementReqDTO;
import com.moeasy.moeasybe.domain.settlement.dto.response.SettlementResDTO;
import com.moeasy.moeasybe.domain.settlement.service.support.SettlementTargetFinder;
import com.moeasy.moeasybe.domain.settlement.util.SettlementSplitCalculator;
import com.moeasy.moeasybe.domain.settlement.util.SettlementSplitCalculator.Allocation;
import com.moeasy.moeasybe.global.apiPayload.code.GeneralErrorCode;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettlementQueryService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final SettlementSplitCalculator splitCalculator;
    private final SettlementTargetFinder targetFinder;

    @Transactional(readOnly = true)
    public SettlementResDTO.Preview previewSplit(
            Long groupId, SettlementReqDTO.Preview request, Long memberId) {
        if (groupId == null || request == null || memberId == null) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST);
        }
        groupRepository.findByIdAndStatusNotAndDeletedAtIsNull(groupId, GroupStatus.CLOSED)
                .orElseThrow(() -> new GroupException(GroupErrorCode.GROUP_NOT_FOUND));
        requireManager(groupId, memberId);
        List<Allocation> allocations = splitCalculator.calculate(
                request.splitMode(), request.totalAmount(), request.groupMemberIds(), request.memberAmounts());
        Map<Long, GroupMember> targets = targetFinder.findTargets(groupId, allocations);
        return SettlementConverter.toPreview(request.splitMode(), request.totalAmount(), allocations, targets);
    }

    private void requireManager(Long groupId, Long memberId) {
        GroupMember requester = groupMemberRepository
                .findByGroupIdAndMemberIdAndStatusAndDeletedAtIsNullAndMemberDeletedAtIsNull(
                        groupId, memberId, GroupMemberStatus.ACTIVE)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.FORBIDDEN));
        if (requester.getRole() != GroupMemberRole.OWNER && requester.getRole() != GroupMemberRole.MANAGER) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        }
    }
}
