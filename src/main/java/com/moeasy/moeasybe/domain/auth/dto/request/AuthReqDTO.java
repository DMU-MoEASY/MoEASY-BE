package com.moeasy.moeasybe.domain.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthReqDTO {

    public record IssueState(
            @Schema(
                    description = "소셜 로그인 제공자",
                    allowableValues = {"KAKAO", "GOOGLE"},
                    example = "KAKAO"
            )
            @NotBlank(message = "provider는 필수입니다.")
            String provider,

            @Schema(description = "로그인을 시작한 브라우저를 식별하는 일회성 상관값")
            @NotBlank(message = "correlationId는 필수입니다.")
            @Size(min = 32, max = 128, message = "correlationId는 32자 이상 128자 이하여야 합니다.")
            String correlationId
    ) {
    }

    public record KakaoLogin(
            @Schema(description = "카카오에서 발급받은 인가 코드", example = "SplxlOBeZQQYbYS6WxSbIA")
            @NotBlank(message = "인가 코드는 필수입니다.")
            String code,

            @Schema(description = "로그인 시작 전에 백엔드에서 발급받은 state")
            @NotBlank(message = "state는 필수입니다.")
            String state,

            @Schema(
                    description = "카카오 인가 코드 요청에 사용한 redirectUri와 완전히 같은 값",
                    example = "https://dev.moeasy.kr/oauth/kakao/callback"
            )
            @NotBlank(message = "redirectUri는 필수입니다.")
            String redirectUri,

            @Schema(description = "state 발급 요청에 사용한 브라우저 상관값")
            @NotBlank(message = "correlationId는 필수입니다.")
            @Size(min = 32, max = 128, message = "correlationId는 32자 이상 128자 이하여야 합니다.")
            String correlationId
    ) {
    }

    public record GoogleLogin(
            @Schema(description = "구글에서 발급받은 인가 코드", example = "4/0AcvDMr...")
            @NotBlank(message = "인가 코드는 필수입니다.")
            String code,

            @Schema(description = "로그인 시작 전에 백엔드에서 발급받은 state")
            @NotBlank(message = "state는 필수입니다.")
            String state,

            @Schema(
                    description = "구글 인가 코드 요청에 사용한 redirectUri와 완전히 같은 값",
                    example = "https://dev.moeasy.kr/oauth/google/callback"
            )
            @NotBlank(message = "redirectUri는 필수입니다.")
            String redirectUri,

            @Schema(description = "state 발급 요청에 사용한 브라우저 상관값")
            @NotBlank(message = "correlationId는 필수입니다.")
            @Size(min = 32, max = 128, message = "correlationId는 32자 이상 128자 이하여야 합니다.")
            String correlationId
    ) {
    }
}
