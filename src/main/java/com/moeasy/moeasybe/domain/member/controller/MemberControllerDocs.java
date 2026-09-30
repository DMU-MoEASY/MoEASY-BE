package com.moeasy.moeasybe.domain.member.controller;

import com.moeasy.moeasybe.domain.member.dto.request.MemberReqDTO;
import com.moeasy.moeasybe.domain.member.dto.response.MemberResDTO;
import com.moeasy.moeasybe.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
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
}
