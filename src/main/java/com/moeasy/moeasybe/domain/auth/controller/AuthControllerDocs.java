package com.moeasy.moeasybe.domain.auth.controller;

import com.moeasy.moeasybe.domain.auth.dto.request.AuthReqDTO;
import com.moeasy.moeasybe.domain.auth.dto.response.AuthResDTO;
import com.moeasy.moeasybe.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

@Tag(name = "Auth", description = "인증/로그인 관련 API")
public interface AuthControllerDocs {

    @Operation(
            summary = "소셜 로그인 state 발급",
            description = "소셜 로그인 요청에 사용할 일회성 state를 발급합니다. "
                    + "provider에는 KAKAO 또는 GOOGLE을 전달해야 하며, 발급된 state는 5분 동안 Redis에 저장됩니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "state 발급 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "isSuccess": true,
                                              "code": "AUTH201_1",
                                              "message": "소셜 로그인 state를 발급했습니다.",
                                              "result": {
                                                "state": "N9a1JxD_Cnqezcvx0W86-RQj8Fsvf1mJqYp0r2LhK0w"
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "provider 누락 또는 지원하지 않는 제공자",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "provider 누락",
                                            value = """
                                                    {
                                                      "isSuccess": false,
                                                      "code": "VALID400_1",
                                                      "message": "검증에 실패했습니다.",
                                                      "result": {
                                                        "provider": ["provider는 필수입니다."]
                                                      }
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "지원하지 않는 제공자",
                                            value = """
                                                    {
                                                      "isSuccess": false,
                                                      "code": "AUTH400_1",
                                                      "message": "지원하지 않는 소셜 로그인 제공자입니다.",
                                                      "result": null
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503",
                    description = "Redis에 state를 저장하지 못함",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "isSuccess": false,
                                              "code": "AUTH503_1",
                                              "message": "소셜 로그인 요청을 준비할 수 없습니다.",
                                              "result": null
                                            }
                                            """
                            )
                    )
            )
    })
    ResponseEntity<ApiResponse<AuthResDTO.IssueState>> issueState(
            @RequestBody(
                    required = true,
                    content = @Content(
                            schema = @Schema(
                                    implementation = AuthReqDTO.IssueState.class,
                                    description = "provider는 KAKAO 또는 GOOGLE 중 하나여야 합니다."
                            ),
                            examples = @ExampleObject(value = """
                                    {
                                      "provider": "KAKAO"
                                    }
                                    """)
                    )
            )
            @Valid @org.springframework.web.bind.annotation.RequestBody AuthReqDTO.IssueState request
    );

    @Operation(
            summary = "카카오 로그인",
            description = "프론트엔드가 카카오에서 받은 인가 코드를 백엔드가 액세스 토큰으로 교환하고, "
                    + "카카오 사용자 ID를 기준으로 회원을 조회하거나 생성합니다. "
                    + "state는 발급 시 KAKAO로 저장된 값이어야 하며 검증과 동시에 삭제되어 한 번만 사용할 수 있습니다. "
                    + "redirectUri는 카카오 인가 코드 요청에 사용한 값과 완전히 같아야 합니다. "
                    + "이번 API는 서비스 JWT를 발급하지 않습니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "카카오 로그인 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "isSuccess": true,
                                              "code": "AUTH200_1",
                                              "message": "카카오 로그인에 성공했습니다.",
                                              "result": {
                                                "memberId": 1,
                                                "onboardingCompleted": false
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "요청 본문 검증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "isSuccess": false,
                                              "code": "VALID400_1",
                                              "message": "검증에 실패했습니다.",
                                              "result": {
                                                "code": ["인가 코드는 필수입니다."]
                                              }
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "state가 유효하지 않거나 카카오 인증에 실패함",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "유효하지 않은 state",
                                            value = """
                                                    {
                                                      "isSuccess": false,
                                                      "code": "AUTH401_2",
                                                      "message": "유효하지 않거나 만료된 소셜 로그인 요청입니다.",
                                                      "result": null
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "카카오 인증 실패",
                                            value = """
                                                    {
                                                      "isSuccess": false,
                                                      "code": "AUTH401_3",
                                                      "message": "카카오 인증에 실패했습니다.",
                                                      "result": null
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "502",
                    description = "카카오 서버 통신 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "isSuccess": false,
                                              "code": "AUTH502_1",
                                              "message": "카카오 로그인 서버와 통신하지 못했습니다.",
                                              "result": null
                                            }
                                            """
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503",
                    description = "Redis에서 state 확인 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "isSuccess": false,
                                              "code": "AUTH503_2",
                                              "message": "소셜 로그인 요청을 확인할 수 없습니다.",
                                              "result": null
                                            }
                                            """
                            )
                    )
            )
    })
    ResponseEntity<ApiResponse<AuthResDTO.SocialLogin>> loginWithKakao(
            @RequestBody(
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = AuthReqDTO.KakaoLogin.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "SplxlOBeZQQYbYS6WxSbIA",
                                      "state": "N9a1JxD_Cnqezcvx0W86-RQj8Fsvf1mJqYp0r2LhK0w",
                                      "redirectUri": "https://dev.moeasy.kr/oauth/kakao/callback"
                                    }
                                    """)
                    )
            )
            @Valid @org.springframework.web.bind.annotation.RequestBody AuthReqDTO.KakaoLogin request
    );
}
