package com.moeasy.moeasybe.domain.member.converter;

import com.moeasy.moeasybe.domain.group.entity.Category;
import com.moeasy.moeasybe.domain.member.dto.response.MemberResDTO;
import com.moeasy.moeasybe.domain.member.entity.Member;
import com.moeasy.moeasybe.domain.member.entity.MemberCategory;
import com.moeasy.moeasybe.domain.member.enums.SocialType;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public final class MemberConverter {

    public static MemberResDTO.NicknameAvailability toNicknameAvailability(boolean available) {
        return MemberResDTO.NicknameAvailability.builder().available(available).build();
    }

    public static List<MemberCategory> toMemberCategories(Member member, List<Category> categories) {
        return categories.stream()
                .map(category -> MemberCategory.builder().member(member).category(category).build())
                .toList();
    }

    public static MemberResDTO.Onboarding toOnboarding(Member member) {
        return MemberResDTO.Onboarding.builder()
                .memberId(member.getId())
                .onboardingCompleted(member.isOnboardingCompleted())
                .build();
    }

    public Member toSocialMember(SocialType socialType, String socialId) {
        return Member.builder()
                .socialType(socialType)
                .socialId(socialId)
                .build();
    }
}
