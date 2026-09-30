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
}
