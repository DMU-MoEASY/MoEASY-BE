package com.moeasy.moeasybe.domain.member.repository;

import com.moeasy.moeasybe.domain.member.entity.MemberCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberCategoryRepository extends JpaRepository<MemberCategory, Long> {

    @Query("select mc.category.id from MemberCategory mc where mc.member.id = :memberId")
    java.util.List<Long> findCategoryIdsByMemberId(Long memberId);

    @Modifying
    @Query("delete from MemberCategory mc where mc.member.id = :memberId")
    void deleteAllByMemberId(@Param("memberId") Long memberId);
}
