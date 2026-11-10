package com.moeasy.moeasybe.domain.bank.repository;

import com.moeasy.moeasybe.domain.bank.entity.GroupBankAccount;
import com.moeasy.moeasybe.domain.bank.enums.BankCode;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GroupBankAccountRepository extends JpaRepository<GroupBankAccount, Long> {
    Optional<GroupBankAccount> findByGroupIdAndDeletedAtIsNull(Long groupId);

    Optional<GroupBankAccount> findByBankCodeAndAccountNumberFingerprint(
            BankCode bankCode, String fingerprint);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from GroupBankAccount a where a.groupId=:groupId and a.deletedAt is null")
    Optional<GroupBankAccount> findActiveForUpdate(@Param("groupId") Long groupId);
}
