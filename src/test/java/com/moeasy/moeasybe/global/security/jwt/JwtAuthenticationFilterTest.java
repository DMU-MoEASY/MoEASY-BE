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
import java.time.temporal.ChronoUnit;
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
    private static final Instant NOW = Instant.now().minusSeconds(10).truncatedTo(ChronoUnit.SECONDS);

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
        JwtProperties properties = new JwtProperties(
                SECRET,
                "https://moeasy",
                new JwtProperties.TokenExpiration(300_000),
                new JwtProperties.TokenExpiration(1_209_600_000)
        );
        SecretKey secretKey = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return new JwtTokenProvider(encoder, decoder, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
