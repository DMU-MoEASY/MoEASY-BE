package com.moeasy.moeasybe.domain.settlement.converter;

import com.moeasy.moeasybe.domain.group.entity.Group;
import com.moeasy.moeasybe.domain.group.entity.GroupMember;
import com.moeasy.moeasybe.domain.settlement.dto.request.SettlementReqDTO;
import com.moeasy.moeasybe.domain.settlement.dto.response.SettlementResDTO;
import com.moeasy.moeasybe.domain.settlement.entity.Settlement;
import com.moeasy.moeasybe.domain.settlement.entity.SettlementItem;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementSplitMode;
import com.moeasy.moeasybe.domain.settlement.util.SettlementSplitCalculator.Allocation;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;

public final class SettlementConverter {

    private SettlementConverter() {
    }

    public static SettlementResDTO.Page toPage(Page<Settlement> settlements, Map<Long, Integer> targetCounts) {
        List<SettlementResDTO.Summary> items = settlements.getContent().stream()
                .map(settlement -> toSummary(settlement, targetCounts.getOrDefault(settlement.getId(), 0)))
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

    public static SettlementResDTO.Summary toSummary(Settlement settlement, int targetCount) {
        return SettlementResDTO.Summary.builder()
                .settlementId(settlement.getId())
                .title(settlement.getTitle())
                .scheduleId(settlement.getScheduleId())
                .status(settlement.getStatus())
                .splitMode(settlement.getSplitMode())
                .totalAmount(settlement.getTotalAmount())
                .targetCount(targetCount)
                .dueAt(settlement.getDueAt())
                .createdAt(settlement.getCreatedAt())
                .build();
    }

    public static Settlement toEntity(SettlementReqDTO.Create request, Group group, GroupMember creator) {
        return Settlement.builder()
                .group(group)
                .createdByGroupMember(creator)
                .scheduleId(request.scheduleId())
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
