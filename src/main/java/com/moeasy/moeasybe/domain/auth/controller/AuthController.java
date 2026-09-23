package com.moeasy.moeasybe.domain.auth.controller;

import com.moeasy.moeasybe.domain.auth.dto.request.AuthReqDTO;
import com.moeasy.moeasybe.domain.auth.dto.response.AuthResDTO;
import com.moeasy.moeasybe.domain.auth.code.AuthSuccessCode;
import com.moeasy.moeasybe.domain.auth.service.command.AuthCommandService;
import com.moeasy.moeasybe.global.apiPayload.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/")
@RequiredArgsConstructor
public class AuthController implements AuthControllerDocs {

    private final AuthCommandService authCommandService;

    @Override
    @PostMapping("/oauth/states")
    public ResponseEntity<ApiResponse<AuthResDTO.IssueState>> issueState(
            @Valid @RequestBody AuthReqDTO.IssueState request
    ) {
        AuthResDTO.IssueState response = authCommandService.issueState(
                request.provider(),
                request.correlationId()
        );

        return ResponseEntity.status(AuthSuccessCode.OAUTH_STATE_ISSUED.getStatus())
                .body(ApiResponse.onSuccess(AuthSuccessCode.OAUTH_STATE_ISSUED, response));
    }

    @Override
    @PostMapping("/oauth/kakao")
    public ResponseEntity<ApiResponse<AuthResDTO.SocialLogin>> loginWithKakao(
            @Valid @RequestBody AuthReqDTO.KakaoLogin request
    ) {
        AuthResDTO.SocialLogin response = authCommandService.loginWithKakao(request);

        return ResponseEntity.status(AuthSuccessCode.KAKAO_LOGIN_SUCCEEDED.getStatus())
                .body(ApiResponse.onSuccess(AuthSuccessCode.KAKAO_LOGIN_SUCCEEDED, response));
    }

    @Override
    @PostMapping("/oauth/google")
    public ResponseEntity<ApiResponse<AuthResDTO.SocialLogin>> loginWithGoogle(
            @Valid @RequestBody AuthReqDTO.GoogleLogin request
    ) {
        AuthResDTO.SocialLogin response = authCommandService.loginWithGoogle(request);

        return ResponseEntity.status(AuthSuccessCode.GOOGLE_LOGIN_SUCCEEDED.getStatus())
                .body(ApiResponse.onSuccess(AuthSuccessCode.GOOGLE_LOGIN_SUCCEEDED, response));
    }
}
