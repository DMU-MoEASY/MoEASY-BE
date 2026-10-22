package com.moeasy.moeasybe.domain.settlement.repository;

import com.moeasy.moeasybe.domain.settlement.entity.Settlement;
import com.moeasy.moeasybe.domain.settlement.enums.SettlementStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    @EntityGraph(attributePaths = "createdByGroupMember.member")
    Optional<Settlement> findByIdAndGroupIdAndDeletedAtIsNull(Long id, Long groupId);

    @Query(value = """
            select s from Settlement s
            where s.group.id = :groupId and s.deletedAt is null
              and (:status is null or s.status = :status)
              and (:scheduleId is null or s.scheduleId = :scheduleId)
            """, countQuery = """
            select count(s) from Settlement s
            where s.group.id = :groupId and s.deletedAt is null
              and (:status is null or s.status = :status)
              and (:scheduleId is null or s.scheduleId = :scheduleId)
            """)
    Page<Settlement> findPageByGroup(
            @Param("groupId") Long groupId,
            @Param("status") SettlementStatus status,
            @Param("scheduleId") Long scheduleId,
            Pageable pageable);
}
