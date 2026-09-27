package com.moeasy.moeasybe.domain.auth.converter;

import com.moeasy.moeasybe.domain.auth.dto.response.AuthResDTO;
import com.moeasy.moeasybe.domain.member.entity.Member;

public final class AuthConverter {

    private AuthConverter() {
    }

    public static AuthResDTO.IssueState toIssueState(String state) {
        return AuthResDTO.IssueState.builder()
                .state(state)
                .build();
    }

    public static AuthResDTO.SocialLogin toSocialLogin(Member member) {
        return AuthResDTO.SocialLogin.builder()
                .memberId(member.getId())
                .onboardingCompleted(member.isOnboardingCompleted())
                .build();
    }

    public static AuthResDTO.Csrf toCsrf(String token, String headerName) {
        return AuthResDTO.Csrf.builder()
                .token(token)
                .headerName(headerName)
                .build();
    }
}
