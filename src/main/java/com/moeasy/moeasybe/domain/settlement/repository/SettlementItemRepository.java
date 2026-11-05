package com.moeasy.moeasybe.domain.settlement.repository;

import com.moeasy.moeasybe.domain.settlement.entity.SettlementItem;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementItemStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SettlementItemRepository extends JpaRepository<SettlementItem, Long> {

    boolean existsByIdAndSettlementId(Long itemId, Long settlementId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from SettlementItem i where i.id = :itemId and i.settlement.id = :settlementId")
    Optional<SettlementItem> findByIdAndSettlementIdForUpdate(
            @Param("itemId") Long itemId, @Param("settlementId") Long settlementId);

    @EntityGraph(attributePaths = "groupMember.member")
    List<SettlementItem> findAllBySettlementIdOrderByGroupMemberIdAsc(Long settlementId);

    @Query("""
            select i.settlement.id as settlementId, count(i) as targetCount,
                   sum(i.amount) as chargedAmount,
                   sum(case when i.status = :paidStatus then 1 else 0 end) as paidCount,
                   sum(case when i.status = :pendingStatus then 1 else 0 end) as pendingCount
            from SettlementItem i
            where i.settlement.id in :settlementIds
            group by i.settlement.id
            """)
    List<Summary> summarizeBySettlementIds(
            @Param("settlementIds") Collection<Long> settlementIds,
            @Param("paidStatus") SettlementItemStatus paidStatus,
            @Param("pendingStatus") SettlementItemStatus pendingStatus);

    interface Summary {
        Long getSettlementId();

        long getTargetCount();

        long getChargedAmount();

        long getPaidCount();

        long getPendingCount();
    }
}
