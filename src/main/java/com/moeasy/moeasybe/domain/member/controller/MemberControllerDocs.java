package com.moeasy.moeasybe.domain.member.controller;

import com.moeasy.moeasybe.domain.member.dto.request.MemberReqDTO;
import com.moeasy.moeasybe.domain.member.dto.response.MemberResDTO;
import com.moeasy.moeasybe.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;

@Tag(name = "Member", description = "회원 API")
public interface MemberControllerDocs {

    @Operation(summary = "닉네임 중복 확인", description = "2~12자 닉네임의 사용 가능 여부를 조회합니다. "
            + "중복이면 HTTP 200과 available=false를 반환합니다. 삭제된 회원의 닉네임도 중복으로 판단합니다. "
            + "조회는 닉네임을 예약하지 않으므로 최종 온보딩 저장 시 중복을 다시 검사해야 합니다. "
            + "로그인 후 moeasy_access_token 쿠키가 필요하며, GET 요청에는 CSRF 헤더가 필요하지 않습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", useReturnTypeSchema = true, description = "닉네임 사용 가능 여부 조회 성공",
                    content = @Content(mediaType = "application/json", examples = {
                            @ExampleObject(name = "사용 가능", value = """
                                    {"isSuccess":true,"code":"MEMBER200_1",
                                     "message":"닉네임 사용 가능 여부 조회에 성공했습니다.","result":{"available":true}}
                                    """),
                            @ExampleObject(name = "중복", value = """
                                    {"isSuccess":true,"code":"MEMBER200_1",
                                     "message":"닉네임 사용 가능 여부 조회에 성공했습니다.","result":{"available":false}}
                                    """)
                    })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "닉네임 누락, 공백 또는 길이 검증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {"isSuccess":false,"code":"VALID400_1","message":"검증에 실패했습니다.",
                                     "result":{"nickname":["닉네임은 2자 이상 12자 이하여야 합니다."]}}
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "Access Token 쿠키가 없거나 유효하지 않은 경우",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {"isSuccess":false,"code":"AUTH401_1","message":"인증이 필요합니다.","result":null}
                                    """)))
    })
    ResponseEntity<ApiResponse<MemberResDTO.NicknameAvailability>> getNicknameAvailability(
            @ParameterObject MemberReqDTO.NicknameAvailability request
    );
    @Operation(summary = "온보딩 완료", description = "로그인한 회원의 닉네임, 한 줄 소개, 주 활동 지역과 관심사를 한 번에 저장합니다. "
            + "닉네임은 2~12자, 한 줄 소개는 선택 입력이며 최대 60자입니다. "
            + "지역 목록 조회 API에서 받은 regionCode와 중복 없는 관심사 코드 3~5개를 전달합니다. "
            + "관심사 코드는 RUNNING, HIKING, STUDY, READING, PHOTOGRAPHY, FOOD, BOARD_GAME, TRAVEL, VOLUNTEERING, DEVELOPMENT입니다. "
            + "삭제된 지역·관심사는 선택할 수 없습니다. 닉네임은 저장 시 다시 검증하며 중복이면 409입니다. "
            + "완료된 회원의 재요청은 409이며 모든 변경은 하나의 트랜잭션으로 처리됩니다. "
            + "moeasy_access_token 쿠키와 XSRF-TOKEN 쿠키, GET /api/v1/auth/csrf 응답의 token을 담은 X-XSRF-TOKEN 헤더가 필요합니다.",
            parameters = @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true,
                    description = "CSRF 발급 API 응답의 token", schema = @Schema(type = "string")))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", useReturnTypeSchema = true, description = "온보딩 완료",
                    content = @Content(mediaType = "application/json", examples = {
                            @ExampleObject(name = "완료", value = """
                                    {"isSuccess":true,"code":"MEMBER200_2","message":"온보딩을 완료했습니다.","result":{"memberId":1,"onboardingCompleted":true}}
                                    """)
                    })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "요청 검증 실패 또는 선택할 수 없는 지역·관심사",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class), examples = {
                            @ExampleObject(name = "필드 검증", value = """
                                    {"isSuccess":false,"code":"VALID400_1","message":"검증에 실패했습니다.","result":{"nickname":["닉네임은 2자 이상 12자 이하여야 합니다."]}}
                                    """),
                            @ExampleObject(name = "지역 오류", value = """
                                    {"isSuccess":false,"code":"MEMBER400_1","message":"선택할 수 없는 지역입니다.","result":null}
                                    """),
                            @ExampleObject(name = "관심사 오류", value = """
                                    {"isSuccess":false,"code":"MEMBER400_2","message":"관심사는 선택 가능한 항목으로 중복 없이 선택해야 합니다.","result":null}
                                    """),
                            @ExampleObject(name = "JSON 오류", value = """
                                    {"isSuccess":false,"code":"COMMON400_1","message":"잘못된 요청입니다.","result":null}
                                    """)
                    })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증 쿠키 누락 또는 유효하지 않은 Access Token",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class), examples = {
                            @ExampleObject(name = "인증 오류", value = """
                                    {"isSuccess":false,"code":"AUTH401_1","message":"인증이 필요합니다.","result":null}
                                    """)
                    })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "CSRF 검증 실패 또는 이용할 수 없는 회원",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class), examples = {
                            @ExampleObject(name = "CSRF 오류", value = """
                                    {"isSuccess":false,"code":"AUTH403_1","message":"요청이 거부되었습니다.","result":null}
                                    """),
                            @ExampleObject(name = "회원 상태 오류", value = """
                                    {"isSuccess":false,"code":"MEMBER403_1","message":"온보딩을 진행할 수 없는 회원입니다.","result":null}
                                    """)
                    })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "회원 없음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class), examples = {
                            @ExampleObject(name = "회원 없음", value = """
                                    {"isSuccess":false,"code":"MEMBER404_1","message":"회원을 찾을 수 없습니다.","result":null}
                                    """)
                    })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "닉네임 중복 또는 이미 완료한 온보딩",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class), examples = {
                            @ExampleObject(name = "닉네임 중복", value = """
                                    {"isSuccess":false,"code":"MEMBER409_1","message":"이미 사용 중인 닉네임입니다.","result":null}
                                    """),
                            @ExampleObject(name = "이미 완료", value = """
                                    {"isSuccess":false,"code":"MEMBER409_2","message":"이미 온보딩을 완료했습니다.","result":null}
                                    """)
                    }))
    })
    ResponseEntity<ApiResponse<MemberResDTO.Onboarding>> completeOnboarding(
            @Parameter(hidden = true) Long memberId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = MemberReqDTO.Onboarding.class),
                            examples = @ExampleObject(value = """
                                    {"nickname":"모이지","statusMessage":"함께 달릴 친구를 찾고 있어요",
                                     "regionCode":"11680","categoryCodes":["RUNNING","STUDY","READING"]}
                                    """)))
            MemberReqDTO.Onboarding request
    );

}
