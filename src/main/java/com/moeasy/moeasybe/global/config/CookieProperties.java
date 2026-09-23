package com.moeasy.moeasybe.global.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.cookie")
public record CookieProperties(
        boolean secure,
        @NotBlank @Pattern(regexp = "(?i)Strict|Lax|None") String sameSite
) {

    public CookieProperties {
        if ("None".equalsIgnoreCase(sameSite) && !secure) {
            throw new IllegalArgumentException("SameSite=None 쿠키에는 Secure=true가 필요합니다.");
        }
    }
}
