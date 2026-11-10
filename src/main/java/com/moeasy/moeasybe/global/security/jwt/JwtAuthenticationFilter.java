package com.moeasy.moeasybe.global.security.jwt;

import com.moeasy.moeasybe.domain.auth.config.AuthCookieNames;
import com.moeasy.moeasybe.global.apiPayload.code.GeneralErrorCode;
import com.moeasy.moeasybe.global.security.SecurityErrorResponseWriter;
import com.moeasy.moeasybe.global.security.SecurityRequestMatchers;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final DefaultBearerTokenResolver BEARER_TOKEN_RESOLVER = new DefaultBearerTokenResolver();

    private final JwtTokenProvider jwtTokenProvider;
    private final SecurityErrorResponseWriter responseWriter;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return SecurityRequestMatchers.PUBLIC_ENDPOINTS.matches(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String token = findAccessToken(request);
            if (token != null && !token.isBlank()) {
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
            }
        } catch (JwtException | IllegalArgumentException | OAuth2AuthenticationException ex) {
            SecurityContextHolder.clearContext();
            responseWriter.write(response, GeneralErrorCode.UNAUTHORIZED);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String findAccessToken(HttpServletRequest request) {
        if (request.getHeader(HttpHeaders.AUTHORIZATION) != null) {
            if (Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION)).size() != 1) {
                throw new JwtException("Authorization 헤더는 하나만 전달해야 합니다.");
            }
            String token = BEARER_TOKEN_RESOLVER.resolve(request);
            if (token == null) {
                throw new JwtException("Bearer 액세스 토큰이 필요합니다.");
            }
            return token;
        }

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
