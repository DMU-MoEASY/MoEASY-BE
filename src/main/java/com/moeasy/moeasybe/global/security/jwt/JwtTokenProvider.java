package com.moeasy.moeasybe.global.security.jwt;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    public static final String TOKEN_TYPE_CLAIM = "token_type";
    public static final String ACCESS_TOKEN_TYPE = "access";
    public static final String REFRESH_TOKEN_TYPE = "refresh";

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final JwtProperties jwtProperties;
    private final Clock jwtClock;

    public IssuedJwt issueAccessToken(Long memberId) {
        Duration expiration = Duration.ofMillis(jwtProperties.accessToken().expirationTime());
        return issueToken(memberId, ACCESS_TOKEN_TYPE, expiration);
    }

    public IssuedJwt issueRefreshToken(Long memberId) {
        Duration expiration = Duration.ofMillis(jwtProperties.refreshToken().expirationTime());
        return issueToken(memberId, REFRESH_TOKEN_TYPE, expiration);
    }

    public Jwt decode(String token) {
        return jwtDecoder.decode(token);
    }

    private IssuedJwt issueToken(Long memberId, String tokenType, Duration expiration) {
        Instant issuedAt = jwtClock.instant();
        String tokenId = UUID.randomUUID().toString();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject(memberId.toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(expiration))
                .id(tokenId)
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .build();

        String value = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(),
                claims
        )).getTokenValue();

        return new IssuedJwt(value, tokenId, expiration);
    }
}
