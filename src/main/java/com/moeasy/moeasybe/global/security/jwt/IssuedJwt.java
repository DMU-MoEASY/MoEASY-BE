package com.moeasy.moeasybe.global.security.jwt;

import java.time.Duration;

public record IssuedJwt(
        String value,
        String tokenId,
        Duration expiration
) {

    @Override
    public String toString() {
        return "IssuedJwt[tokenId=" + tokenId + ", expiration=" + expiration + "]";
    }
}
