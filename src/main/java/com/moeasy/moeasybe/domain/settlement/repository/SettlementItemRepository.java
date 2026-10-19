package com.moeasy.moeasybe.domain.settlement.repository;

import com.moeasy.moeasybe.domain.settlement.entity.SettlementItem;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SettlementItemRepository extends JpaRepository<SettlementItem, Long> {

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
