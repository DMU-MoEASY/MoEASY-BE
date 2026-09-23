package com.moeasy.moeasybe.domain.auth.service.command;

import com.moeasy.moeasybe.domain.auth.code.AuthErrorCode;
import com.moeasy.moeasybe.domain.auth.client.GoogleOAuthClient;
import com.moeasy.moeasybe.domain.auth.client.KakaoOAuthClient;
import com.moeasy.moeasybe.domain.auth.config.AuthProperties;
import com.moeasy.moeasybe.domain.auth.dto.request.AuthReqDTO;
import com.moeasy.moeasybe.domain.auth.dto.response.AuthResDTO;
import com.moeasy.moeasybe.domain.auth.exception.AuthException;
import com.moeasy.moeasybe.domain.auth.repository.AuthRedisRepository;
import com.moeasy.moeasybe.domain.member.entity.Member;
import com.moeasy.moeasybe.domain.member.entity.SocialType;
import com.moeasy.moeasybe.domain.member.service.command.MemberCommandService;
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
    private final KakaoOAuthClient kakaoOAuthClient;
    private final GoogleOAuthClient googleOAuthClient;
    private final MemberCommandService memberCommandService;

    public AuthResDTO.IssueState issueState(String providerName, String browserId) {
        SocialType provider = parseProvider(providerName);
        String state = generateState();

        try {
            authRedisRepository.save(stateKey(state), stateValue(provider, browserId), authProperties.expiration());
        } catch (DataAccessException ex) {
            log.error("OAuth state를 Redis에 저장하지 못했습니다. provider={}", provider, ex);
            throw new AuthException(AuthErrorCode.OAUTH_STATE_ISSUANCE_FAILED);
        }

        return new AuthResDTO.IssueState(state);
    }

    public AuthResDTO.SocialLogin loginWithKakao(AuthReqDTO.KakaoLogin request, String browserId) {
        validateAndConsumeState(request.state(), SocialType.KAKAO, browserId);

        String socialId = kakaoOAuthClient.getUserId(request.code(), request.redirectUri());
        Member member = memberCommandService.findOrCreateSocialMember(SocialType.KAKAO, socialId);

        return new AuthResDTO.SocialLogin(
                member.getId(),
                member.isOnboardingCompleted()
        );
    }

    public AuthResDTO.SocialLogin loginWithGoogle(AuthReqDTO.GoogleLogin request, String browserId) {
        validateAndConsumeState(request.state(), SocialType.GOOGLE, browserId);

        String socialId = googleOAuthClient.getUserId(request.code(), request.redirectUri());
        Member member = memberCommandService.findOrCreateSocialMember(SocialType.GOOGLE, socialId);

        return new AuthResDTO.SocialLogin(
                member.getId(),
                member.isOnboardingCompleted()
        );
    }

    private void validateAndConsumeState(String state, SocialType expectedProvider, String browserId) {
        if (browserId == null || browserId.isBlank()) {
            throw new AuthException(AuthErrorCode.INVALID_OAUTH_STATE);
        }

        String savedValue;
        try {
            savedValue = authRedisRepository.getAndDelete(stateKey(state));
        } catch (DataAccessException ex) {
            log.error("OAuth state를 Redis에서 확인하지 못했습니다. provider={}", expectedProvider, ex);
            throw new AuthException(AuthErrorCode.OAUTH_STATE_VERIFICATION_FAILED);
        }

        if (!stateValue(expectedProvider, browserId).equals(savedValue)) {
            throw new AuthException(AuthErrorCode.INVALID_OAUTH_STATE);
        }
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

    private String stateValue(SocialType provider, String browserId) {
        return provider.name() + ":" + browserId;
    }
}
