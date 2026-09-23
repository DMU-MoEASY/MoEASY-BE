package com.moeasy.moeasybe.domain.auth.controller;

import com.moeasy.moeasybe.domain.auth.dto.request.AuthReqDTO;
import com.moeasy.moeasybe.domain.auth.dto.response.AuthResDTO;
import com.moeasy.moeasybe.domain.auth.exception.code.AuthSuccessCode;
import com.moeasy.moeasybe.domain.auth.config.AuthProperties;
import com.moeasy.moeasybe.domain.auth.service.command.AuthCommandService;
import com.moeasy.moeasybe.global.apiPayload.ApiResponse;
import com.moeasy.moeasybe.global.security.util.CookieUtil;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
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

    private final AuthCommandService authCommandService;
    private final AuthProperties authProperties;
    private final CookieUtil cookieUtil;

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
        AuthResDTO.SocialLogin response = authCommandService.loginWithKakao(request, browserId);

        return ResponseEntity.status(AuthSuccessCode.KAKAO_LOGIN_SUCCEEDED.getStatus())
                .body(ApiResponse.onSuccess(AuthSuccessCode.KAKAO_LOGIN_SUCCEEDED, response));
    }

    @Override
    @PostMapping("/oauth/google")
    public ResponseEntity<ApiResponse<AuthResDTO.SocialLogin>> loginWithGoogle(
            @Valid @RequestBody AuthReqDTO.GoogleLogin request,
            @CookieValue(value = OAUTH_BROWSER_COOKIE, required = false) String browserId
    ) {
        AuthResDTO.SocialLogin response = authCommandService.loginWithGoogle(request, browserId);

        return ResponseEntity.status(AuthSuccessCode.GOOGLE_LOGIN_SUCCEEDED.getStatus())
                .body(ApiResponse.onSuccess(AuthSuccessCode.GOOGLE_LOGIN_SUCCEEDED, response));
    }
}
