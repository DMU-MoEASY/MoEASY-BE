package com.moeasy.moeasybe.global.security;

import java.util.List;
import org.springframework.http.server.PathContainer;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

public final class SecurityRequestMatchers {

    private static final PathPatternRequestMatcher.Builder PATH_MATCHER =
            PathPatternRequestMatcher.withDefaults();

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/csrf",
            "/api/v1/auth/oauth/states",
            "/api/v1/auth/oauth/kakao",
            "/api/v1/auth/oauth/google",
            "/api/v1/auth/reissue",
            "/api/v1/auth/logout",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/actuator/health",
            "/actuator/health/**"
    );

    public static final RequestMatcher PUBLIC_ENDPOINTS = new OrRequestMatcher(
            PUBLIC_PATHS.stream().map(PATH_MATCHER::matcher).toList());

    private static final List<PathPattern> PUBLIC_PATH_PATTERNS = PUBLIC_PATHS.stream()
            .map(PathPatternParser.defaultInstance::parse)
            .toList();

    public static boolean isPublicPath(String path) {
        PathContainer container = PathContainer.parsePath(path);
        return PUBLIC_PATH_PATTERNS.stream().anyMatch(pattern -> pattern.matches(container));
    }

    private SecurityRequestMatchers() {
    }
}
