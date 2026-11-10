package com.moeasy.moeasybe.domain.bank.repository;

import com.moeasy.moeasybe.domain.bank.entity.AccountTransaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AccountTransactionRepository extends JpaRepository<AccountTransaction, Long> {
    Page<AccountTransaction> findByAccountIdOrderByTransactedAtDescIdDesc(
            Long accountId, Pageable page);

    @Query(
            "select t.sourceHash from AccountTransaction t where t.accountId=:accountId and"
                    + " t.sourceHash in :hashes")
    List<String> findExistingHashes(
            @Param("accountId") Long accountId, @Param("hashes") Collection<String> hashes);
}
