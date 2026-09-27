package com.moeasy.moeasybe.domain.auth.controller;

import com.moeasy.moeasybe.domain.auth.config.AuthCookieNames;
import com.moeasy.moeasybe.domain.auth.dto.request.AuthReqDTO;
import com.moeasy.moeasybe.domain.auth.dto.response.AuthResDTO;
import com.moeasy.moeasybe.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;

@Tag(name = "Auth", description = "인증/로그인 관련 API")
public interface AuthControllerDocs {

    @Operation(
            summary = "CSRF 토큰 발급",
            description = "쿠키 기반 인증 요청에 사용할 CSRF 토큰을 발급합니다. "
                    + "응답의 token을 이후 상태 변경 요청의 X-XSRF-TOKEN 헤더에 전달해야 합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "CSRF 토큰 발급 성공. XSRF-TOKEN 쿠키도 설정됩니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "AUTH200_5",
                                      "message": "CSRF 토큰을 발급했습니다.",
                                      "result": {
                                        "token": "csrf-token-value",
                                        "headerName": "X-XSRF-TOKEN"
                                      }
                                    }
                                    """)
                    )
            )
    })
    ResponseEntity<ApiResponse<AuthResDTO.Csrf>> getCsrfToken(
            @Parameter(hidden = true) CsrfToken csrfToken
    );

    @Operation(
            summary = "소셜 로그인 state 발급",
            parameters = @Parameter(
                    name = "X-XSRF-TOKEN",
                    in = ParameterIn.HEADER,
                    description = "GET /api/v1/auth/csrf 응답의 result.token 값",
                    required = true
            ),
            description = "소셜 로그인 요청에 사용할 일회성 state를 발급합니다. "
                    + "provider에는 KAKAO 또는 GOOGLE을 전달해야 하며, 발급된 state는 5분 동안 Redis에 저장됩니다. "
                    + "응답의 HttpOnly 쿠키는 로그인 요청을 시작한 브라우저를 식별하며, 로그인 요청 때 자동으로 전송되어야 합니다."
                    + " 먼저 GET /api/v1/auth/csrf로 CSRF 토큰을 받은 뒤, 이 POST 요청에 X-XSRF-TOKEN 헤더로 전달해야 합니다."
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
            @Valid @org.springframework.web.bind.annotation.RequestBody AuthReqDTO.IssueState request,
            @Parameter(hidden = true) String existingBrowserId
    );

    @Operation(
            summary = "카카오 로그인",
            parameters = @Parameter(
                    name = "X-XSRF-TOKEN",
                    in = ParameterIn.HEADER,
                    description = "GET /api/v1/auth/csrf 응답의 result.token 값",
                    required = true
            ),
            description = "프론트엔드가 카카오에서 받은 인가 코드를 백엔드가 액세스 토큰으로 교환하고, "
                    + "카카오 사용자 ID를 기준으로 회원을 조회하거나 생성합니다. "
                    + "state는 발급 시 KAKAO로 저장된 값이어야 하며 검증과 동시에 삭제되어 한 번만 사용할 수 있습니다. "
                    + "state 발급 응답의 HttpOnly 쿠키가 같은 브라우저에서 함께 전송되어야 합니다. "
                    + "POST 요청에는 GET /api/v1/auth/csrf 응답의 CSRF 토큰을 X-XSRF-TOKEN 헤더로 전달해야 합니다. "
                    + "토큰 교환에 사용하는 redirect_uri는 백엔드의 KAKAO_REDIRECT_URI 환경변수 값이며, "
                    + "카카오 인가 코드 요청에 사용한 redirect_uri와 완전히 같아야 합니다. "
                    + "로그인 성공 시 Access Token과 Refresh Token을 HttpOnly 쿠키로 설정합니다."
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
                                      "state": "N9a1JxD_Cnqezcvx0W86-RQj8Fsvf1mJqYp0r2LhK0w"
                                    }
                                    """)
                    )
            )
            @Valid @org.springframework.web.bind.annotation.RequestBody AuthReqDTO.KakaoLogin request,
            @Parameter(hidden = true) String browserId
    );

    @Operation(
            summary = "구글 로그인",
            parameters = @Parameter(
                    name = "X-XSRF-TOKEN",
                    in = ParameterIn.HEADER,
                    description = "GET /api/v1/auth/csrf 응답의 result.token 값",
                    required = true
            ),
            description = "프론트엔드가 구글에서 받은 인가 코드를 백엔드가 액세스 토큰으로 교환하고, "
                    + "구글 사용자 ID(sub)를 기준으로 회원을 조회하거나 생성합니다. "
                    + "state는 발급 시 GOOGLE로 저장된 값이어야 하며 검증과 동시에 삭제되어 한 번만 사용할 수 있습니다. "
                    + "state 발급 응답의 HttpOnly 쿠키가 같은 브라우저에서 함께 전송되어야 합니다. "
                    + "POST 요청에는 GET /api/v1/auth/csrf 응답의 CSRF 토큰을 X-XSRF-TOKEN 헤더로 전달해야 합니다. "
                    + "토큰 교환에 사용하는 redirect_uri는 백엔드의 GOOGLE_REDIRECT_URI 환경변수 값이며, "
                    + "구글 인가 코드 요청에 사용한 redirect_uri와 완전히 같아야 합니다. "
                    + "로그인 성공 시 Access Token과 Refresh Token을 HttpOnly 쿠키로 설정합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "구글 로그인 성공",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "isSuccess": true,
                                              "code": "AUTH200_2",
                                              "message": "구글 로그인에 성공했습니다.",
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
                    description = "state가 유효하지 않거나 구글 인증에 실패함",
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
                                            name = "구글 인증 실패",
                                            value = """
                                                    {
                                                      "isSuccess": false,
                                                      "code": "AUTH401_4",
                                                      "message": "구글 인증에 실패했습니다.",
                                                      "result": null
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "502",
                    description = "구글 서버 통신 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "isSuccess": false,
                                              "code": "AUTH502_2",
                                              "message": "구글 로그인 서버와 통신하지 못했습니다.",
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
    ResponseEntity<ApiResponse<AuthResDTO.SocialLogin>> loginWithGoogle(
            @RequestBody(
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = AuthReqDTO.GoogleLogin.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "code": "4/0AcvDMr...",
                                      "state": "N9a1JxD_Cnqezcvx0W86-RQj8Fsvf1mJqYp0r2LhK0w"
                                    }
                                    """)
                    )
            )
            @Valid @org.springframework.web.bind.annotation.RequestBody AuthReqDTO.GoogleLogin request,
            @Parameter(hidden = true) String browserId
    );

    @Operation(
            summary = "토큰 재발급",
            parameters = @Parameter(
                    name = "X-XSRF-TOKEN",
                    in = ParameterIn.HEADER,
                    description = "GET /api/v1/auth/csrf 응답의 result.token 값",
                    required = true
            ),
            description = "Refresh Token 쿠키를 검증하고 Redis에 저장된 토큰 식별 정보를 교체한 뒤, "
                    + "새 Access Token과 Refresh Token을 HttpOnly 쿠키로 설정합니다. "
                    + "요청에는 X-XSRF-TOKEN 헤더를 포함해야 합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Access Token과 Refresh Token 재발급 성공. 두 토큰은 Set-Cookie 응답 헤더로 전달됩니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "AUTH200_3",
                                      "message": "토큰을 재발급했습니다.",
                                      "result": null
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Refresh Token이 없거나 만료·폐기됨",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "AUTH401_5",
                                      "message": "유효하지 않거나 만료된 Refresh Token입니다.",
                                      "result": null
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "CSRF 토큰 검증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "AUTH403_1",
                                      "message": "요청이 거부되었습니다.",
                                      "result": null
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503",
                    description = "Redis 토큰 정보 처리 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "AUTH503_3",
                                      "message": "토큰 인증 정보를 처리할 수 없습니다.",
                                      "result": null
                                    }
                                    """)
                    )
            )
    })
    ResponseEntity<ApiResponse<Void>> reissue(
            @Parameter(hidden = true)
            @org.springframework.web.bind.annotation.CookieValue(
                    value = AuthCookieNames.REFRESH_TOKEN,
                    required = false
            ) String refreshToken
    );

    @Operation(
            summary = "로그아웃",
            parameters = @Parameter(
                    name = "X-XSRF-TOKEN",
                    in = ParameterIn.HEADER,
                    description = "GET /api/v1/auth/csrf 응답의 result.token 값",
                    required = true
            ),
            description = "Refresh Token을 Redis에서 폐기하고 Access Token 및 Refresh Token 쿠키를 만료 처리합니다. "
                    + "요청에는 X-XSRF-TOKEN 헤더를 포함해야 합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "로그아웃 성공. Access Token과 Refresh Token 쿠키가 만료되며, 응답 본문은 아래와 같습니다.",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": true,
                                      "code": "AUTH200_4",
                                      "message": "로그아웃에 성공했습니다.",
                                      "result": null
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "CSRF 토큰 검증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "AUTH403_1",
                                      "message": "요청이 거부되었습니다.",
                                      "result": null
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503",
                    description = "Redis에서 Refresh Token 삭제 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "isSuccess": false,
                                      "code": "AUTH503_3",
                                      "message": "토큰 인증 정보를 처리할 수 없습니다.",
                                      "result": null
                                    }
                                    """)
                    )
            )
    })
    ResponseEntity<ApiResponse<Void>> logout(
            @Parameter(hidden = true)
            @org.springframework.web.bind.annotation.CookieValue(
                    value = AuthCookieNames.REFRESH_TOKEN,
                    required = false
            ) String refreshToken
    );
}
