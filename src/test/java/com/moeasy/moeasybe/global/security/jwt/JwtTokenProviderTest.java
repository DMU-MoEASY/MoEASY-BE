package com.moeasy.moeasybe.global.security.jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class JwtTokenProviderTest {

    private static final String SECRET = "unit-test-secret-that-is-long-enough-32-bytes";
    private static final Instant NOW = Instant.now().minusSeconds(10).truncatedTo(ChronoUnit.SECONDS);

    @Test
    void Access와_Refresh_Token에_회원_ID_발급자_종류_만료를_넣는다() {
        JwtProperties properties = properties();
        JwtTokenProvider provider = provider(properties, SECRET);

        IssuedJwt accessToken = provider.issueAccessToken(42L);
        IssuedJwt refreshToken = provider.issueRefreshToken(42L);
        var decodedAccess = provider.decode(accessToken.value());
        var decodedRefresh = provider.decode(refreshToken.value());

        assertEquals("42", decodedAccess.getSubject());
        assertEquals("https://moeasy", decodedAccess.getClaimAsString("iss"));
        assertEquals(JwtTokenProvider.ACCESS_TOKEN_TYPE,
                decodedAccess.getClaimAsString(JwtTokenProvider.TOKEN_TYPE_CLAIM));
        assertEquals(NOW.plusSeconds(300), decodedAccess.getExpiresAt());
        assertEquals(JwtTokenProvider.REFRESH_TOKEN_TYPE,
                decodedRefresh.getClaimAsString(JwtTokenProvider.TOKEN_TYPE_CLAIM));
        assertEquals(NOW.plusSeconds(1209600), decodedRefresh.getExpiresAt());
        assertNotEquals(accessToken.tokenId(), refreshToken.tokenId());
    }

    private JwtTokenProvider provider(JwtProperties properties, String secret) {
        SecretKey secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.issuer()));
        return new JwtTokenProvider(
                encoder,
                decoder,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private JwtProperties properties() {
        return new JwtProperties(
                SECRET,
                "https://moeasy",
                new JwtProperties.TokenExpiration(300_000),
                new JwtProperties.TokenExpiration(1_209_600_000)
        );
    }
}
