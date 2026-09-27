package com.moeasy.moeasybe.global.security.jwt;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        @NotBlank String secret,
        @NotBlank String issuer,
        @NotNull @Valid TokenExpiration accessToken,
        @NotNull @Valid TokenExpiration refreshToken
) {

    public JwtProperties {
        if (secret != null && secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT_SECRET은 HS256을 위해 UTF-8 기준 32바이트 이상이어야 합니다.");
        }
    }

    public record TokenExpiration(
            @Positive long expirationTime
    ) {
    }
}
