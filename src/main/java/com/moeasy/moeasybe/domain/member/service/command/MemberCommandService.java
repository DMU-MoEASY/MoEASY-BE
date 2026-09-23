package com.moeasy.moeasybe.domain.member.service.command;

import com.moeasy.moeasybe.domain.member.entity.Member;
import com.moeasy.moeasybe.domain.member.entity.SocialType;
import com.moeasy.moeasybe.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberCommandService {

    private final MemberRepository memberRepository;

    @Transactional
    public Member findOrCreateSocialMember(SocialType socialType, String socialId) {
        memberRepository.insertSocialMemberIfAbsent(socialType.name(), socialId);
        return memberRepository.findBySocialTypeAndSocialId(socialType, socialId)
                .orElseThrow(() -> new IllegalStateException("소셜 회원을 조회할 수 없습니다."));
    }
}
