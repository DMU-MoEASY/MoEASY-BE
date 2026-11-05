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
import com.moeasy.moeasybe.domain.settlement.enums.SettlementItemStatus;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementPaymentStatus;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementPaymentType;
import com.moeasy.moeasybe.domain.settlement.exception.SettlementException;
import com.moeasy.moeasybe.domain.settlement.exception.code.SettlementErrorCode;
import com.moeasy.moeasybe.domain.settlement.repository.SettlementItemRepository;
import com.moeasy.moeasybe.domain.settlement.repository.SettlementPaymentRepository;
import com.moeasy.moeasybe.domain.settlement.repository.SettlementRepository;
import com.moeasy.moeasybe.domain.settlement.service.result.SettlementPaymentSummary;
import com.moeasy.moeasybe.domain.settlement.service.support.SettlementTargetFinder;
import com.moeasy.moeasybe.domain.settlement.util.SettlementSplitCalculator;
import com.moeasy.moeasybe.domain.settlement.util.SettlementSplitCalculator.Allocation;
import com.moeasy.moeasybe.global.apiPayload.code.GeneralErrorCode;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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
    private final SettlementPaymentRepository settlementPaymentRepository;
    private final SettlementSplitCalculator splitCalculator;
    private final SettlementTargetFinder targetFinder;

    @Transactional(readOnly = true)
    public SettlementResDTO.PaymentPage getPaymentHistory(
            Long groupId, Long settlementId, Long settlementItemId,
            SettlementReqDTO.PaymentSearch request, Long memberId) {
        if (groupId == null || settlementId == null || settlementItemId == null
                || request == null || memberId == null) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST);
        }
        if ((long) request.page() * request.size() > Integer.MAX_VALUE) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST);
        }
        groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupException(GroupErrorCode.GROUP_NOT_FOUND));
        requireMember(groupId, memberId);
        settlementRepository.findByIdAndGroupIdAndDeletedAtIsNull(settlementId, groupId)
                .orElseThrow(() -> new SettlementException(SettlementErrorCode.SETTLEMENT_NOT_FOUND));
        if (!settlementItemRepository.existsByIdAndSettlementId(settlementItemId, settlementId)) {
            throw new SettlementException(SettlementErrorCode.ITEM_NOT_FOUND);
        }

        PageRequest pageable = PageRequest.of(request.page(), request.size(),
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return SettlementConverter.toPaymentPage(
                settlementPaymentRepository.findAllBySettlementItemId(settlementItemId, pageable));
    }

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
        Map<Long, Long> paidAmounts = items.isEmpty() ? Map.of()
                : settlementPaymentRepository.sumAmountsBySettlementIdAndTypeAndStatus(
                        settlementId, SettlementPaymentType.PAYMENT, SettlementPaymentStatus.COMPLETED).stream()
                        .collect(Collectors.toMap(SettlementPaymentRepository.ItemPaidAmount::getItemId,
                                SettlementPaymentRepository.ItemPaidAmount::getPaidAmount));
        return SettlementConverter.toDetail(settlement, items, summarizeItems(items, paidAmounts), paidAmounts);
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
        return SettlementConverter.toPage(settlements, findPaymentSummaries(settlementIds));
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

    private Map<Long, SettlementPaymentSummary> findPaymentSummaries(List<Long> settlementIds) {
        if (settlementIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, SettlementItemRepository.Summary> charges = settlementItemRepository.summarizeBySettlementIds(
                settlementIds, SettlementItemStatus.PAID, SettlementItemStatus.PENDING).stream()
                .collect(Collectors.toMap(SettlementItemRepository.Summary::getSettlementId, Function.identity()));
        Map<Long, Long> paidAmounts = settlementPaymentRepository.sumAmountsBySettlementIdsAndTypeAndStatus(
                settlementIds, SettlementPaymentType.PAYMENT, SettlementPaymentStatus.COMPLETED).stream()
                .collect(Collectors.toMap(SettlementPaymentRepository.SettlementPaidAmount::getSettlementId,
                        SettlementPaymentRepository.SettlementPaidAmount::getPaidAmount));
        Map<Long, SettlementPaymentSummary> summaries = new HashMap<>();
        for (Long id : settlementIds) {
            var charge = charges.get(id);
            long paidAmount = paidAmounts.getOrDefault(id, 0L);
            summaries.put(id, charge == null ? new SettlementPaymentSummary(0, 0L, 0L, 0, 0)
                    : new SettlementPaymentSummary(Math.toIntExact(charge.getTargetCount()), paidAmount,
                            Math.subtractExact(charge.getChargedAmount(), paidAmount),
                            Math.toIntExact(charge.getPaidCount()), Math.toIntExact(charge.getPendingCount())));
        }
        return summaries;
    }

    private SettlementPaymentSummary summarizeItems(List<SettlementItem> items, Map<Long, Long> paidAmounts) {
        long totalPaidAmount = 0L;
        long remainingAmount = 0L;
        int paidCount = 0;
        int pendingCount = 0;
        for (SettlementItem item : items) {
            long paidAmount = paidAmounts.getOrDefault(item.getId(), 0L);
            totalPaidAmount = Math.addExact(totalPaidAmount, paidAmount);
            remainingAmount = Math.addExact(remainingAmount, Math.subtractExact(item.getAmount(), paidAmount));
            if (item.getStatus() == SettlementItemStatus.PAID) {
                paidCount++;
            } else if (item.getStatus() == SettlementItemStatus.PENDING) {
                pendingCount++;
            }
        }
        return new SettlementPaymentSummary(items.size(), totalPaidAmount, remainingAmount, paidCount, pendingCount);
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
