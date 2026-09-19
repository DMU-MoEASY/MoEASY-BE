package com.moeasy.moeasybe.domain.auth.service.command;

import com.moeasy.moeasybe.domain.auth.code.AuthErrorCode;
import com.moeasy.moeasybe.domain.auth.config.AuthProperties;
import com.moeasy.moeasybe.domain.auth.dto.response.AuthResDTO;
import com.moeasy.moeasybe.domain.auth.exception.AuthException;
import com.moeasy.moeasybe.domain.auth.repository.AuthRedisRepository;
import com.moeasy.moeasybe.domain.member.entity.SocialType;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthCommandService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int STATE_BYTE_LENGTH = 32;
    private static final String STATE_KEY_PREFIX = "oauth:state:";

    private final AuthRedisRepository authRedisRepository;
    private final AuthProperties authProperties;

    public AuthResDTO.IssueState issueState(String providerName) {
        SocialType provider = parseProvider(providerName);
        String state = generateState();

        try {
            authRedisRepository.save(stateKey(state), provider.name(), authProperties.expiration());
        } catch (DataAccessException ex) {
            log.error("OAuth state를 Redis에 저장하지 못했습니다. provider={}", provider, ex);
            throw new AuthException(AuthErrorCode.OAUTH_STATE_ISSUANCE_FAILED);
        }

        return new AuthResDTO.IssueState(state);
    }

    private SocialType parseProvider(String providerName) {
        if (providerName == null) {
            throw new AuthException(AuthErrorCode.UNSUPPORTED_OAUTH_PROVIDER);
        }

        try {
            return SocialType.valueOf(providerName.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new AuthException(AuthErrorCode.UNSUPPORTED_OAUTH_PROVIDER);
        }
    }

    private String generateState() {
        byte[] randomBytes = new byte[STATE_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String stateKey(String state) {
        return STATE_KEY_PREFIX + state;
    }
}
