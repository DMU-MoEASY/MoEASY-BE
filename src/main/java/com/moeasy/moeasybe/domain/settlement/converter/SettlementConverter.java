package com.moeasy.moeasybe.domain.settlement.converter;

import com.moeasy.moeasybe.domain.group.entity.Group;
import com.moeasy.moeasybe.domain.group.entity.GroupMember;
import com.moeasy.moeasybe.domain.schedule.entity.Schedule;
import com.moeasy.moeasybe.domain.settlement.dto.request.SettlementReqDTO;
import com.moeasy.moeasybe.domain.settlement.dto.response.SettlementResDTO;
import com.moeasy.moeasybe.domain.settlement.entity.Settlement;
import com.moeasy.moeasybe.domain.settlement.entity.SettlementItem;
import com.moeasy.moeasybe.domain.settlement.entity.SettlementPayment;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementSplitMode;
import com.moeasy.moeasybe.domain.settlement.service.result.SettlementPaymentSummary;
import com.moeasy.moeasybe.domain.settlement.util.SettlementSplitCalculator.Allocation;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;

public final class SettlementConverter {

    private SettlementConverter() {
    }

    public static SettlementResDTO.PaymentPage toPaymentPage(Page<SettlementPayment> payments) {
        return SettlementResDTO.PaymentPage.builder()
                .items(payments.getContent().stream().map(SettlementConverter::toPaymentHistory).toList())
                .page(payments.getNumber())
                .size(payments.getSize())
                .totalElements(payments.getTotalElements())
                .totalPages(payments.getTotalPages())
                .hasNext(payments.hasNext())
                .build();
    }

    public static SettlementResDTO.PaymentHistory toPaymentHistory(SettlementPayment payment) {
        return SettlementResDTO.PaymentHistory.builder()
                .paymentId(payment.getId())
                .settlementItemId(payment.getSettlementItem().getId())
                .paymentType(payment.getPaymentType())
                .source(payment.getSource())
                .status(payment.getStatus())
                .amount(payment.getAmount())
                .occurredAt(payment.getOccurredAt())
                .createdAt(payment.getCreatedAt())
                .recordedBy(toPaymentActor(payment.getRecordedByGroupMember()))
                .cancelledAt(payment.getCancelledAt())
                .cancelledBy(toPaymentActor(payment.getCancelledByGroupMember()))
                .cancelReason(payment.getCancelReason())
                .build();
    }

    public static SettlementResDTO.PaymentActor toPaymentActor(GroupMember actor) {
        if (actor == null) {
            return null;
        }
        return SettlementResDTO.PaymentActor.builder()
                .groupMemberId(actor.getId())
                .nickname(actor.getMember().getNickname())
                .build();
    }

    public static SettlementPayment toPayment(
            SettlementItem item, long amount, LocalDateTime occurredAt, GroupMember recorder) {
        return SettlementPayment.builder()
                .settlementItem(item)
                .amount(amount)
                .occurredAt(occurredAt)
                .recordedByGroupMember(recorder)
                .build();
    }

    public static SettlementResDTO.PaymentRegistered toPaymentRegistered(
            SettlementPayment payment, SettlementItem item, long paidAmount) {
        return SettlementResDTO.PaymentRegistered.builder()
                .paymentId(payment.getId())
                .settlementItemId(item.getId())
                .status(item.getStatus())
                .paidAmount(paidAmount)
                .remainingAmount(item.getAmount() - paidAmount)
                .version(item.getVersion())
                .build();
    }

    public static SettlementResDTO.PaymentCancelled toPaymentCancelled(
            SettlementPayment payment, SettlementItem item, long paidAmount) {
        return SettlementResDTO.PaymentCancelled.builder()
                .paymentId(payment.getId())
                .settlementItemId(item.getId())
                .status(item.getStatus())
                .paidAmount(paidAmount)
                .remainingAmount(item.getAmount() - paidAmount)
                .version(item.getVersion())
                .build();
    }

