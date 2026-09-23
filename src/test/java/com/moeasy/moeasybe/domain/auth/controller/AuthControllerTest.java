package com.moeasy.moeasybe.domain.auth.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moeasy.moeasybe.domain.auth.config.AuthProperties;
import com.moeasy.moeasybe.domain.auth.dto.request.AuthReqDTO;
import com.moeasy.moeasybe.domain.auth.dto.response.AuthResDTO;
import com.moeasy.moeasybe.domain.auth.service.command.AuthCommandService;
import com.moeasy.moeasybe.global.config.CookieProperties;
import com.moeasy.moeasybe.global.security.util.CookieUtil;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthCommandService authCommandService;

    private AuthController authController;

    @BeforeEach
    void setUp() {
        authController = new AuthController(
                authCommandService,
                new AuthProperties(Duration.ofMinutes(5)),
                new CookieUtil(new CookieProperties(false, "Lax"))
        );
    }

    @Test
    void state_발급시_브라우저_식별_쿠키를_설정한다() {
        when(authCommandService.issueState(eq("KAKAO"), anyString()))
                .thenReturn(new AuthResDTO.IssueState("issued-state"));

        var response = authController.issueState(new AuthReqDTO.IssueState("KAKAO"), null);

        ArgumentCaptor<String> browserId = ArgumentCaptor.forClass(String.class);
        verify(authCommandService).issueState(eq("KAKAO"), browserId.capture());
        String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);
        assertTrue(setCookie.startsWith(AuthController.OAUTH_BROWSER_COOKIE + "=" + browserId.getValue()));
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("SameSite=Lax"));
        assertTrue(setCookie.contains("Path=/api/v1/auth/oauth"));
        assertTrue(setCookie.contains("Max-Age=300"));
    }

    @Test
    void 기존_브라우저_쿠키가_있으면_같은_식별값을_사용한다() {
        when(authCommandService.issueState("GOOGLE", "existing-browser"))
                .thenReturn(new AuthResDTO.IssueState("issued-state"));

        var response = authController.issueState(new AuthReqDTO.IssueState("GOOGLE"), "existing-browser");

        verify(authCommandService).issueState("GOOGLE", "existing-browser");
        assertTrue(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)
                .startsWith(AuthController.OAUTH_BROWSER_COOKIE + "=existing-browser"));
        assertEquals(201, response.getStatusCode().value());
    }

    @Test
    void 로그인_HTTP_요청의_쿠키를_서비스에_전달한다() throws Exception {
        when(authCommandService.loginWithKakao(any(AuthReqDTO.KakaoLogin.class), eq("browser-123")))
                .thenReturn(new AuthResDTO.SocialLogin(7L, true));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(authController).build();

        mockMvc.perform(post("/api/v1/auth/oauth/kakao")
                        .cookie(new Cookie(AuthController.OAUTH_BROWSER_COOKIE, "browser-123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "authorization-code",
                                  "state": "issued-state",
                                  "redirectUri": "https://dev.moeasy.kr/oauth/kakao/callback"
                                }
                                """))
                .andExpect(status().isOk());

        verify(authCommandService).loginWithKakao(any(AuthReqDTO.KakaoLogin.class), eq("browser-123"));
    }
}
