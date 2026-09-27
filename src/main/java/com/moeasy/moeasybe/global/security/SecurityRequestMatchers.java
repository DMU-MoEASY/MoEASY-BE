package com.moeasy.moeasybe.global.security;

import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

public final class SecurityRequestMatchers {

    private static final PathPatternRequestMatcher.Builder PATH_MATCHER =
            PathPatternRequestMatcher.withDefaults();

    public static final RequestMatcher PUBLIC_ENDPOINTS = new OrRequestMatcher(
            PATH_MATCHER.matcher("/api/v1/auth/csrf"),
            PATH_MATCHER.matcher("/api/v1/auth/oauth/states"),
            PATH_MATCHER.matcher("/api/v1/auth/oauth/kakao"),
            PATH_MATCHER.matcher("/api/v1/auth/oauth/google"),
            PATH_MATCHER.matcher("/api/v1/auth/reissue"),
            PATH_MATCHER.matcher("/api/v1/auth/logout"),
            PATH_MATCHER.matcher("/swagger-ui.html"),
            PATH_MATCHER.matcher("/swagger-ui/**"),
            PATH_MATCHER.matcher("/v3/api-docs"),
            PATH_MATCHER.matcher("/v3/api-docs/**"),
            PATH_MATCHER.matcher("/actuator/health"),
            PATH_MATCHER.matcher("/actuator/health/**")
    );

    private SecurityRequestMatchers() {
    }
}
