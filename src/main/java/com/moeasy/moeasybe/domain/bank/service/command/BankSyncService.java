package com.moeasy.moeasybe.domain.bank.service.command;

import com.moeasy.moeasybe.domain.bank.dto.request.BankReqDTO;
import com.moeasy.moeasybe.domain.bank.dto.response.BankResDTO;
import com.moeasy.moeasybe.domain.bank.exception.BankException;
import com.moeasy.moeasybe.domain.bank.exception.code.BankErrorCode;
import com.moeasy.moeasybe.domain.bank.infra.BankApiClient;
import com.moeasy.moeasybe.domain.bank.infra.BankSecretCipher;
import com.moeasy.moeasybe.domain.bank.infra.BankSyncRateLimiter;
import com.moeasy.moeasybe.domain.bank.service.result.BankSyncContext;
import com.moeasy.moeasybe.domain.bank.service.support.BankSyncStore;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class BankSyncService {
    private final BankSyncStore store;
    private final BankApiClient client;
    private final BankSecretCipher cipher;
    private final BankSyncRateLimiter limits;
    private final Clock clock;

    // Separate short transactions before/after HTTP: no connection or lock is held for the bank
    // call.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public BankResDTO.Synced sync(Long group, Long member, BankReqDTO.Sync request) {
        validateDates(request);
        BankSyncContext context = store.claim(group, member, request);
        try {
            String apiKey =
                    cipher.decrypt(
                            context.encryptedApiKey(), context.credentialCryptoContext() + ":api");
            limits.acquire(cipher.fingerprint("api:" + apiKey));
            return store.complete(
                    group,
                    member,
                    context,
                    client.fetch(
                            context.bankCode(),
                            cipher.decrypt(
                                    context.encryptedNumber(),
                                    context.accountCryptoContext() + ":account"),
                            apiKey,
                            cipher.decrypt(
                                    context.encryptedSecretKey(),
                                    context.credentialCryptoContext() + ":secret"),
                            request));
        } catch (RuntimeException ex) {
            BankErrorCode error =
                    ex instanceof BankException bank
                            ? (BankErrorCode) bank.getCode()
                            : BankErrorCode.UPSTREAM_FAILED;
            long wait =
                    ex instanceof BankApiClient.UpstreamException upstream
                            ? upstream.retrySeconds()
                            : error == BankErrorCode.RATE_LIMITED ? 1800 : 300;
            store.fail(group, context, error, wait);
            if (ex instanceof GeneralException general) {
                throw general;
            }
            throw new BankException(BankErrorCode.UPSTREAM_FAILED);
        }
    }

    private void validateDates(BankReqDTO.Sync request) {
        LocalDate start = request.startDate(), end = request.endDate();
        if (start == null
                || end == null
                || start.getYear() < 1000
                || end.getYear() > 9999
                || end.isBefore(start)
                || end.isAfter(LocalDate.now(clock))
                || ChronoUnit.DAYS.between(start, end) > 30)
            throw new BankException(BankErrorCode.INVALID_QUERY);
    }
}
