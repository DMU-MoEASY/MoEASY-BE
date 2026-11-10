package com.moeasy.moeasybe.domain.bank.service.query;

import com.moeasy.moeasybe.domain.bank.converter.BankConverter;
import com.moeasy.moeasybe.domain.bank.dto.request.BankReqDTO;
import com.moeasy.moeasybe.domain.bank.dto.response.BankResDTO;
import com.moeasy.moeasybe.domain.bank.entity.GroupBankAccount;
import com.moeasy.moeasybe.domain.bank.exception.BankException;
import com.moeasy.moeasybe.domain.bank.exception.code.BankErrorCode;
import com.moeasy.moeasybe.domain.bank.repository.AccountTransactionRepository;
import com.moeasy.moeasybe.domain.bank.repository.GroupBankAccountRepository;
import com.moeasy.moeasybe.domain.bank.service.support.BankAccess;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BankQueryService {
    private final BankAccess access;
    private final GroupBankAccountRepository accounts;
    private final AccountTransactionRepository transactions;

    @Transactional(readOnly = true)
    public BankResDTO.Account getAccount(Long group, Long member) {
        access.requireMember(group, member);
        return BankConverter.toAccount(account(group));
    }

    @Transactional(readOnly = true)
    public BankResDTO.Transactions getTransactions(
            Long group, Long member, BankReqDTO.Search request) {
        access.requireMember(group, member);
        if ((long) request.page() * request.size() > Integer.MAX_VALUE)
            throw new BankException(BankErrorCode.INVALID_QUERY);
        return BankConverter.toTransactions(
                transactions.findByAccountIdOrderByTransactedAtDescIdDesc(
                        account(group).getId(), PageRequest.of(request.page(), request.size())));
    }

    private GroupBankAccount account(Long group) {
        return accounts.findByGroupIdAndDeletedAtIsNull(group)
                .orElseThrow(() -> new BankException(BankErrorCode.ACCOUNT_NOT_FOUND));
    }
}
