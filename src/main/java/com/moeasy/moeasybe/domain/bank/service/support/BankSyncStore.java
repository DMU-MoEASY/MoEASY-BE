package com.moeasy.moeasybe.domain.bank.service.support;

import com.moeasy.moeasybe.domain.bank.converter.BankConverter;
import com.moeasy.moeasybe.domain.bank.dto.request.BankReqDTO;
import com.moeasy.moeasybe.domain.bank.dto.response.BankResDTO;
import com.moeasy.moeasybe.domain.bank.entity.AccountTransaction;
import com.moeasy.moeasybe.domain.bank.entity.GroupBankAccount;
import com.moeasy.moeasybe.domain.bank.enums.BankCode;
import com.moeasy.moeasybe.domain.bank.exception.BankException;
import com.moeasy.moeasybe.domain.bank.exception.code.BankErrorCode;
import com.moeasy.moeasybe.domain.bank.infra.BankApiClient;
import com.moeasy.moeasybe.domain.bank.repository.AccountTransactionRepository;
import com.moeasy.moeasybe.domain.bank.repository.GroupBankAccountRepository;
import com.moeasy.moeasybe.domain.bank.service.result.BankSyncContext;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class BankSyncStore {
    private final BankAccess access;
    private final GroupBankAccountRepository accounts;
    private final AccountTransactionRepository transactions;
    private final Clock clock;

    @Transactional
    public BankSyncContext claim(Long group, Long member, BankReqDTO.Sync request) {
        access.requireManager(group, member);
        GroupBankAccount account = locked(group);
        if (account.getCredential() == null
                || account.getCredential().getDeletedAt() != null
                || account.getEncryptedAccountNumber() == null)
            throw new BankException(BankErrorCode.UNAVAILABLE);
        validateCredentials(account.getBankCode(), request);
        String token = UUID.randomUUID().toString();
        account.claimSync(token, LocalDateTime.now(clock));
        return BankConverter.toSyncContext(account, token);
    }

    @Transactional
    public BankResDTO.Synced complete(
            Long group,
            Long member,
            BankSyncContext context,
            List<BankApiClient.Transaction> rows) {
        access.requireManager(group, member);
        GroupBankAccount account = locked(group);
        Map<String, Integer> occurrences = new HashMap<>();
        Map<String, BankApiClient.Transaction> hashed = new LinkedHashMap<>();
        // The provider supplies no transaction ID. Keep repeated identical financial rows as a
        // multiset.
        for (BankApiClient.Transaction row : rows) {
            String identity =
                    row.at() + "|" + row.type() + "|" + row.amount() + "|" + row.balance();
            int occurrence = occurrences.merge(identity, 1, Integer::sum);
            hashed.put(hash(identity + "|" + occurrence), row);
        }
        Set<String> existing = new HashSet<>();
        List<String> hashes = new ArrayList<>(hashed.keySet());
        for (int i = 0; i < hashes.size(); i += 500)
            existing.addAll(
                    transactions.findExistingHashes(
                            account.getId(), hashes.subList(i, Math.min(i + 500, hashes.size()))));
        LocalDateTime now = LocalDateTime.now(clock);
        List<AccountTransaction> added =
                hashed.entrySet().stream()
                        .filter(e -> !existing.contains(e.getKey()))
                        .map(
                                e ->
                                        BankConverter.toTransaction(
                                                account.getId(), e.getValue(), e.getKey(), now))
                        .toList();
        transactions.saveAll(added);
        account.finishSync(context.token(), now, null, 0);
        return BankConverter.toSynced(added.size(), now);
    }

    @Transactional
    public void fail(Long group, BankSyncContext context, BankErrorCode error, long retry) {
        GroupBankAccount account = locked(group);
        account.finishSync(context.token(), LocalDateTime.now(clock), error.name(), retry);
    }

    private GroupBankAccount locked(Long group) {
        return accounts.findActiveForUpdate(group)
                .orElseThrow(() -> new BankException(BankErrorCode.ACCOUNT_NOT_FOUND));
    }

    private String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new BankException(BankErrorCode.UNAVAILABLE);
        }
    }

    private void validateCredentials(BankCode bank, BankReqDTO.Sync request) {
        if (bank == BankCode.SH) {
            if (request.loginId() == null
                    || request.loginId().isBlank()
                    || request.loginPassword() == null
                    || request.loginPassword().isBlank())
                throw new BankException(BankErrorCode.INVALID_QUERY);
        } else if (request.residentNumber() == null
                || request.accountType() == null
                || (request.accountType().equals("personal")
                        ? request.residentNumber().length() != 6
                        : request.residentNumber().length() != 10))
            throw new BankException(BankErrorCode.INVALID_QUERY);
    }
}
