package com.moeasy.moeasybe.domain.settlement.service.result;

public record SettlementPaymentSummary(
        int targetCount,
        long paidAmount,
        long remainingAmount,
        int paidCount,
        int pendingCount
) {
}
