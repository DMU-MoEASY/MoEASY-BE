package com.moeasy.moeasybe.domain.auth.service.result;

import com.moeasy.moeasybe.global.security.jwt.IssuedJwt;

public record AuthTokenPair(
        IssuedJwt accessToken,
        IssuedJwt refreshToken
) {
}
