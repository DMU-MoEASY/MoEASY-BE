package com.moeasy.moeasybe.domain.bank.entity;

import com.moeasy.moeasybe.domain.bank.enums.TransactionType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "account_transaction")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_bank_account_id", nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 32)
    private TransactionType type;

    @Column(name = "transacted_at", nullable = false)
    private LocalDateTime transactedAt;

    @Column(nullable = false, length = 100)
    private String description;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(length = 100)
    private String counterparty;

    @Column(nullable = false, columnDefinition = "BIGINT UNSIGNED")
    private Long amount;

    @Column(nullable = false)
    private Long balance;

    @Column(length = 100)
    private String branch;

    @Column(length = 255)
    private String memo;

    @Column(name = "source_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String sourceHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private AccountTransaction(
            Long accountId,
            TransactionType type,
            LocalDateTime transactedAt,
            String description,
            String displayName,
            String counterparty,
            Long amount,
            Long balance,
            String branch,
            String memo,
            String sourceHash,
            LocalDateTime createdAt) {
        this.accountId = accountId;
        this.type = type;
        this.transactedAt = transactedAt;
        this.description = description;
        this.displayName = displayName;
        this.counterparty = counterparty;
        this.amount = amount;
        this.balance = balance;
        this.branch = branch;
        this.memo = memo;
        this.sourceHash = sourceHash;
        this.createdAt = createdAt;
    }
}
