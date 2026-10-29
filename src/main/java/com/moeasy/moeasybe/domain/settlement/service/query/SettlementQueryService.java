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
import com.moeasy.moeasybe.domain.settlement.entity.Settlement;
import com.moeasy.moeasybe.domain.settlement.entity.SettlementItem;
import com.moeasy.moeasybe.domain.settlement.exception.SettlementException;
import com.moeasy.moeasybe.domain.settlement.exception.code.SettlementErrorCode;
import com.moeasy.moeasybe.domain.settlement.repository.SettlementItemRepository;
import com.moeasy.moeasybe.domain.settlement.repository.SettlementRepository;
import com.moeasy.moeasybe.domain.settlement.service.support.SettlementTargetFinder;
import com.moeasy.moeasybe.domain.settlement.util.SettlementSplitCalculator;
import com.moeasy.moeasybe.domain.settlement.util.SettlementSplitCalculator.Allocation;
import com.moeasy.moeasybe.global.apiPayload.code.GeneralErrorCode;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettlementQueryService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementItemRepository settlementItemRepository;
    private final SettlementSplitCalculator splitCalculator;
    private final SettlementTargetFinder targetFinder;

    @Transactional(readOnly = true)
    public SettlementResDTO.Detail getSettlement(Long groupId, Long settlementId, Long memberId) {
        if (groupId == null || settlementId == null || memberId == null) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST);
        }
        groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupException(GroupErrorCode.GROUP_NOT_FOUND));
        requireMember(groupId, memberId);

        Settlement settlement = settlementRepository.findByIdAndGroupIdAndDeletedAtIsNull(settlementId, groupId)
                .orElseThrow(() -> new SettlementException(SettlementErrorCode.SETTLEMENT_NOT_FOUND));
        List<SettlementItem> items = settlementItemRepository.findAllBySettlementIdOrderByGroupMemberIdAsc(settlementId);
        return SettlementConverter.toDetail(settlement, items);
    }

    @Transactional(readOnly = true)
    public SettlementResDTO.Page getSettlements(
            Long groupId, SettlementReqDTO.Search request, Long memberId) {
        if (groupId == null || request == null || memberId == null) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST);
        }
        if ((long) request.page() * request.size() > Integer.MAX_VALUE) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST);
        }
        groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupException(GroupErrorCode.GROUP_NOT_FOUND));
        requireMember(groupId, memberId);

        PageRequest pageable = PageRequest.of(request.page(), request.size(),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<Settlement> settlements = settlementRepository.findPageByGroup(
                groupId, request.status(), request.scheduleId(), pageable);
        List<Long> settlementIds = settlements.getContent().stream().map(Settlement::getId).toList();
        Map<Long, Integer> targetCounts = settlementIds.isEmpty() ? Map.of()
                : settlementItemRepository.countTargetsBySettlementIds(settlementIds).stream()
                        .collect(Collectors.toMap(SettlementItemRepository.TargetCount::getSettlementId,
                                count -> Math.toIntExact(count.getTargetCount())));
        return SettlementConverter.toPage(settlements, targetCounts);
    }

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
        GroupMember requester = requireMember(groupId, memberId);
        if (requester.getRole() != GroupMemberRole.OWNER && requester.getRole() != GroupMemberRole.MANAGER) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        }
    }

    private GroupMember requireMember(Long groupId, Long memberId) {
        return groupMemberRepository
                .findByGroupIdAndMemberIdAndStatusAndDeletedAtIsNullAndMemberDeletedAtIsNull(
                        groupId, memberId, GroupMemberStatus.ACTIVE)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.FORBIDDEN));
    }
}
