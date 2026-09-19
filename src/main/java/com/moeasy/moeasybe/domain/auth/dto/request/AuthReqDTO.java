package com.moeasy.moeasybe.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public class AuthReqDTO {

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
}
