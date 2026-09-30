package com.moeasy.moeasybe.domain.auth.service.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.moeasy.moeasybe.domain.auth.client.GoogleOAuthClient;
import com.moeasy.moeasybe.domain.auth.client.KakaoOAuthClient;
import com.moeasy.moeasybe.domain.auth.config.AuthProperties;
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
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class AuthCommandServiceTest {

    private static final Duration STATE_EXPIRATION = Duration.ofMinutes(5);
    private static final String BROWSER_ID = "browser-123";

    @Mock
    private AuthRedisRepository authRedisRepository;

    @Mock
    private KakaoOAuthClient kakaoOAuthClient;

    @Mock
    private GoogleOAuthClient googleOAuthClient;

    @Mock
    private MemberCommandService memberCommandService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private AuthCommandService authCommandService;

    @BeforeEach
    void setUp() {
        authCommandService = new AuthCommandService(
                authRedisRepository,
                new AuthProperties(STATE_EXPIRATION),
                kakaoOAuthClient,
                googleOAuthClient,
                memberCommandService,
                jwtTokenProvider
        );
    }

    @Test
    void 카카오_state를_발급하고_Redis에_저장한다() {
        AuthResDTO.IssueState response = authCommandService.issueState("KAKAO", BROWSER_ID);

        ArgumentCaptor<String> stateCaptor = ArgumentCaptor.forClass(String.class);
        verify(authRedisRepository).save(
                stateCaptor.capture(),
                eq(SocialType.KAKAO.name() + ":" + BROWSER_ID),
                eq(STATE_EXPIRATION)
        );
        assertEquals("oauth:state:" + response.state(), stateCaptor.getValue());
        assertFalse(response.state().isBlank());
    }

    @Test
    void 지원하지_않는_제공자는_인증_예외를_던진다() {
        AuthException exception = assertThrows(
                AuthException.class,
                () -> authCommandService.issueState("NAVER", BROWSER_ID)
        );

        assertEquals(AuthErrorCode.UNSUPPORTED_OAUTH_PROVIDER, exception.getCode());
    }

    @Test
    void 카카오_state를_소비하고_기존_회원을_로그인한다() {
        AuthReqDTO.KakaoLogin request = AuthReqDTO.KakaoLogin.builder()
                .code("authorization-code")
                .state("issued-state")
                .build();
        Member member = mock(Member.class);

        when(authRedisRepository.getAndDelete("oauth:state:issued-state"))
                .thenReturn(SocialType.KAKAO.name() + ":" + BROWSER_ID);
        when(kakaoOAuthClient.getUserId(request.code()))
                .thenReturn("123456789");
        when(memberCommandService.findOrCreateSocialMember(SocialType.KAKAO, "123456789"))
                .thenReturn(member);
        stubIssuedTokens(7L);
        when(member.getId()).thenReturn(7L);
        when(member.isOnboardingCompleted()).thenReturn(true);

        AuthSession response = authCommandService.loginWithKakao(request, BROWSER_ID);

        assertEquals(7L, response.member().memberId());
        assertTrue(response.member().onboardingCompleted());
        verify(authRedisRepository).getAndDelete("oauth:state:issued-state");
        verify(authRedisRepository).saveRefreshToken("refresh-id", 7L, Duration.ofDays(14));
    }

    @Test
    void 구글용_state로_카카오_로그인을_요청하면_거부한다() {
        AuthReqDTO.KakaoLogin request = AuthReqDTO.KakaoLogin.builder()
                .code("authorization-code")
                .state("google-state")
                .build();
        when(authRedisRepository.getAndDelete("oauth:state:google-state"))
                .thenReturn(SocialType.GOOGLE.name() + ":" + BROWSER_ID);

        AuthException exception = assertThrows(
                AuthException.class,
                () -> authCommandService.loginWithKakao(request, BROWSER_ID)
        );

        assertEquals(AuthErrorCode.INVALID_OAUTH_STATE, exception.getCode());
    }

    @Test
    void 구글_state를_소비하고_회원을_로그인한다() {
        AuthReqDTO.GoogleLogin request = AuthReqDTO.GoogleLogin.builder()
                .code("authorization-code")
                .state("issued-state")
                .build();
        Member member = mock(Member.class);

        when(authRedisRepository.getAndDelete("oauth:state:issued-state"))
                .thenReturn(SocialType.GOOGLE.name() + ":" + BROWSER_ID);
        when(googleOAuthClient.getUserId(request.code()))
                .thenReturn("google-user-123");
        when(memberCommandService.findOrCreateSocialMember(SocialType.GOOGLE, "google-user-123"))
                .thenReturn(member);
        stubIssuedTokens(8L);
        when(member.getId()).thenReturn(8L);
        when(member.isOnboardingCompleted()).thenReturn(false);

        AuthSession response = authCommandService.loginWithGoogle(request, BROWSER_ID);

        assertEquals(8L, response.member().memberId());
        assertFalse(response.member().onboardingCompleted());
        verify(authRedisRepository).getAndDelete("oauth:state:issued-state");
    }

    @Test
    void 카카오용_state로_구글_로그인을_요청하면_거부한다() {
        AuthReqDTO.GoogleLogin request = AuthReqDTO.GoogleLogin.builder()
                .code("authorization-code")
                .state("kakao-state")
                .build();
        when(authRedisRepository.getAndDelete("oauth:state:kakao-state"))
                .thenReturn(SocialType.KAKAO.name() + ":" + BROWSER_ID);

        AuthException exception = assertThrows(
                AuthException.class,
                () -> authCommandService.loginWithGoogle(request, BROWSER_ID)
        );

        assertEquals(AuthErrorCode.INVALID_OAUTH_STATE, exception.getCode());
    }

    @Test
    void 다른_브라우저에서_발급된_state로_로그인하면_거부한다() {
        AuthReqDTO.KakaoLogin request = AuthReqDTO.KakaoLogin.builder()
                .code("authorization-code")
                .state("issued-state")
                .build();
        when(authRedisRepository.getAndDelete("oauth:state:issued-state"))
                .thenReturn(SocialType.KAKAO.name() + ":" + BROWSER_ID);

        AuthException exception = assertThrows(
                AuthException.class,
                () -> authCommandService.loginWithKakao(request, "another-browser")
        );

        assertEquals(AuthErrorCode.INVALID_OAUTH_STATE, exception.getCode());
        verify(kakaoOAuthClient, never()).getUserId(request.code());
    }

    @Test
    void 브라우저_쿠키가_없으면_state를_소비하지_않고_거부한다() {
        AuthReqDTO.KakaoLogin request = AuthReqDTO.KakaoLogin.builder()
                .code("authorization-code")
                .state("issued-state")
                .build();

        AuthException exception = assertThrows(
                AuthException.class,
                () -> authCommandService.loginWithKakao(request, null)
        );

        assertEquals(AuthErrorCode.INVALID_OAUTH_STATE, exception.getCode());
        verify(authRedisRepository, never()).getAndDelete("oauth:state:issued-state");
    }

    private void stubIssuedTokens(Long memberId) {
        when(jwtTokenProvider.issueAccessToken(memberId))
                .thenReturn(new IssuedJwt("access-token", "access-id", Duration.ofMinutes(5)));
        when(jwtTokenProvider.issueRefreshToken(memberId))
                .thenReturn(new IssuedJwt("refresh-token", "refresh-id", Duration.ofDays(14)));
    }

    @Test
    void 재발급시_기존_Refresh_Token을_원자적으로_새_식별자로_교체한다() {
        when(jwtTokenProvider.decode("previous-refresh"))
                .thenReturn(refreshJwt("previous-refresh", "previous-id"));
        when(jwtTokenProvider.issueAccessToken(27L))
                .thenReturn(new IssuedJwt("next-access", "next-access-id", Duration.ofMinutes(5)));
        when(jwtTokenProvider.issueRefreshToken(27L))
                .thenReturn(new IssuedJwt("next-refresh", "next-refresh-id", Duration.ofDays(14)));
        when(authRedisRepository.rotateRefreshToken(
                "previous-id", "next-refresh-id", 27L, Duration.ofDays(14)
        )).thenReturn(true);

        AuthTokenPair tokens = authCommandService.reissue("previous-refresh");

        assertEquals("next-access", tokens.accessToken().value());
        assertEquals("next-refresh", tokens.refreshToken().value());
        verify(authRedisRepository).rotateRefreshToken(
                "previous-id", "next-refresh-id", 27L, Duration.ofDays(14)
        );
    }

    @Test
    void 이미_사용되었거나_Redis에_없는_Refresh_Token은_재발급하지_않는다() {
        when(jwtTokenProvider.decode("previous-refresh"))
                .thenReturn(refreshJwt("previous-refresh", "previous-id"));
        when(jwtTokenProvider.issueAccessToken(27L))
                .thenReturn(new IssuedJwt("next-access", "next-access-id", Duration.ofMinutes(5)));
        when(jwtTokenProvider.issueRefreshToken(27L))
                .thenReturn(new IssuedJwt("next-refresh", "next-refresh-id", Duration.ofDays(14)));
        when(authRedisRepository.rotateRefreshToken(
                "previous-id", "next-refresh-id", 27L, Duration.ofDays(14)
        )).thenReturn(false);

        AuthException exception = assertThrows(
                AuthException.class,
                () -> authCommandService.reissue("previous-refresh")
        );

        assertEquals(AuthErrorCode.INVALID_REFRESH_TOKEN, exception.getCode());
    }

    private Jwt refreshJwt(String token, String tokenId) {
        Instant now = Instant.parse("2026-09-24T00:00:00Z");
        return Jwt.withTokenValue(token)
                .header("alg", "HS256")
                .issuer("https://moeasy")
                .subject("27")
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofDays(14)))
                .jti(tokenId)
                .claim(JwtTokenProvider.TOKEN_TYPE_CLAIM, JwtTokenProvider.REFRESH_TOKEN_TYPE)
                .build();
    }
}
