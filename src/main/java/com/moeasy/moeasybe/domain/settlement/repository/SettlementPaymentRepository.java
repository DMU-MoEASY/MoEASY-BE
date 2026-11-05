package com.moeasy.moeasybe.domain.settlement.repository;

import com.moeasy.moeasybe.domain.settlement.entity.SettlementPayment;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementPaymentStatus;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementPaymentType;
import java.util.Collection;
import java.util.List;
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
            select p.settlementItem.settlement.id as settlementId, sum(p.amount) as paidAmount
            from SettlementPayment p
            where p.settlementItem.settlement.id in :settlementIds
              and p.paymentType = :paymentType and p.status = :status
            group by p.settlementItem.settlement.id
            """)
    List<SettlementPaidAmount> sumAmountsBySettlementIdsAndTypeAndStatus(
            @Param("settlementIds") Collection<Long> settlementIds,
            @Param("paymentType") SettlementPaymentType paymentType,
            @Param("status") SettlementPaymentStatus status);

    @Query("""
            select p.settlementItem.id as itemId, sum(p.amount) as paidAmount
            from SettlementPayment p
            where p.settlementItem.settlement.id = :settlementId
              and p.paymentType = :paymentType and p.status = :status
            group by p.settlementItem.id
            """)
    List<ItemPaidAmount> sumAmountsBySettlementIdAndTypeAndStatus(
            @Param("settlementId") Long settlementId,
            @Param("paymentType") SettlementPaymentType paymentType,
            @Param("status") SettlementPaymentStatus status);

    @Query("""
            select coalesce(sum(p.amount), 0) from SettlementPayment p
            where p.settlementItem.id = :itemId and p.paymentType = :paymentType and p.status = :status
            """)
    long sumAmountByItemAndTypeAndStatus(
            @Param("itemId") Long itemId,
            @Param("paymentType") SettlementPaymentType paymentType,
            @Param("status") SettlementPaymentStatus status);

    interface SettlementPaidAmount {
        Long getSettlementId();

        long getPaidAmount();
    }

    interface ItemPaidAmount {
        Long getItemId();

        long getPaidAmount();
    }
}
