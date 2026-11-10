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
import com.moeasy.moeasybe.global.apiPayload.code.BaseErrorCode;
import com.moeasy.moeasybe.global.apiPayload.code.GeneralErrorCode;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

@Slf4j
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
        String stage = "PREPARE";
        try {
            String apiKey =
                    cipher.decrypt(
                            context.encryptedApiKey(), context.credentialCryptoContext() + ":api");
            limits.acquire(cipher.fingerprint("api:" + apiKey));
            stage = "FETCH";
            var rows =
                    client.fetch(
                            context.bankCode(),
                            cipher.decrypt(
                                    context.encryptedNumber(),
                                    context.accountCryptoContext() + ":account"),
                            apiKey,
                            cipher.decrypt(
                                    context.encryptedSecretKey(),
                                    context.credentialCryptoContext() + ":secret"),
                            request);
            stage = "PERSIST";
            return store.complete(group, member, context, rows);
        } catch (RuntimeException ex) {
            BaseErrorCode error =
                    ex instanceof GeneralException general
                            ? general.getCode()
                            : GeneralErrorCode.INTERNAL_SERVER_ERROR;
            if (!(ex instanceof GeneralException)) logFailure(group, stage, ex);
            long wait =
                    ex instanceof BankApiClient.UpstreamException upstream
                            ? upstream.retrySeconds()
                            : error == BankErrorCode.RATE_LIMITED
                                    ? 1800
                                    : error instanceof BankErrorCode ? 300 : 0;
            try {
                // claim에서 설정한 5분 제한은 유지한다. 은행 호출은 이미 소비됐을 수 있다.
                store.fail(group, context, error, wait);
            } catch (RuntimeException cleanup) {
                logFailure(group, "CLEANUP", cleanup);
            }
            if (ex instanceof GeneralException general) {
                throw general;
            }
            throw new GeneralException(GeneralErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private void logFailure(Long group, String stage, RuntimeException failure) {
        // 예외 메시지·cause의 메시지는 SQL 값이나 은행 인증정보를 포함할 수 있다.
        Throwable cause = failure;
        for (int depth = 0; cause != null && depth < 8; depth++, cause = cause.getCause()) {
            log.error(
                    "은행 동기화 오류. group={}, stage={}, causeDepth={}, type={}, frames={}",
                    group,
                    stage,
                    depth,
                    cause.getClass().getName(),
                    Arrays.toString(cause.getStackTrace()));
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
