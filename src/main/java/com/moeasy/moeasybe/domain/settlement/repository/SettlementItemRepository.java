package com.moeasy.moeasybe.domain.settlement.repository;

import com.moeasy.moeasybe.domain.settlement.entity.SettlementItem;
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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from SettlementItem i where i.id = :itemId and i.settlement.id = :settlementId")
    Optional<SettlementItem> findByIdAndSettlementIdForUpdate(
            @Param("itemId") Long itemId, @Param("settlementId") Long settlementId);

    @EntityGraph(attributePaths = "groupMember.member")
    List<SettlementItem> findAllBySettlementIdOrderByGroupMemberIdAsc(Long settlementId);

    @Query("""
            select i.settlement.id as settlementId, count(i) as targetCount
            from SettlementItem i
            where i.settlement.id in :settlementIds
            group by i.settlement.id
            """)
    List<TargetCount> countTargetsBySettlementIds(@Param("settlementIds") Collection<Long> settlementIds);

    interface TargetCount {
        Long getSettlementId();

        long getTargetCount();
    }
}
