package com.moeasy.moeasybe.domain.member.converter;

import com.moeasy.moeasybe.domain.member.entity.Member;
import com.moeasy.moeasybe.domain.member.enums.SocialType;
import org.springframework.stereotype.Component;

@Component
public final class MemberConverter {

    public Member toSocialMember(SocialType socialType, String socialId) {
        return Member.builder()
                .socialType(socialType)
                .socialId(socialId)
                .build();
    }
}
