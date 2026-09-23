package com.moeasy.moeasybe.domain.auth.dto.response;

import lombok.Builder;

public final class AuthResDTO {

    @Builder
    public record IssueState(
            String state
    ) {
    }

    @Builder
    public record SocialLogin(
            Long memberId,
            boolean onboardingCompleted
    ) {
    }
}
