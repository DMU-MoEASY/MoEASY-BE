package com.moeasy.moeasybe.domain.bank.entity;

import com.moeasy.moeasybe.domain.bank.enums.BankCode;
import com.moeasy.moeasybe.global.entity.BaseEntity;

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
@Table(name = "bank_api_credential")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BankApiCredential extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "crypto_context", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String cryptoContext;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BankCode provider;

    @Column(name = "secret_ref", nullable = false, length = 500)
    private String secretRef;

    @Column(name = "encrypted_api_key", columnDefinition = "TEXT")
    private String encryptedApiKey;

    @Column(name = "encrypted_secret_key", columnDefinition = "TEXT")
    private String encryptedSecretKey;

    @Builder
    private BankApiCredential(
            String cryptoContext,
            Long memberId,
            BankCode provider,
            String encryptedApiKey,
            String encryptedSecretKey) {
        this.cryptoContext = cryptoContext;
        this.memberId = memberId;
        this.provider = provider;
        this.secretRef = "db:" + cryptoContext;
        this.encryptedApiKey = encryptedApiKey;
        this.encryptedSecretKey = encryptedSecretKey;
    }

    public void revoke(LocalDateTime now) {
        encryptedApiKey = null;
        encryptedSecretKey = null;
        markDeletedAt(now);
    }
}
