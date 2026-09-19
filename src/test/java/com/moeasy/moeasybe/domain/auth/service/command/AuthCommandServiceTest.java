package com.moeasy.moeasybe.domain.auth.service.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.moeasy.moeasybe.domain.auth.code.AuthErrorCode;
import com.moeasy.moeasybe.domain.auth.config.AuthProperties;
import com.moeasy.moeasybe.domain.auth.dto.response.AuthResDTO;
import com.moeasy.moeasybe.domain.auth.exception.AuthException;
import com.moeasy.moeasybe.domain.auth.repository.AuthRedisRepository;
import com.moeasy.moeasybe.domain.member.entity.SocialType;
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

    private AuthCommandService authCommandService;

    @BeforeEach
    void setUp() {
        authCommandService = new AuthCommandService(
                authRedisRepository,
                new AuthProperties(STATE_EXPIRATION)
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
}
