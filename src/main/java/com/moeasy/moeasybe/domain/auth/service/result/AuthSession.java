package com.moeasy.moeasybe.domain.auth.service.result;

import com.moeasy.moeasybe.domain.auth.dto.response.AuthResDTO;

public record AuthSession(
        AuthResDTO.SocialLogin member,
        AuthTokenPair tokens
) {
}
