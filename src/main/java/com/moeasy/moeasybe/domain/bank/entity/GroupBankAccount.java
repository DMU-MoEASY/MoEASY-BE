package com.moeasy.moeasybe.domain.bank.entity;

import com.moeasy.moeasybe.domain.bank.enums.BankCode;
import com.moeasy.moeasybe.domain.bank.exception.BankException;
import com.moeasy.moeasybe.domain.bank.exception.code.BankErrorCode;
import com.moeasy.moeasybe.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "group_bank_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupBankAccount extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "crypto_context", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String cryptoContext;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_api_credential_id")
    private BankApiCredential credential;

    @Enumerated(EnumType.STRING)
    @Column(name = "bank_code", nullable = false, length = 32)
    private BankCode bankCode;

    @Column(name = "account_number_secret_ref", nullable = false, length = 500)
    private String accountNumberSecretRef;

    @Column(
            name = "account_number_fingerprint",
            nullable = false,
            length = 64,
            columnDefinition = "CHAR(64)")
    private String accountNumberFingerprint;

    @Column(name = "account_number_masked", nullable = false, length = 50)
    private String accountNumberMasked;

    @Column(name = "account_holder", nullable = false, length = 50)
    private String accountHolder;

    @Column(name = "encrypted_account_number", columnDefinition = "TEXT")
    private String encryptedAccountNumber;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "next_sync_at")
    private LocalDateTime nextSyncAt;

    @Column(name = "sync_token", length = 36)
    private String syncToken;

    @Column(name = "sync_lease_until")
    private LocalDateTime syncLeaseUntil;

    @Column(name = "last_sync_error", length = 32)
    private String lastSyncError;

    @Builder
    private GroupBankAccount(
            String cryptoContext,
            Long groupId,
            BankApiCredential credential,
            BankCode bankCode,
            String accountNumberFingerprint,
            String accountNumberMasked,
            String accountHolder,
            String encryptedAccountNumber) {
        this.cryptoContext = cryptoContext;
        this.groupId = groupId;
        this.credential = credential;
        this.bankCode = bankCode;
        accountNumberSecretRef = "db:" + cryptoContext;
        this.accountNumberFingerprint = accountNumberFingerprint;
        this.accountNumberMasked = accountNumberMasked;
        this.accountHolder = accountHolder;
        this.encryptedAccountNumber = encryptedAccountNumber;
    }

    public void reconnect(
            BankApiCredential credential,
            String encryptedNumber,
            String holder,
            LocalDateTime now) {
        requireIdle(now);
        this.credential = credential;
        encryptedAccountNumber = encryptedNumber;
        accountHolder = holder;
        markDeletedAt(null);
    }

    public void disconnect(LocalDateTime now) {
        requireIdle(now);
        encryptedAccountNumber = null;
        if (credential != null) {
            credential.revoke(now);
        }
        markDeletedAt(now);
    }

    public void claimSync(String token, LocalDateTime now) {
        requireIdle(now);
        if (nextSyncAt != null && now.isBefore(nextSyncAt))
            throw new BankException(BankErrorCode.RATE_LIMITED);
        syncToken = token;
        syncLeaseUntil = now.plusMinutes(2);
        nextSyncAt = now.plusMinutes(5);
    }

    public void finishSync(String token, LocalDateTime now, String error, long retrySeconds) {
        if (!token.equals(syncToken)) throw new BankException(BankErrorCode.SYNC_IN_PROGRESS);
        if (error == null) lastSyncedAt = now;
        lastSyncError = error;
        LocalDateTime retry = now.plusSeconds(retrySeconds);
        if (nextSyncAt == null || retry.isAfter(nextSyncAt)) nextSyncAt = retry;
        syncToken = null;
        syncLeaseUntil = null;
    }

    private void requireIdle(LocalDateTime now) {
        if (syncToken != null && syncLeaseUntil != null && now.isBefore(syncLeaseUntil))
            throw new BankException(BankErrorCode.SYNC_IN_PROGRESS);
    }
}
