package com.moeasy.moeasybe.domain.settlement.repository;

import com.moeasy.moeasybe.domain.settlement.entity.SettlementPayment;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementPaymentStatus;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementPaymentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SettlementPaymentRepository extends JpaRepository<SettlementPayment, Long> {

    @Query("""
            select coalesce(sum(p.amount), 0) from SettlementPayment p
            where p.settlementItem.id = :itemId and p.paymentType = :paymentType and p.status = :status
            """)
    long sumAmountByItemAndTypeAndStatus(
            @Param("itemId") Long itemId,
            @Param("paymentType") SettlementPaymentType paymentType,
            @Param("status") SettlementPaymentStatus status);
}
