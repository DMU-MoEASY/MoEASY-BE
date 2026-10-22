package com.moeasy.moeasybe.domain.settlement.service.support;

import com.moeasy.moeasybe.domain.group.entity.GroupMember;
import com.moeasy.moeasybe.domain.group.enums.GroupMemberStatus;
import com.moeasy.moeasybe.domain.group.repository.GroupMemberRepository;
import com.moeasy.moeasybe.domain.settlement.exception.SettlementException;
import com.moeasy.moeasybe.domain.settlement.exception.code.SettlementErrorCode;
import com.moeasy.moeasybe.domain.settlement.util.SettlementSplitCalculator.Allocation;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SettlementTargetFinder {

    private final GroupMemberRepository groupMemberRepository;

    public Map<Long, GroupMember> findTargets(Long groupId, List<Allocation> allocations) {
        List<Long> targetIds = allocations.stream().map(Allocation::groupMemberId).toList();
        List<GroupMember> targets = groupMemberRepository
                .findAllByGroupIdAndIdInAndStatusAndDeletedAtIsNullAndMemberDeletedAtIsNull(
                        groupId, targetIds, GroupMemberStatus.ACTIVE);
        if (targets.size() != allocations.size()) {
            throw new SettlementException(SettlementErrorCode.INVALID_TARGET_MEMBERS);
        }
        return targets.stream().collect(Collectors.toMap(GroupMember::getId, Function.identity()));
    }
}
