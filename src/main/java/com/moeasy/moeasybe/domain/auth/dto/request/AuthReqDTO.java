package com.moeasy.moeasybe.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

public final class AuthReqDTO {

    private AuthReqDTO() {}

    @Builder
    public record IssueState(
            @Schema(
                    description = "소셜 로그인 제공자",
                    allowableValues = {"KAKAO", "GOOGLE"},
                    example = "KAKAO"
            )
            @NotBlank(message = "provider는 필수입니다.")
            String provider
    ) {
    }

    @Builder
    public record KakaoLogin(
            @Schema(description = "카카오에서 발급받은 인가 코드", example = "SplxlOBeZQQYbYS6WxSbIA")
            @NotBlank(message = "인가 코드는 필수입니다.")
            String code,

            @Schema(description = "로그인 시작 전에 백엔드에서 발급받은 state")
            @NotBlank(message = "state는 필수입니다.")
            String state
    ) {
    }

    @Builder
    public record GoogleLogin(
            @Schema(description = "구글에서 발급받은 인가 코드", example = "4/0AcvDMr...")
            @NotBlank(message = "인가 코드는 필수입니다.")
            String code,

            @Schema(description = "로그인 시작 전에 백엔드에서 발급받은 state")
            @NotBlank(message = "state는 필수입니다.")
            String state
    ) {
    }
}
