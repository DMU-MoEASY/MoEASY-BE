package com.moeasy.moeasybe.domain.bank.converter;

import com.moeasy.moeasybe.domain.bank.dto.request.BankReqDTO;
import com.moeasy.moeasybe.domain.bank.dto.response.BankResDTO;
import com.moeasy.moeasybe.domain.bank.entity.AccountTransaction;
import com.moeasy.moeasybe.domain.bank.entity.BankApiCredential;
import com.moeasy.moeasybe.domain.bank.entity.GroupBankAccount;
import com.moeasy.moeasybe.domain.bank.infra.BankApiClient;
import com.moeasy.moeasybe.domain.bank.service.result.BankSyncContext;

import org.springframework.data.domain.Page;

import java.time.LocalDateTime;

public final class BankConverter {
    private BankConverter() {}

    public static BankResDTO.Account toAccount(GroupBankAccount account) {
        return BankResDTO.Account.builder()
                .accountId(account.getId())
                .bankCode(account.getBankCode())
                .accountNumberMasked(account.getAccountNumberMasked())
                .accountHolder(account.getAccountHolder())
                .lastSyncedAt(account.getLastSyncedAt())
                .nextSyncAt(account.getNextSyncAt())
                .lastSyncError(account.getLastSyncError())
                .build();
    }

    public static BankApiCredential toCredential(
            BankReqDTO.Connect request, Long member, String id, String api, String secret) {
        return BankApiCredential.builder()
                .cryptoContext(id)
                .memberId(member)
                .provider(request.bankCode())
                .encryptedApiKey(api)
                .encryptedSecretKey(secret)
                .build();
    }

    public static GroupBankAccount toAccount(
            BankReqDTO.Connect request,
            Long group,
            String id,
            BankApiCredential credential,
            String fingerprint,
            String encrypted,
            String masked) {
        return GroupBankAccount.builder()
                .cryptoContext(id)
                .groupId(group)
                .credential(credential)
                .bankCode(request.bankCode())
                .accountNumberFingerprint(fingerprint)
                .accountNumberMasked(masked)
                .accountHolder(request.accountHolder())
                .encryptedAccountNumber(encrypted)
                .build();
    }

    public static BankSyncContext toSyncContext(GroupBankAccount account, String token) {
        BankApiCredential credential = account.getCredential();
        return BankSyncContext.builder()
                .accountId(account.getId())
                .accountCryptoContext(account.getCryptoContext())
                .credentialCryptoContext(credential.getCryptoContext())
                .bankCode(account.getBankCode())
                .encryptedNumber(account.getEncryptedAccountNumber())
                .encryptedApiKey(credential.getEncryptedApiKey())
                .encryptedSecretKey(credential.getEncryptedSecretKey())
                .token(token)
                .build();
    }

    public static AccountTransaction toTransaction(
            Long account, BankApiClient.Transaction data, String hash, LocalDateTime now) {
        return AccountTransaction.builder()
                .accountId(account)
                .type(data.type())
                .transactedAt(data.at())
                .description(data.description())
                .displayName(data.displayName())
                .counterparty(data.counterparty())
                .amount(data.amount())
                .balance(data.balance())
                .branch(data.branch())
                .memo(data.memo())
                .sourceHash(hash)
                .createdAt(now)
                .build();
    }

    public static BankResDTO.Synced toSynced(int inserted, LocalDateTime now) {
        return BankResDTO.Synced.builder().insertedCount(inserted).lastSyncedAt(now).build();
    }

    public static BankResDTO.Transactions toTransactions(Page<AccountTransaction> page) {
        return BankResDTO.Transactions.builder()
                .items(
                        page.stream()
                                .map(
                                        t ->
                                                BankResDTO.Transaction.builder()
                                                        .transactionId(t.getId())
                                                        .type(t.getType())
                                                        .transactedAt(t.getTransactedAt())
                                                        .description(t.getDescription())
                                                        .displayName(t.getDisplayName())
                                                        .counterparty(t.getCounterparty())
                                                        .amount(t.getAmount())
                                                        .balance(t.getBalance())
                                                        .branch(t.getBranch())
                                                        .memo(t.getMemo())
                                                        .build())
                                .toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .hasNext(page.hasNext())
                .build();
    }
}
