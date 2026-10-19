package com.moeasy.moeasybe.domain.settlement.util;

import com.moeasy.moeasybe.domain.settlement.dto.request.SettlementReqDTO;
import com.moeasy.moeasybe.domain.settlement.exception.SettlementException;
import com.moeasy.moeasybe.domain.settlement.exception.code.SettlementErrorCode;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

@Component
public class SettlementSplitCalculator {

    public List<Allocation> calculateEqual(Long totalAmount, List<Long> groupMemberIds) {
        validateAmount(totalAmount);
        if (groupMemberIds == null || groupMemberIds.isEmpty()) {
            throw new SettlementException(SettlementErrorCode.INVALID_TARGET_MEMBERS);
        }
        Set<Long> seen = new HashSet<>();
        for (Long groupMemberId : groupMemberIds) {
            validateTarget(groupMemberId, seen);
        }
        if (totalAmount < groupMemberIds.size()) {
            throw new SettlementException(SettlementErrorCode.INVALID_AMOUNT);
        }

        List<Long> sortedIds = groupMemberIds.stream().sorted().toList();
        long baseAmount = totalAmount / sortedIds.size();
        long remainder = totalAmount % sortedIds.size();
        return IntStream.range(0, sortedIds.size())
                .mapToObj(index -> {
                    long remainderAmount = index < remainder ? 1L : 0L;
                    return new Allocation(
                            sortedIds.get(index), baseAmount + remainderAmount, remainderAmount);
                })
                .toList();
    }

    public List<Allocation> calculateCustom(
            Long totalAmount, List<SettlementReqDTO.MemberAmount> memberAmounts) {
        validateAmount(totalAmount);
        if (memberAmounts == null || memberAmounts.isEmpty()) {
            throw new SettlementException(SettlementErrorCode.INVALID_TARGET_MEMBERS);
        }

        Set<Long> seen = new HashSet<>();
        long sum = 0L;
        for (SettlementReqDTO.MemberAmount memberAmount : memberAmounts) {
            if (memberAmount == null) {
                throw new SettlementException(SettlementErrorCode.INVALID_TARGET_MEMBERS);
            }
            validateTarget(memberAmount.groupMemberId(), seen);
            validateAmount(memberAmount.amount());
            try {
                sum = Math.addExact(sum, memberAmount.amount());
            } catch (ArithmeticException exception) {
                throw new SettlementException(SettlementErrorCode.INVALID_AMOUNT);
            }
        }
        if (sum != totalAmount) {
            throw new SettlementException(SettlementErrorCode.INVALID_AMOUNT);
        }

        return memberAmounts.stream()
                .sorted(Comparator.comparing(SettlementReqDTO.MemberAmount::groupMemberId))
                .map(memberAmount -> new Allocation(
                        memberAmount.groupMemberId(), memberAmount.amount(), 0L))
                .toList();
    }

    private void validateTarget(Long groupMemberId, Set<Long> seen) {
        if (groupMemberId == null || groupMemberId <= 0 || !seen.add(groupMemberId)) {
            throw new SettlementException(SettlementErrorCode.INVALID_TARGET_MEMBERS);
        }
    }

    private void validateAmount(Long amount) {
        if (amount == null || amount <= 0) {
            throw new SettlementException(SettlementErrorCode.INVALID_AMOUNT);
        }
    }

    public record Allocation(
            Long groupMemberId,
            long amount,
            long remainderAmount
    ) {
    }
}
