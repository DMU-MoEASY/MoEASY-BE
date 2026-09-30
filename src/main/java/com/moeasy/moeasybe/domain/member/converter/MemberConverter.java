package com.moeasy.moeasybe.domain.member.converter;

import com.moeasy.moeasybe.domain.member.dto.response.MemberResDTO;
import com.moeasy.moeasybe.domain.member.entity.Member;
import com.moeasy.moeasybe.domain.member.enums.SocialType;
import org.springframework.stereotype.Component;

@Component
public final class MemberConverter {

    public static MemberResDTO.NicknameAvailability toNicknameAvailability(boolean available) {
        return MemberResDTO.NicknameAvailability.builder().available(available).build();
    }

    public Member toSocialMember(SocialType socialType, String socialId) {
        return Member.builder()
                .socialType(socialType)
                .socialId(socialId)
                .build();
    }
}
