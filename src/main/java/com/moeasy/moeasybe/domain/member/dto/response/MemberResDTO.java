package com.moeasy.moeasybe.domain.member.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

public final class MemberResDTO {

    private MemberResDTO() {
    }

    @Builder
    public record NicknameAvailability(
            @Schema(description = "닉네임 사용 가능 여부", example = "true")
            boolean available
    ) {
    }

    @Builder
    @Schema(name = "MemberOnboardingResponse", description = "온보딩 완료 결과")
    public record Onboarding(
            @Schema(description = "회원 ID", example = "1") Long memberId,
            @Schema(description = "온보딩 완료 여부", example = "true") boolean onboardingCompleted
    ) {
    }
}
