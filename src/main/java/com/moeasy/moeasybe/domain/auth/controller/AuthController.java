package com.moeasy.moeasybe.domain.auth.controller;

import com.moeasy.moeasybe.domain.auth.dto.request.AuthReqDTO;
import com.moeasy.moeasybe.domain.auth.dto.response.AuthResDTO;
import com.moeasy.moeasybe.domain.auth.exception.code.AuthSuccessCode;
import com.moeasy.moeasybe.domain.auth.config.AuthProperties;
import com.moeasy.moeasybe.domain.auth.config.AuthCookieNames;
import com.moeasy.moeasybe.domain.auth.converter.AuthConverter;
import com.moeasy.moeasybe.domain.auth.service.command.AuthCommandService;
import com.moeasy.moeasybe.domain.auth.service.result.AuthSession;
import com.moeasy.moeasybe.domain.auth.service.result.AuthTokenPair;
import com.moeasy.moeasybe.global.apiPayload.ApiResponse;
import com.moeasy.moeasybe.global.security.util.CookieUtil;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/")
@RequiredArgsConstructor
public class AuthController implements AuthControllerDocs {

    public static final String OAUTH_BROWSER_COOKIE = "moeasy_oauth_browser";
    private static final String OAUTH_COOKIE_PATH = "/api/v1/auth/oauth";
    private static final String ACCESS_TOKEN_COOKIE_PATH = "/api/v1";
    private static final String REFRESH_TOKEN_COOKIE_PATH = "/api/v1/auth";

    private final AuthCommandService authCommandService;
    private final AuthProperties authProperties;
    private final CookieUtil cookieUtil;

    @Override
    @GetMapping("/csrf")
    public ResponseEntity<ApiResponse<AuthResDTO.Csrf>> getCsrfToken(CsrfToken csrfToken) {
        AuthResDTO.Csrf response = AuthConverter.toCsrf(
                csrfToken.getToken(),
                csrfToken.getHeaderName()
        );

        return ResponseEntity.status(AuthSuccessCode.CSRF_TOKEN_ISSUED.getStatus())
                .body(ApiResponse.onSuccess(AuthSuccessCode.CSRF_TOKEN_ISSUED, response));
    }

    @Override
    @PostMapping("/oauth/states")
    public ResponseEntity<ApiResponse<AuthResDTO.IssueState>> issueState(
            @Valid @RequestBody AuthReqDTO.IssueState request,
            @CookieValue(value = OAUTH_BROWSER_COOKIE, required = false) String existingBrowserId
    ) {
        String browserId = existingBrowserId == null || existingBrowserId.isBlank()
                ? UUID.randomUUID().toString()
                : existingBrowserId;
        AuthResDTO.IssueState response = authCommandService.issueState(request.provider(), browserId);
        String setCookie = cookieUtil.createCookie(
                OAUTH_BROWSER_COOKIE,
                browserId,
                authProperties.expiration(),
                OAUTH_COOKIE_PATH
        ).toString();

        return ResponseEntity.status(AuthSuccessCode.OAUTH_STATE_ISSUED.getStatus())
                .header(HttpHeaders.SET_COOKIE, setCookie)
                .body(ApiResponse.onSuccess(AuthSuccessCode.OAUTH_STATE_ISSUED, response));
    }

    @Override
    @PostMapping("/oauth/kakao")
    public ResponseEntity<ApiResponse<AuthResDTO.SocialLogin>> loginWithKakao(
            @Valid @RequestBody AuthReqDTO.KakaoLogin request,
            @CookieValue(value = OAUTH_BROWSER_COOKIE, required = false) String browserId
    ) {
        AuthSession session = authCommandService.loginWithKakao(request, browserId);

        return ResponseEntity.status(AuthSuccessCode.KAKAO_LOGIN_SUCCEEDED.getStatus())
                .header(HttpHeaders.SET_COOKIE, createAccessTokenCookie(session.tokens()).toString())
                .header(HttpHeaders.SET_COOKIE, createRefreshTokenCookie(session.tokens()).toString())
                .body(ApiResponse.onSuccess(AuthSuccessCode.KAKAO_LOGIN_SUCCEEDED, session.member()));
    }

    @Override
    @PostMapping("/oauth/google")
    public ResponseEntity<ApiResponse<AuthResDTO.SocialLogin>> loginWithGoogle(
            @Valid @RequestBody AuthReqDTO.GoogleLogin request,
            @CookieValue(value = OAUTH_BROWSER_COOKIE, required = false) String browserId
    ) {
        AuthSession session = authCommandService.loginWithGoogle(request, browserId);

        return ResponseEntity.status(AuthSuccessCode.GOOGLE_LOGIN_SUCCEEDED.getStatus())
                .header(HttpHeaders.SET_COOKIE, createAccessTokenCookie(session.tokens()).toString())
                .header(HttpHeaders.SET_COOKIE, createRefreshTokenCookie(session.tokens()).toString())
                .body(ApiResponse.onSuccess(AuthSuccessCode.GOOGLE_LOGIN_SUCCEEDED, session.member()));
    }

    @Override
    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<Void>> reissue(
            @CookieValue(value = AuthCookieNames.REFRESH_TOKEN, required = false) String refreshToken
    ) {
        AuthTokenPair tokens = authCommandService.reissue(refreshToken);

        return ResponseEntity.status(AuthSuccessCode.TOKEN_REISSUED.getStatus())
                .header(HttpHeaders.SET_COOKIE, createAccessTokenCookie(tokens).toString())
                .header(HttpHeaders.SET_COOKIE, createRefreshTokenCookie(tokens).toString())
                .body(ApiResponse.onSuccess(AuthSuccessCode.TOKEN_REISSUED, null));
    }

    @Override
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(value = AuthCookieNames.REFRESH_TOKEN, required = false) String refreshToken
    ) {
        authCommandService.logout(refreshToken);

        return ResponseEntity.status(AuthSuccessCode.LOGOUT_SUCCEEDED.getStatus())
                .header(HttpHeaders.SET_COOKIE, cookieUtil.expireCookie(
                        AuthCookieNames.ACCESS_TOKEN,
                        ACCESS_TOKEN_COOKIE_PATH
                ).toString())
                .header(HttpHeaders.SET_COOKIE, cookieUtil.expireCookie(
                        AuthCookieNames.REFRESH_TOKEN,
                        REFRESH_TOKEN_COOKIE_PATH
                ).toString())
                .body(ApiResponse.onSuccess(AuthSuccessCode.LOGOUT_SUCCEEDED, null));
    }

    private ResponseCookie createAccessTokenCookie(AuthTokenPair tokens) {
        return cookieUtil.createCookie(
                AuthCookieNames.ACCESS_TOKEN,
                tokens.accessToken().value(),
                tokens.accessToken().expiration(),
                ACCESS_TOKEN_COOKIE_PATH
        );
    }

    private ResponseCookie createRefreshTokenCookie(AuthTokenPair tokens) {
        return cookieUtil.createCookie(
                AuthCookieNames.REFRESH_TOKEN,
                tokens.refreshToken().value(),
                tokens.refreshToken().expiration(),
                REFRESH_TOKEN_COOKIE_PATH
        );
    }
}
