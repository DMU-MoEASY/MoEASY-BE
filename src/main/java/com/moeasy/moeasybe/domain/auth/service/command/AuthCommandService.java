package com.moeasy.moeasybe.domain.auth.service.command;

import com.moeasy.moeasybe.domain.auth.client.GoogleOAuthClient;
import com.moeasy.moeasybe.domain.auth.client.KakaoOAuthClient;
import com.moeasy.moeasybe.domain.auth.config.AuthProperties;
import com.moeasy.moeasybe.domain.auth.converter.AuthConverter;
import com.moeasy.moeasybe.domain.auth.dto.request.AuthReqDTO;
import com.moeasy.moeasybe.domain.auth.dto.response.AuthResDTO;
import com.moeasy.moeasybe.domain.auth.exception.AuthException;
import com.moeasy.moeasybe.domain.auth.exception.code.AuthErrorCode;
import com.moeasy.moeasybe.domain.auth.repository.AuthRedisRepository;
import com.moeasy.moeasybe.domain.auth.service.result.AuthSession;
import com.moeasy.moeasybe.domain.auth.service.result.AuthTokenPair;
import com.moeasy.moeasybe.domain.member.entity.Member;
import com.moeasy.moeasybe.domain.member.enums.SocialType;
import com.moeasy.moeasybe.domain.member.service.command.MemberCommandService;
import com.moeasy.moeasybe.global.security.jwt.IssuedJwt;
import com.moeasy.moeasybe.global.security.jwt.JwtTokenProvider;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
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
    private final JwtTokenProvider jwtTokenProvider;

    public AuthResDTO.IssueState issueState(String providerName, String browserId) {
        SocialType provider = parseProvider(providerName);
        String state = generateState();

        try {
            authRedisRepository.save(stateKey(state), stateValue(provider, browserId), authProperties.expiration());
        } catch (DataAccessException ex) {
            log.error("OAuth state를 Redis에 저장하지 못했습니다. provider={}", provider, ex);
            throw new AuthException(AuthErrorCode.OAUTH_STATE_ISSUANCE_FAILED);
        }

        return AuthConverter.toIssueState(state);
    }

    public AuthSession loginWithKakao(AuthReqDTO.KakaoLogin request, String browserId) {
        validateAndConsumeState(request.state(), SocialType.KAKAO, browserId);

        String socialId = kakaoOAuthClient.getUserId(request.code());
        Member member = memberCommandService.findOrCreateSocialMember(SocialType.KAKAO, socialId);

        AuthResDTO.SocialLogin response = AuthConverter.toSocialLogin(member);
        return new AuthSession(response, issueTokens(member.getId()));
    }

    public AuthSession loginWithGoogle(AuthReqDTO.GoogleLogin request, String browserId) {
        validateAndConsumeState(request.state(), SocialType.GOOGLE, browserId);

        String socialId = googleOAuthClient.getUserId(request.code());
        Member member = memberCommandService.findOrCreateSocialMember(SocialType.GOOGLE, socialId);

        AuthResDTO.SocialLogin response = AuthConverter.toSocialLogin(member);
        return new AuthSession(response, issueTokens(member.getId()));
    }

    public AuthTokenPair reissue(String refreshTokenValue) {
        RefreshTokenClaims previous = parseRefreshToken(refreshTokenValue);
        AuthTokenPair next = createTokenPair(previous.memberId());

        try {
            boolean rotated = authRedisRepository.rotateRefreshToken(
                    previous.tokenId(),
                    next.refreshToken().tokenId(),
                    previous.memberId(),
                    next.refreshToken().expiration()
            );
            if (!rotated) {
                throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
            }
        } catch (DataAccessException ex) {
            log.error("Refresh Token을 Redis에서 갱신하지 못했습니다.", ex);
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_STORAGE_FAILED);
        }

        return next;
    }

    public void logout(String refreshTokenValue) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            return;
        }

        RefreshTokenClaims refreshToken;
        try {
            refreshToken = parseRefreshToken(refreshTokenValue);
        } catch (AuthException ex) {
            return;
        }

        try {
            authRedisRepository.deleteRefreshToken(refreshToken.tokenId());
        } catch (DataAccessException ex) {
            log.error("로그아웃 중 Refresh Token을 Redis에서 삭제하지 못했습니다.", ex);
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_STORAGE_FAILED);
        }
    }

    private AuthTokenPair issueTokens(Long memberId) {
        IssuedJwt accessToken = jwtTokenProvider.issueAccessToken(memberId);
        IssuedJwt refreshToken = jwtTokenProvider.issueRefreshToken(memberId);
        try {
            authRedisRepository.saveRefreshToken(
                    refreshToken.tokenId(),
                    memberId,
                    refreshToken.expiration()
            );
        } catch (DataAccessException ex) {
            log.error("Refresh Token을 Redis에 저장하지 못했습니다. memberId={}", memberId, ex);
            throw new AuthException(AuthErrorCode.REFRESH_TOKEN_STORAGE_FAILED);
        }
        return new AuthTokenPair(accessToken, refreshToken);
    }

    private AuthTokenPair createTokenPair(Long memberId) {
        return new AuthTokenPair(
                jwtTokenProvider.issueAccessToken(memberId),
                jwtTokenProvider.issueRefreshToken(memberId)
        );
    }

    private RefreshTokenClaims parseRefreshToken(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) {
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        try {
            Jwt jwt = jwtTokenProvider.decode(tokenValue);
            if (!JwtTokenProvider.REFRESH_TOKEN_TYPE.equals(
                    jwt.getClaimAsString(JwtTokenProvider.TOKEN_TYPE_CLAIM)
            )) {
                throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
            }

            Long memberId = Long.valueOf(jwt.getSubject());
            String tokenId = jwt.getId();
            if (tokenId == null || tokenId.isBlank()) {
                throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
            }
            return new RefreshTokenClaims(memberId, tokenId);
        } catch (JwtException | NumberFormatException ex) {
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
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

    private record RefreshTokenClaims(Long memberId, String tokenId) {
    }
}
