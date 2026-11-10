package com.moeasy.moeasybe.domain.bank.service.result;

import com.moeasy.moeasybe.domain.bank.enums.BankCode;

import lombok.Builder;

@Builder
public record BankSyncContext(
        Long accountId,
        String accountCryptoContext,
        String credentialCryptoContext,
        BankCode bankCode,
        String encryptedNumber,
        String encryptedApiKey,
        String encryptedSecretKey,
        String token) {
    @Override
    public String toString() {
        return "BankSyncContext[REDACTED]";
    }
}
