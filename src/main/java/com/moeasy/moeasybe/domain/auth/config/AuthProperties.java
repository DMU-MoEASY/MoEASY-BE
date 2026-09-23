package com.moeasy.moeasybe.domain.auth.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "oauth.state")
public record AuthProperties(
        @NotNull Duration expiration
) {
}
