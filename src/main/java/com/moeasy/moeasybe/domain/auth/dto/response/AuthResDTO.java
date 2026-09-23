package com.moeasy.moeasybe.domain.auth.dto.response;

public final class AuthResDTO {

    public record IssueState(
            String state
    ) {
    }

    public record SocialLogin(
            Long memberId,
            boolean onboardingCompleted
    ) {
    }
}
