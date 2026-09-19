package com.moeasy.moeasybe.domain.auth.service.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moeasy.moeasybe.domain.auth.client.KakaoOAuthClient;
import com.moeasy.moeasybe.domain.auth.client.GoogleOAuthClient;
import com.moeasy.moeasybe.domain.auth.code.AuthErrorCode;
import com.moeasy.moeasybe.domain.auth.config.AuthProperties;
import com.moeasy.moeasybe.domain.auth.dto.request.AuthReqDTO;
import com.moeasy.moeasybe.domain.auth.dto.response.AuthResDTO;
import com.moeasy.moeasybe.domain.auth.exception.AuthException;
import com.moeasy.moeasybe.domain.auth.repository.AuthRedisRepository;
import com.moeasy.moeasybe.domain.member.entity.Member;
import com.moeasy.moeasybe.domain.member.entity.SocialType;
import com.moeasy.moeasybe.domain.member.service.command.MemberCommandService;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthCommandServiceTest {

    private static final Duration STATE_EXPIRATION = Duration.ofMinutes(5);

    @Mock
    private AuthRedisRepository authRedisRepository;

    @Mock
    private KakaoOAuthClient kakaoOAuthClient;

    @Mock
    private GoogleOAuthClient googleOAuthClient;

    @Mock
    private MemberCommandService memberCommandService;

    private AuthCommandService authCommandService;

    @BeforeEach
    void setUp() {
        authCommandService = new AuthCommandService(
                authRedisRepository,
                new AuthProperties(STATE_EXPIRATION),
                kakaoOAuthClient,
                googleOAuthClient,
                memberCommandService
        );
    }

    @Test
    void 카카오_state를_발급하고_Redis에_저장한다() {
        AuthResDTO.IssueState response = authCommandService.issueState("KAKAO");

        ArgumentCaptor<String> stateCaptor = ArgumentCaptor.forClass(String.class);
        verify(authRedisRepository).save(
                stateCaptor.capture(),
                eq(SocialType.KAKAO.name()),
                eq(STATE_EXPIRATION)
        );
        assertEquals("oauth:state:" + response.state(), stateCaptor.getValue());
        assertFalse(response.state().isBlank());
    }

    @Test
    void 지원하지_않는_제공자는_인증_예외를_던진다() {
        AuthException exception = assertThrows(
                AuthException.class,
                () -> authCommandService.issueState("NAVER")
        );

        assertEquals(AuthErrorCode.UNSUPPORTED_OAUTH_PROVIDER, exception.getCode());
    }

    @Test
    void 카카오_state를_소비하고_기존_회원을_로그인한다() {
        AuthReqDTO.KakaoLogin request = new AuthReqDTO.KakaoLogin(
                "authorization-code",
                "issued-state",
                "https://dev.moeasy.kr/oauth/kakao/callback"
        );
        Member member = mock(Member.class);

        when(authRedisRepository.getAndDelete("oauth:state:issued-state"))
                .thenReturn(SocialType.KAKAO.name());
        when(kakaoOAuthClient.getUserId(request.code(), request.redirectUri()))
                .thenReturn("123456789");
        when(memberCommandService.findOrCreateSocialMember(SocialType.KAKAO, "123456789"))
                .thenReturn(member);
        when(member.getId()).thenReturn(7L);
        when(member.isOnboardingCompleted()).thenReturn(true);

        AuthResDTO.SocialLogin response = authCommandService.loginWithKakao(request);

        assertEquals(7L, response.memberId());
        assertTrue(response.onboardingCompleted());
        verify(authRedisRepository).getAndDelete("oauth:state:issued-state");
    }

    @Test
    void 구글용_state로_카카오_로그인을_요청하면_거부한다() {
        AuthReqDTO.KakaoLogin request = new AuthReqDTO.KakaoLogin(
                "authorization-code",
                "google-state",
                "https://dev.moeasy.kr/oauth/kakao/callback"
        );
        when(authRedisRepository.getAndDelete("oauth:state:google-state"))
                .thenReturn(SocialType.GOOGLE.name());

        AuthException exception = assertThrows(
                AuthException.class,
                () -> authCommandService.loginWithKakao(request)
        );

        assertEquals(AuthErrorCode.INVALID_OAUTH_STATE, exception.getCode());
    }

    @Test
    void 구글_state를_소비하고_회원을_로그인한다() {
        AuthReqDTO.GoogleLogin request = new AuthReqDTO.GoogleLogin(
                "authorization-code",
                "issued-state",
                "https://dev.moeasy.kr/oauth/google/callback"
        );
        Member member = mock(Member.class);

        when(authRedisRepository.getAndDelete("oauth:state:issued-state"))
                .thenReturn(SocialType.GOOGLE.name());
        when(googleOAuthClient.getUserId(request.code(), request.redirectUri()))
                .thenReturn("google-user-123");
        when(memberCommandService.findOrCreateSocialMember(SocialType.GOOGLE, "google-user-123"))
                .thenReturn(member);
        when(member.getId()).thenReturn(8L);
        when(member.isOnboardingCompleted()).thenReturn(false);

        AuthResDTO.SocialLogin response = authCommandService.loginWithGoogle(request);

        assertEquals(8L, response.memberId());
        assertFalse(response.onboardingCompleted());
        verify(authRedisRepository).getAndDelete("oauth:state:issued-state");
    }

    @Test
    void 카카오용_state로_구글_로그인을_요청하면_거부한다() {
        AuthReqDTO.GoogleLogin request = new AuthReqDTO.GoogleLogin(
                "authorization-code",
                "kakao-state",
                "https://dev.moeasy.kr/oauth/google/callback"
        );
        when(authRedisRepository.getAndDelete("oauth:state:kakao-state"))
                .thenReturn(SocialType.KAKAO.name());

        AuthException exception = assertThrows(
                AuthException.class,
                () -> authCommandService.loginWithGoogle(request)
        );

        assertEquals(AuthErrorCode.INVALID_OAUTH_STATE, exception.getCode());
    }
}
