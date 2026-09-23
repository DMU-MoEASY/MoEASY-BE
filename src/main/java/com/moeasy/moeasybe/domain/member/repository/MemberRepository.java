package com.moeasy.moeasybe.domain.member.repository;

import com.moeasy.moeasybe.domain.member.entity.Member;
import com.moeasy.moeasybe.domain.member.entity.SocialType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findBySocialTypeAndSocialId(SocialType socialType, String socialId);

    @Modifying
    @Query(
            value = "INSERT IGNORE INTO member (social_type, social_id) "
                    + "VALUES (:socialType, :socialId)",
            nativeQuery = true
    )
    int insertSocialMemberIfAbsent(
            @Param("socialType") String socialType,
            @Param("socialId") String socialId
    );
}