    public static SettlementResDTO.Detail toDetail(Settlement settlement, List<SettlementItem> items,
            SettlementPaymentSummary summary, Map<Long, Long> paidAmounts) {
        return SettlementResDTO.Detail.builder()
                .settlementId(settlement.getId())
                .groupId(settlement.getGroup().getId())
                .scheduleId(settlement.getSchedule() == null ? null : settlement.getSchedule().getId())
                .title(settlement.getTitle())
                .description(settlement.getDescription())
                .status(settlement.getStatus())
                .splitMode(settlement.getSplitMode())
                .totalAmount(settlement.getTotalAmount())
                .paidAmount(summary.paidAmount())
                .remainingAmount(summary.remainingAmount())
                .paidCount(summary.paidCount())
                .pendingCount(summary.pendingCount())
                .targetCount(summary.targetCount())
                .dueAt(settlement.getDueAt())
                .createdAt(settlement.getCreatedAt())
                .createdBy(toCreator(settlement.getCreatedByGroupMember()))
                .items(items.stream().map(item -> toItem(item, paidAmounts.getOrDefault(item.getId(), 0L))).toList())
                .build();
    }

    public static SettlementResDTO.Creator toCreator(GroupMember creator) {
        return SettlementResDTO.Creator.builder()
                .groupMemberId(creator.getId())
                .nickname(creator.getMember().getNickname())
                .build();
    }

    public static SettlementResDTO.Item toItem(SettlementItem item, long paidAmount) {
        return SettlementResDTO.Item.builder()
                .settlementItemId(item.getId())
                .groupMemberId(item.getGroupMember().getId())
                .nickname(item.getGroupMember().getMember().getNickname())
                .amount(item.getAmount())
                .status(item.getStatus())
                .paidAmount(paidAmount)
                .remainingAmount(item.getAmount() - paidAmount)
                .version(item.getVersion())
                .build();
    }

    public static SettlementResDTO.Page toPage(Page<Settlement> settlements,
            Map<Long, SettlementPaymentSummary> summaries) {
        List<SettlementResDTO.Summary> items = settlements.getContent().stream()
                .map(settlement -> toSummary(settlement, summaries.get(settlement.getId())))
                .toList();
        return SettlementResDTO.Page.builder()
                .items(items)
                .page(settlements.getNumber())
                .size(settlements.getSize())
                .totalElements(settlements.getTotalElements())
                .totalPages(settlements.getTotalPages())
                .hasNext(settlements.hasNext())
                .build();
    }

    public static SettlementResDTO.Summary toSummary(Settlement settlement, SettlementPaymentSummary summary) {
        return SettlementResDTO.Summary.builder()
                .settlementId(settlement.getId())
                .title(settlement.getTitle())
                .scheduleId(settlement.getSchedule() == null ? null : settlement.getSchedule().getId())
                .status(settlement.getStatus())
                .splitMode(settlement.getSplitMode())
                .totalAmount(settlement.getTotalAmount())
                .paidAmount(summary.paidAmount())
                .remainingAmount(summary.remainingAmount())
                .paidCount(summary.paidCount())
                .pendingCount(summary.pendingCount())
                .targetCount(summary.targetCount())
                .dueAt(settlement.getDueAt())
                .createdAt(settlement.getCreatedAt())
                .build();
    }

    public static Settlement toEntity(
            SettlementReqDTO.Create request, Group group, GroupMember creator, Schedule schedule) {
        return Settlement.builder()
                .group(group)
                .createdByGroupMember(creator)
                .schedule(schedule)
                .title(request.title())
                .description(request.description())
                .dueAt(request.dueAt())
                .totalAmount(request.totalAmount())
                .splitMode(request.splitMode())
                .build();
    }

    public static List<SettlementItem> toItems(
            Settlement settlement, List<Allocation> allocations, Map<Long, GroupMember> targets) {
        return allocations.stream()
                .map(allocation -> SettlementItem.builder()
                        .settlement(settlement)
                        .groupMember(targets.get(allocation.groupMemberId()))
                        .amount(allocation.amount())
                        .build())
                .toList();
    }

    public static SettlementResDTO.Created toCreated(Settlement settlement) {
        return SettlementResDTO.Created.builder().settlementId(settlement.getId()).build();
    }

    public static SettlementResDTO.Preview toPreview(
            SettlementSplitMode splitMode, Long totalAmount,
            List<Allocation> allocations, Map<Long, GroupMember> targets) {
        List<SettlementResDTO.PreviewItem> items = allocations.stream()
                .map(allocation -> SettlementResDTO.PreviewItem.builder()
                        .groupMemberId(allocation.groupMemberId())
                        .nickname(targets.get(allocation.groupMemberId()).getMember().getNickname())
                        .amount(allocation.amount())
                        .remainderAmount(allocation.remainderAmount())
                        .build())
                .toList();
        return SettlementResDTO.Preview.builder()
                .splitMode(splitMode)
                .totalAmount(totalAmount)
                .targetCount(items.size())
                .items(items)
                .build();
    }
}
