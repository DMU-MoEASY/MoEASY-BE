package com.moeasy.moeasybe.global.security.jwt;

import com.moeasy.moeasybe.domain.auth.config.AuthCookieNames;
import com.moeasy.moeasybe.global.apiPayload.code.GeneralErrorCode;
import com.moeasy.moeasybe.global.security.SecurityErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/auth/csrf",
            "/api/v1/auth/oauth/states",
            "/api/v1/auth/oauth/kakao",
            "/api/v1/auth/oauth/google",
            "/api/v1/auth/reissue",
            "/api/v1/auth/logout",
            "/swagger-ui.html",
            "/actuator/health"
    );

    private final JwtTokenProvider jwtTokenProvider;
    private final SecurityErrorResponseWriter responseWriter;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return PUBLIC_PATHS.contains(path)
                || path.startsWith("/swagger-ui/")
                || path.startsWith("/v3/api-docs/")
                || "/v3/api-docs".equals(path)
                || path.startsWith("/actuator/health/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = findAccessToken(request);
        if (token == null || token.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Jwt jwt = jwtTokenProvider.decode(token);
            if (!JwtTokenProvider.ACCESS_TOKEN_TYPE.equals(
                    jwt.getClaimAsString(JwtTokenProvider.TOKEN_TYPE_CLAIM)
            )) {
                throw new JwtException("Access Token이 아닙니다.");
            }

            Long memberId = Long.valueOf(jwt.getSubject());
            if (memberId <= 0) {
                throw new JwtException("회원 식별자가 유효하지 않습니다.");
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(memberId, null, List.of());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        } catch (JwtException | IllegalArgumentException ex) {
            SecurityContextHolder.clearContext();
            responseWriter.write(response, GeneralErrorCode.UNAUTHORIZED);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String findAccessToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (AuthCookieNames.ACCESS_TOKEN.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
