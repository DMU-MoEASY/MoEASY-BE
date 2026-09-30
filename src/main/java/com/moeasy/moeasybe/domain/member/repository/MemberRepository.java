package com.moeasy.moeasybe.domain.member.repository;

import com.moeasy.moeasybe.domain.member.entity.Member;
import com.moeasy.moeasybe.domain.member.enums.SocialType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByNickname(String nickname);

    Optional<Member> findBySocialTypeAndSocialId(SocialType socialType, String socialId);
}
