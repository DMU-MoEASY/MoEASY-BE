package com.moeasy.moeasybe.domain.settlement.repository;

import com.moeasy.moeasybe.domain.settlement.entity.SettlementPayment;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementPaymentStatus;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementPaymentType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SettlementPaymentRepository extends JpaRepository<SettlementPayment, Long> {

    @EntityGraph(attributePaths = {"recordedByGroupMember.member", "cancelledByGroupMember.member"})
    Page<SettlementPayment> findAllBySettlementItemId(Long settlementItemId, Pageable pageable);

    Optional<SettlementPayment> findByIdAndSettlementItemId(Long paymentId, Long settlementItemId);

    @Query("""
            select coalesce(sum(p.amount), 0) from SettlementPayment p
            where p.settlementItem.id = :itemId and p.paymentType = :paymentType and p.status = :status
            """)
    long sumAmountByItemAndTypeAndStatus(
            @Param("itemId") Long itemId,
            @Param("paymentType") SettlementPaymentType paymentType,
            @Param("status") SettlementPaymentStatus status);
}
