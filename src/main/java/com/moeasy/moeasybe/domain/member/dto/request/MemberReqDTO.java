package com.moeasy.moeasybe.domain.member.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class MemberReqDTO {

    private MemberReqDTO() {
    }

    public record NicknameAvailability(
            @NotBlank(message = "닉네임은 필수입니다.")
            @Size(min = 2, max = 12, message = "닉네임은 2자 이상 12자 이하여야 합니다.")
            @Schema(description = "확인할 닉네임 (2~12자, 공백만 입력 불가)", example = "모이지",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            String nickname
    ) {
    }
}
