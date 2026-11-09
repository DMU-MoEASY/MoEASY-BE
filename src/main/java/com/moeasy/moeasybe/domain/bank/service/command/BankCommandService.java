package com.moeasy.moeasybe.domain.bank.service.command;

import com.moeasy.moeasybe.domain.bank.converter.BankConverter;
import com.moeasy.moeasybe.domain.bank.dto.request.BankReqDTO;
import com.moeasy.moeasybe.domain.bank.dto.response.BankResDTO;
import com.moeasy.moeasybe.domain.bank.entity.BankApiCredential;
import com.moeasy.moeasybe.domain.bank.entity.GroupBankAccount;
import com.moeasy.moeasybe.domain.bank.exception.BankException;
import com.moeasy.moeasybe.domain.bank.exception.code.BankErrorCode;
import com.moeasy.moeasybe.domain.bank.infra.BankSecretCipher;
import com.moeasy.moeasybe.domain.bank.repository.BankApiCredentialRepository;
import com.moeasy.moeasybe.domain.bank.repository.GroupBankAccountRepository;
import com.moeasy.moeasybe.domain.bank.service.support.BankAccess;
import com.moeasy.moeasybe.domain.group.exception.GroupException;
import com.moeasy.moeasybe.domain.group.exception.code.GroupErrorCode;
import com.moeasy.moeasybe.domain.group.repository.GroupRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BankCommandService {
    private final BankAccess access;
    private final GroupRepository groups;
    private final GroupBankAccountRepository accounts;
    private final BankApiCredentialRepository credentials;
    private final BankSecretCipher cipher;
    private final Clock clock;

    @Transactional
    public BankResDTO.Account connect(Long group, Long member, BankReqDTO.Connect request) {
        groups.findByIdForUpdate(group)
                .orElseThrow(() -> new GroupException(GroupErrorCode.GROUP_NOT_FOUND));
        access.requireManager(group, member);
        if (accounts.findByGroupIdAndDeletedAtIsNull(group).isPresent())
            throw new BankException(BankErrorCode.ACCOUNT_CONFLICT);
        String fingerprint =
                cipher.fingerprint("account:" + request.bankCode() + ":" + request.accountNumber());
        GroupBankAccount existing =
                accounts.findByBankCodeAndAccountNumberFingerprint(request.bankCode(), fingerprint)
                        .orElse(null);
        if (existing != null && !existing.getGroupId().equals(group))
            throw new BankException(BankErrorCode.ACCOUNT_CONFLICT);
        String credentialId = UUID.randomUUID().toString();
        BankApiCredential credential =
                credentials.save(
                        BankConverter.toCredential(
                                request,
                                member,
                                credentialId,
                                cipher.encrypt(request.apiKey(), credentialId + ":api"),
                                cipher.encrypt(request.secretKey(), credentialId + ":secret")));
        String accountId =
                existing == null ? UUID.randomUUID().toString() : existing.getCryptoContext();
        String encrypted = cipher.encrypt(request.accountNumber(), accountId + ":account");
        String masked =
                "*".repeat(request.accountNumber().length() - 4)
                        + request.accountNumber().substring(request.accountNumber().length() - 4);
        GroupBankAccount account = existing;
        if (account == null)
            account =
                    BankConverter.toAccount(
                            request, group, accountId, credential, fingerprint, encrypted, masked);
        else
            account.reconnect(
                    credential, encrypted, request.accountHolder(), LocalDateTime.now(clock));
        try {
            accounts.saveAndFlush(account);
        } catch (DataIntegrityViolationException ex) {
            throw new BankException(BankErrorCode.ACCOUNT_CONFLICT);
        }
        return BankConverter.toAccount(account);
    }

    @Transactional
    public void disconnect(Long group, Long member) {
        access.requireManager(group, member);
        GroupBankAccount account =
                accounts.findActiveForUpdate(group)
                        .orElseThrow(() -> new BankException(BankErrorCode.ACCOUNT_NOT_FOUND));
        account.disconnect(LocalDateTime.now(clock));
    }
}
