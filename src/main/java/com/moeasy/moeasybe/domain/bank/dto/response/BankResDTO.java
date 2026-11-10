package com.moeasy.moeasybe.domain.bank.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.moeasy.moeasybe.domain.bank.enums.BankCode;
import com.moeasy.moeasybe.domain.bank.enums.TransactionType;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

public final class BankResDTO {
    private BankResDTO() {}

    @Builder
    public record Account(
            Long accountId,
            BankCode bankCode,
            String accountNumberMasked,
            String accountHolder,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime lastSyncedAt,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime nextSyncAt,
            String lastSyncError) {}

    @Builder
    public record Synced(
            int insertedCount,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime lastSyncedAt) {}

    @Builder
    public record Transaction(
            Long transactionId,
            TransactionType type,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime transactedAt,
            String description,
            String displayName,
            String counterparty,
            Long amount,
            Long balance,
            String branch,
            String memo) {}

    @Builder
    public record Transactions(
            List<Transaction> items,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean hasNext) {}
}
