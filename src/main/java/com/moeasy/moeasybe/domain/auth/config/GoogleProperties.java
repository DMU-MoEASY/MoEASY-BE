package com.moeasy.moeasybe.domain.auth.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "oauth.google")
public record GoogleProperties(
        @NotBlank String webClientId,
        @NotBlank String clientSecret,
        @NotBlank String redirectUri,
        @NotBlank String tokenUri,
        @NotBlank String userInfoUri
) {
}
