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
import com.moeasy.moeasybe.domain.settlement.exception.SettlementException;
import com.moeasy.moeasybe.domain.settlement.exception.code.SettlementErrorCode;
import com.moeasy.moeasybe.domain.settlement.repository.SettlementItemRepository;
import com.moeasy.moeasybe.domain.settlement.repository.SettlementRepository;
import com.moeasy.moeasybe.domain.settlement.util.SettlementSplitCalculator;
import com.moeasy.moeasybe.domain.settlement.util.SettlementSplitCalculator.Allocation;
import com.moeasy.moeasybe.global.apiPayload.code.GeneralErrorCode;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
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
    private final SettlementSplitCalculator splitCalculator;
    private final Clock clock;

    @Transactional
    public SettlementResDTO.Created createSettlement(
            Long groupId, SettlementReqDTO.Create request, Long memberId) {
        if (groupId == null || request == null || memberId == null) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST);
        }
        Group group = groupRepository.findByIdAndStatusNotAndDeletedAtIsNull(groupId, GroupStatus.CLOSED)
                .orElseThrow(() -> new GroupException(GroupErrorCode.GROUP_NOT_FOUND));
        GroupMember creator = requireCreator(groupId, memberId);
        validateSchedule(groupId, request.scheduleId());
        List<Allocation> allocations = calculateAllocations(request);
        Map<Long, GroupMember> targets = findTargets(groupId, allocations);
        validateDueAt(request.dueAt());

        Settlement settlement = settlementRepository.save(SettlementConverter.toEntity(request, group, creator));
        settlementItemRepository.saveAll(SettlementConverter.toItems(settlement, allocations, targets));
        return SettlementConverter.toCreated(settlement);
    }

    private GroupMember requireCreator(Long groupId, Long memberId) {
        GroupMember creator = groupMemberRepository
                .findByGroupIdAndMemberIdAndStatusAndDeletedAtIsNullAndMemberDeletedAtIsNull(
                        groupId, memberId, GroupMemberStatus.ACTIVE)
                .orElseThrow(() -> new GeneralException(GeneralErrorCode.FORBIDDEN));
        if (creator.getRole() != GroupMemberRole.OWNER && creator.getRole() != GroupMemberRole.MANAGER) {
            throw new GeneralException(GeneralErrorCode.FORBIDDEN);
        }
        return creator;
    }

    private void validateSchedule(Long groupId, Long scheduleId) {
        if (scheduleId != null && !scheduleRepository
                .existsByIdAndGroupIdAndDeletedAtIsNull(scheduleId, groupId)) {
            throw new SettlementException(SettlementErrorCode.INVALID_SCHEDULE);
        }
    }

    private List<Allocation> calculateAllocations(SettlementReqDTO.Create request) {
        if (request.splitMode() == null) {
            throw new SettlementException(SettlementErrorCode.INVALID_SPLIT_MODE_FIELDS);
        }
        return switch (request.splitMode()) {
            case EQUAL -> {
                if (request.groupMemberIds() == null || request.memberAmounts() != null) {
                    throw new SettlementException(SettlementErrorCode.INVALID_SPLIT_MODE_FIELDS);
                }
                yield splitCalculator.calculateEqual(request.totalAmount(), request.groupMemberIds());
            }
            case CUSTOM -> {
                if (request.memberAmounts() == null || request.groupMemberIds() != null) {
                    throw new SettlementException(SettlementErrorCode.INVALID_SPLIT_MODE_FIELDS);
                }
                yield splitCalculator.calculateCustom(request.totalAmount(), request.memberAmounts());
            }
        };
    }

    private Map<Long, GroupMember> findTargets(Long groupId, List<Allocation> allocations) {
        List<Long> targetIds = allocations.stream().map(Allocation::groupMemberId).toList();
        List<GroupMember> targets = groupMemberRepository
                .findAllByGroupIdAndIdInAndStatusAndDeletedAtIsNullAndMemberDeletedAtIsNull(
                        groupId, targetIds, GroupMemberStatus.ACTIVE);
        if (targets.size() != allocations.size()) {
            throw new SettlementException(SettlementErrorCode.INVALID_TARGET_MEMBERS);
        }
        return targets.stream().collect(Collectors.toMap(GroupMember::getId, Function.identity()));
    }

    private void validateDueAt(LocalDateTime dueAt) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), SEOUL);
        if (dueAt != null && !dueAt.isAfter(now)) {
            throw new SettlementException(SettlementErrorCode.INVALID_DUE_AT);
        }
    }
}
