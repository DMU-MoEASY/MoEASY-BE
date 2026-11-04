package com.moeasy.moeasybe.domain.settlement.service.command;

import com.moeasy.moeasybe.domain.group.entity.Group;
import com.moeasy.moeasybe.domain.group.entity.GroupMember;
import com.moeasy.moeasybe.domain.group.enums.GroupMemberRole;
import com.moeasy.moeasybe.domain.group.enums.GroupMemberStatus;
import com.moeasy.moeasybe.domain.group.enums.GroupStatus;
import com.moeasy.moeasybe.domain.group.exception.GroupException;
import com.moeasy.moeasybe.domain.group.exception.code.GroupErrorCode;
import com.moeasy.moeasybe.domain.group.repository.GroupMemberRepository;
import com.moeasy.moeasybe.domain.group.repository.GroupRepository;
import com.moeasy.moeasybe.domain.schedule.repository.ScheduleRepository;
import com.moeasy.moeasybe.domain.settlement.converter.SettlementConverter;
import com.moeasy.moeasybe.domain.settlement.dto.request.SettlementReqDTO;
import com.moeasy.moeasybe.domain.settlement.dto.response.SettlementResDTO;
import com.moeasy.moeasybe.domain.settlement.entity.Settlement;
import com.moeasy.moeasybe.domain.settlement.entity.SettlementItem;
import com.moeasy.moeasybe.domain.settlement.entity.SettlementPayment;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementPaymentStatus;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementPaymentType;
import com.moeasy.moeasybe.domain.settlement.exception.SettlementException;
import com.moeasy.moeasybe.domain.settlement.exception.code.SettlementErrorCode;
import com.moeasy.moeasybe.domain.settlement.repository.SettlementItemRepository;
import com.moeasy.moeasybe.domain.settlement.repository.SettlementPaymentRepository;
import com.moeasy.moeasybe.domain.settlement.repository.SettlementRepository;
import com.moeasy.moeasybe.domain.settlement.service.support.SettlementTargetFinder;
import com.moeasy.moeasybe.domain.settlement.util.SettlementSplitCalculator;
import com.moeasy.moeasybe.domain.settlement.util.SettlementSplitCalculator.Allocation;
import com.moeasy.moeasybe.global.apiPayload.code.GeneralErrorCode;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SettlementCommandService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ScheduleRepository scheduleRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementItemRepository settlementItemRepository;
    private final SettlementPaymentRepository settlementPaymentRepository;
    private final SettlementSplitCalculator splitCalculator;
    private final SettlementTargetFinder targetFinder;
    private final Clock clock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SettlementResDTO.PaymentRegistered registerPayment(
            Long groupId, Long settlementId, Long settlementItemId,
            SettlementReqDTO.RegisterPayment request, Long memberId) {
        if (groupId == null || settlementId == null || settlementItemId == null || memberId == null
                || request == null || request.amount() == null || request.expectedVersion() == null) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST);
        }
        groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupException(GroupErrorCode.GROUP_NOT_FOUND));
        GroupMember recorder = requireOperator(groupId, memberId);
        settlementRepository.findByIdAndGroupIdAndDeletedAtIsNull(settlementId, groupId)
                .orElseThrow(() -> new SettlementException(SettlementErrorCode.SETTLEMENT_NOT_FOUND));
        SettlementItem item = settlementItemRepository
                .findByIdAndSettlementIdForUpdate(settlementItemId, settlementId)
                .orElseThrow(() -> new SettlementException(SettlementErrorCode.ITEM_NOT_FOUND));

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), SEOUL);
        LocalDateTime occurredAt = request.occurredAt() == null ? now : request.occurredAt();
        if (occurredAt.isAfter(now)) {
            throw new SettlementException(SettlementErrorCode.INVALID_OCCURRED_AT);
        }
        // 잠금 획득 후 최신 커밋된 기록을 집계한다. 취소·대기·실패·환불 기록은 합산하지 않는다.
        long currentPaidAmount = settlementPaymentRepository.sumAmountByItemAndTypeAndStatus(
                item.getId(), SettlementPaymentType.PAYMENT, SettlementPaymentStatus.COMPLETED);
        long paidAmount = item.applyPayment(request.expectedVersion(), currentPaidAmount, request.amount());
        SettlementPayment payment = settlementPaymentRepository.save(
                SettlementConverter.toPayment(item, request.amount(), occurredAt, recorder));
        return SettlementConverter.toPaymentRegistered(payment, item, paidAmount);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SettlementResDTO.PaymentCancelled cancelPayment(
            Long groupId, Long settlementId, Long settlementItemId, Long paymentId,
            SettlementReqDTO.CancelPayment request, Long memberId) {
        if (groupId == null || settlementId == null || settlementItemId == null || paymentId == null
                || memberId == null || request == null || request.expectedVersion() == null || request.reason() == null) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST);
        }
        groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupException(GroupErrorCode.GROUP_NOT_FOUND));
        GroupMember canceller = requireOperator(groupId, memberId);
        settlementRepository.findByIdAndGroupIdAndDeletedAtIsNull(settlementId, groupId)
                .orElseThrow(() -> new SettlementException(SettlementErrorCode.SETTLEMENT_NOT_FOUND));
        SettlementItem item = settlementItemRepository
                .findByIdAndSettlementIdForUpdate(settlementItemId, settlementId)
                .orElseThrow(() -> new SettlementException(SettlementErrorCode.ITEM_NOT_FOUND));
        SettlementPayment payment = settlementPaymentRepository
                .findByIdAndSettlementItemId(paymentId, item.getId())
                .orElseThrow(() -> new SettlementException(SettlementErrorCode.PAYMENT_NOT_FOUND));

        // 취소 전 합계를 읽는다. 취소 상태로 바꾼 뒤 집계하면 해당 금액이 두 번 차감된다.
        long currentPaidAmount = settlementPaymentRepository.sumAmountByItemAndTypeAndStatus(
                item.getId(), SettlementPaymentType.PAYMENT, SettlementPaymentStatus.COMPLETED);
        LocalDateTime cancelledAt = LocalDateTime.ofInstant(clock.instant(), SEOUL);
        payment.cancel(canceller, cancelledAt, request.reason());
        long paidAmount = item.cancelPayment(request.expectedVersion(), currentPaidAmount, payment.getAmount());
        return SettlementConverter.toPaymentCancelled(payment, item, paidAmount);
    }

    @Transactional
    public SettlementResDTO.Created createSettlement(
            Long groupId, SettlementReqDTO.Create request, Long memberId) {
        if (groupId == null || request == null || memberId == null) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST);
        }
        Group group = groupRepository.findByIdAndStatusNotAndDeletedAtIsNull(groupId, GroupStatus.CLOSED)
                .orElseThrow(() -> new GroupException(GroupErrorCode.GROUP_NOT_FOUND));
        GroupMember creator = requireOperator(groupId, memberId);
        validateSchedule(groupId, request.scheduleId());
        List<Allocation> allocations = splitCalculator.calculate(
                request.splitMode(), request.totalAmount(), request.groupMemberIds(), request.memberAmounts());
        Map<Long, GroupMember> targets = targetFinder.findTargets(groupId, allocations);
        validateDueAt(request.dueAt());

        Settlement settlement = settlementRepository.save(SettlementConverter.toEntity(request, group, creator));
        settlementItemRepository.saveAll(SettlementConverter.toItems(settlement, allocations, targets));
        return SettlementConverter.toCreated(settlement);
    }

    private GroupMember requireOperator(Long groupId, Long memberId) {
        GroupMember operator = groupMemberRepository
                .findByGroupIdAndMemberIdAndStatusAndDeletedAtIsNullAndMemberDeletedAtIsNull(
                        groupId, memberId, GroupMemberStatus.ACTIVE)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.FORBIDDEN));
        if (operator.getRole() != GroupMemberRole.OWNER && operator.getRole() != GroupMemberRole.MANAGER) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        }
        return operator;
    }

    private void validateSchedule(Long groupId, Long scheduleId) {
        if (scheduleId != null && !scheduleRepository
                .existsByIdAndGroupIdAndDeletedAtIsNull(scheduleId, groupId)) {
            throw new SettlementException(SettlementErrorCode.INVALID_SCHEDULE);
        }
    }

    private void validateDueAt(LocalDateTime dueAt) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), SEOUL);
        if (dueAt != null && !dueAt.isAfter(now)) {
            throw new SettlementException(SettlementErrorCode.INVALID_DUE_AT);
        }
    }
}
