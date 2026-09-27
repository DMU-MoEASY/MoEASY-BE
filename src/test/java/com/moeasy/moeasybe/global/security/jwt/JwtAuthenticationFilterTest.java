package com.moeasy.moeasybe.global.security.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.moeasy.moeasybe.domain.auth.config.AuthCookieNames;
import com.moeasy.moeasybe.global.security.SecurityErrorResponseWriter;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.mock.web.MockFilterChain;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "filter-test-secret-with-a-minimum-of-32-bytes";
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void 유효한_Access_Token_쿠키를_회원_ID_인증정보로_등록한다() throws Exception {
        JwtTokenProvider provider = provider();
        String token = provider.issueAccessToken(42L).value();
        JwtAuthenticationFilter filter = filter(provider);
        MockHttpServletRequest request = protectedRequest(token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(42L, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        assertEquals(200, response.getStatus());
        assertTrue(chain.getRequest() != null);
    }

    @Test
    void Refresh_Token을_Access_Token_쿠키로_보내면_인증을_거부한다() throws Exception {
        JwtTokenProvider provider = provider();
        String token = provider.issueRefreshToken(42L).value();
        JwtAuthenticationFilter filter = filter(provider);
        MockHttpServletRequest request = protectedRequest(token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertTrue(chain.getRequest() == null);
    }

    @Test
    void Access_Token_쿠키가_없으면_인증없이_요청을_계속한다() throws Exception {
        JwtAuthenticationFilter filter = filter(provider());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/members/me");
        request.setServletPath("/api/v1/members/me");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(200, response.getStatus());
        assertTrue(chain.getRequest() != null);
    }

    @Test
    void 빈_Access_Token_쿠키는_인증없이_요청을_계속한다() throws Exception {
        JwtAuthenticationFilter filter = filter(provider());
        MockHttpServletRequest request = protectedRequest("");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(200, response.getStatus());
        assertTrue(chain.getRequest() != null);
    }

    @Test
    void 서명이_다른_Access_Token은_인증을_거부한다() throws Exception {
        JwtTokenProvider otherProvider = provider("another-filter-secret-with-a-minimum-of-32-bytes", "https://moeasy", NOW);
        String token = otherProvider.issueAccessToken(42L).value();
        JwtAuthenticationFilter filter = filter(provider());
        MockHttpServletRequest request = protectedRequest(token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(chain.getRequest());
    }

    @Test
    void 만료된_Access_Token은_인증을_거부한다() throws Exception {
        JwtTokenProvider expiredTokenProvider = provider(
                SECRET,
                "https://moeasy",
                Instant.parse("2020-01-01T00:00:00Z")
        );
        String token = expiredTokenProvider.issueAccessToken(42L).value();
        JwtAuthenticationFilter filter = filter(provider());
        MockHttpServletRequest request = protectedRequest(token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(chain.getRequest());
    }

    @Test
    void issuer가_다른_Access_Token은_인증을_거부한다() throws Exception {
        JwtTokenProvider otherIssuerProvider = provider(SECRET, "https://other-moeasy", NOW);
        String token = otherIssuerProvider.issueAccessToken(42L).value();
        JwtAuthenticationFilter filter = filter(provider());
        MockHttpServletRequest request = protectedRequest(token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(chain.getRequest());
    }

    @Test
    void 공개_경로는_잘못된_Access_Token_쿠키가_있어도_필터를_통과한다() throws Exception {
        JwtAuthenticationFilter filter = filter(provider());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/reissue");
        request.setServletPath("/api/v1/auth/reissue");
        request.setCookies(new jakarta.servlet.http.Cookie(AuthCookieNames.ACCESS_TOKEN, "invalid-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(200, response.getStatus());
        assertTrue(chain.getRequest() != null);
    }

    private JwtAuthenticationFilter filter(JwtTokenProvider provider) {
        return new JwtAuthenticationFilter(provider, new SecurityErrorResponseWriter());
    }

    private MockHttpServletRequest protectedRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/members/me");
        request.setServletPath("/api/v1/members/me");
        request.setCookies(new jakarta.servlet.http.Cookie(AuthCookieNames.ACCESS_TOKEN, token));
        return request;
    }

    private JwtTokenProvider provider() {
        return provider(SECRET, "https://moeasy", NOW);
    }

    private JwtTokenProvider provider(String secret, String issuer, Instant now) {
        JwtProperties properties = new JwtProperties(
                secret,
                issuer,
                new JwtProperties.TokenExpiration(300_000),
                new JwtProperties.TokenExpiration(1_209_600_000)
        );
        SecretKey secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return new JwtTokenProvider(encoder, decoder, properties, Clock.fixed(now, ZoneOffset.UTC));
    }
}
