package com.moeasy.moeasybe.domain.member.service.command;

import com.moeasy.moeasybe.domain.member.converter.MemberConverter;
import com.moeasy.moeasybe.domain.member.entity.Member;
import com.moeasy.moeasybe.domain.member.enums.SocialType;
import com.moeasy.moeasybe.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberCommandService {

    private final MemberRepository memberRepository;
    private final MemberConverter memberConverter;

    @Transactional
    public Member findOrCreateSocialMember(SocialType socialType, String socialId) {
        return memberRepository.findBySocialTypeAndSocialId(socialType, socialId)
                .orElseGet(() -> {
                    Member member = memberConverter.toSocialMember(socialType, socialId);
                    return memberRepository.save(member);
                });
    }
}
