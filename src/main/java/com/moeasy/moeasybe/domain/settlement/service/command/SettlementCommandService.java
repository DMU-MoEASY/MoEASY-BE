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
    private final SettlementTargetFinder targetFinder;
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
        List<Allocation> allocations = splitCalculator.calculate(
                request.splitMode(), request.totalAmount(), request.groupMemberIds(), request.memberAmounts());
        Map<Long, GroupMember> targets = targetFinder.findTargets(groupId, allocations);
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

    private void validateDueAt(LocalDateTime dueAt) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), SEOUL);
        if (dueAt != null && !dueAt.isAfter(now)) {
            throw new SettlementException(SettlementErrorCode.INVALID_DUE_AT);
        }
    }
}
