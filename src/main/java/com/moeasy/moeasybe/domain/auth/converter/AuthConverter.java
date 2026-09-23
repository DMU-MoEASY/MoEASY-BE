package com.moeasy.moeasybe.domain.auth.converter;

import com.moeasy.moeasybe.domain.auth.dto.response.AuthResDTO;
import com.moeasy.moeasybe.domain.member.entity.Member;
import org.springframework.stereotype.Component;

@Component
public final class AuthConverter {

    public AuthResDTO.IssueState toIssueState(String state) {
        return AuthResDTO.IssueState.builder()
                .state(state)
                .build();
    }

    public AuthResDTO.SocialLogin toSocialLogin(Member member) {
        return AuthResDTO.SocialLogin.builder()
                .memberId(member.getId())
                .onboardingCompleted(member.isOnboardingCompleted())
                .build();
    }
}
