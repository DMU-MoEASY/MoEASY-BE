package com.moeasy.moeasybe.domain.auth.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "oauth.kakao")
public record KakaoProperties(
        @NotBlank String restApiKey,
        String clientSecret,
        @NotBlank String tokenUri,
        @NotBlank String userInfoUri
) {
}
