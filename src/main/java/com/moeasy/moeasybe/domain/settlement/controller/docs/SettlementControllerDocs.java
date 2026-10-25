package com.moeasy.moeasybe.domain.settlement.controller.docs;

import com.moeasy.moeasybe.domain.settlement.dto.request.SettlementReqDTO;
import com.moeasy.moeasybe.domain.settlement.dto.response.SettlementResDTO;
import com.moeasy.moeasybe.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springdoc.core.annotations.ParameterObject;

@Tag(name = "Settlement", description = "회비·정산 API")
public interface SettlementControllerDocs {

    @Operation(summary = "정산 상세 조회",
            description = "모임의 활성 OWNER·MANAGER·MEMBER가 정산 정보와 전체 회원별 청구 내역을 조회합니다. "
                    + "정산 대상이 아니어도 조회할 수 있으며 폐쇄된 모임도 허용합니다. 삭제된 모임·정산은 조회할 수 없습니다. "
                    + "정산은 경로의 모임에 속해야 하며 다른 모임의 정산은 정산 없음으로 처리합니다. "
                    + "청구 항목은 groupMemberId 오름차순이며 탈퇴·강퇴·삭제된 대상도 포함합니다. "
                    + "생성자·대상자의 계정이 삭제되어도 저장된 닉네임을 그대로 표시합니다. "
                    + "amount는 저장된 최종 청구액이며 조회 시 재분할하지 않습니다. "
                    + "moeasy_access_token 쿠키가 필요하며 GET 요청이므로 CSRF 헤더는 필요하지 않습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", useReturnTypeSchema = true, description = "정산 상세 조회 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"isSuccess":true,"code":"SETTLEMENT200_3","message":"정산 상세 조회에 성공했습니다.",
                             "result":{"settlementId":31,"groupId":1,"scheduleId":7,"title":"러닝 후 식사비",
                             "description":"식사비 정산입니다.","status":"OPEN","splitMode":"EQUAL","totalAmount":10000,
                             "targetCount":3,"dueAt":"2026-11-01T23:59:59","createdAt":"2026-10-20T14:00:00",
                             "createdBy":{"groupMemberId":11,"nickname":"정규"},
                             "items":[{"settlementItemId":101,"groupMemberId":11,"nickname":"정규","amount":3334,"status":"PENDING"},
                                      {"settlementItemId":102,"groupMemberId":12,"nickname":"민지","amount":3333,"status":"PENDING"},
                                      {"settlementItemId":103,"groupMemberId":13,"nickname":"도윤","amount":3333,"status":"PENDING"}]}}
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "경로 ID의 양수 검증 또는 숫자 변환 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class),
                            examples = {
                                    @ExampleObject(name = "양수 검증", value = """
                                            {"isSuccess":false,"code":"VALID400_1","message":"검증에 실패했습니다.",
                                             "result":{"settlementId":["정산 ID는 양수여야 합니다."]}}
                                            """),
                                    @ExampleObject(name = "숫자 변환", value = """
                                            {"isSuccess":false,"code":"COMMON400_1","message":"잘못된 요청입니다.","result":null}
                                            """)
                            })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증 쿠키 누락·만료 또는 유효하지 않은 Access Token",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {"isSuccess":false,"code":"AUTH401_1","message":"인증이 필요합니다.","result":null}
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "활성 모임 회원이 아님",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {"isSuccess":false,"code":"AUTH403_1","message":"요청이 거부되었습니다.","result":null}
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "모임 없음·삭제 또는 정산 없음·삭제·다른 모임의 정산",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class),
                            examples = {
                                    @ExampleObject(name = "모임 없음", value = """
                                            {"isSuccess":false,"code":"GROUP404_1","message":"모임을 찾을 수 없습니다.","result":null}
                                            """),
                                    @ExampleObject(name = "정산 없음", value = """
                                            {"isSuccess":false,"code":"SETTLEMENT404_1","message":"정산을 찾을 수 없습니다.","result":null}
                                            """)
                            }))
    })
    ResponseEntity<ApiResponse<SettlementResDTO.Detail>> getSettlement(
            @Positive(message = "모임 ID는 양수여야 합니다.")
            @Parameter(description = "정산이 속한 모임 ID (member_group.id)", required = true) Long groupId,
            @Positive(message = "정산 ID는 양수여야 합니다.")
            @Parameter(description = "조회할 정산 ID", required = true) Long settlementId,
            @Parameter(hidden = true) Long memberId
    );

    @Operation(summary = "정산 목록 조회",
            description = "해당 모임의 활성 OWNER·MANAGER·MEMBER가 정산 요약과 페이지 정보를 조회합니다. "
                    + "정산 대상이 아닌 회원도 조회할 수 있으며 폐쇄된 모임도 허용합니다. 삭제된 모임·정산은 제외합니다. "
                    + "page는 0부터 시작하고 기본값은 0, size는 1~100이며 기본값은 20입니다. "
                    + "page와 size의 곱이 2147483647을 초과하면 COMMON400_1로 거부합니다. "
                    + "status와 scheduleId는 선택 필터이며 함께 전달하면 모두 적용합니다. "
                    + "일정은 저장된 연결 ID로만 필터링합니다. 없는 일정·다른 모임의 일정은 빈 목록을 반환하고 "
                    + "삭제된 일정에 연결된 기존 정산은 조회합니다. "
                    + "생성 시각 내림차순, 같은 시각에서는 정산 ID 내림차순입니다. "
                    + "targetCount는 탈퇴·삭제 대상도 포함한 저장된 청구 항목 수입니다. "
                    + "moeasy_access_token 쿠키가 필요하며 GET 요청이므로 CSRF 헤더는 필요하지 않습니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", useReturnTypeSchema = true, description = "정산 목록 조회 성공",
                    content = @Content(mediaType = "application/json", examples = {
                            @ExampleObject(name = "정산 목록", value = """
                                    {"isSuccess":true,"code":"SETTLEMENT200_2","message":"정산 목록 조회에 성공했습니다.",
                                     "result":{"items":[{"settlementId":31,"title":"러닝 후 식사비","scheduleId":7,
                                     "status":"OPEN","splitMode":"EQUAL","totalAmount":10000,"targetCount":3,
                                     "dueAt":"2026-11-01T23:59:59","createdAt":"2026-10-20T14:00:00"}],
                                     "page":0,"size":20,"totalElements":1,"totalPages":1,"hasNext":false}}
                                    """),
                            @ExampleObject(name = "빈 목록", value = """
                                    {"isSuccess":true,"code":"SETTLEMENT200_2","message":"정산 목록 조회에 성공했습니다.",
                                     "result":{"items":[],"page":0,"size":20,"totalElements":0,"totalPages":0,"hasNext":false}}
                                    """)
                    })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "ID·페이지 범위, 조회 offset 범위 또는 쿼리 파라미터 형식 오류",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class),
                            examples = {
                                    @ExampleObject(name = "범위 검증", value = """
                                            {"isSuccess":false,"code":"VALID400_1","message":"검증에 실패했습니다.",
                                             "result":{"size":["페이지 크기는 100 이하여야 합니다."]}}
                                            """),
                                    @ExampleObject(name = "파라미터 형식", value = """
                                            {"isSuccess":false,"code":"COMMON400_1","message":"잘못된 요청입니다.","result":null}
                                            """)
                            })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증 쿠키 누락·만료 또는 유효하지 않은 Access Token",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {"isSuccess":false,"code":"AUTH401_1","message":"인증이 필요합니다.","result":null}
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "활성 모임 회원이 아님",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {"isSuccess":false,"code":"AUTH403_1","message":"요청이 거부되었습니다.","result":null}
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "모임 없음·삭제 (폐쇄된 모임은 조회 가능)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {"isSuccess":false,"code":"GROUP404_1","message":"모임을 찾을 수 없습니다.","result":null}
                                    """)))
    })
    ResponseEntity<ApiResponse<SettlementResDTO.Page>> getSettlements(
            @Positive(message = "모임 ID는 양수여야 합니다.")
            @Parameter(description = "조회할 모임 ID (member_group.id)", required = true) Long groupId,
            @Valid @ParameterObject SettlementReqDTO.Search request,
            @Parameter(hidden = true) Long memberId
    );

    @Operation(summary = "정산 생성",
            description = "폐쇄·삭제되지 않은 모임의 활성 OWNER 또는 MANAGER가 정산을 생성합니다. "
                    + "대상 ID는 회원 계정 ID가 아닌 group_member.id입니다. "
                    + "EQUAL은 totalAmount와 groupMemberIds, CUSTOM은 totalAmount와 memberAmounts를 전달합니다. "
                    + "사용하지 않는 대상·금액 목록은 생략하거나 null로 전달해야 합니다. "
                    + "균등 분할의 나머지는 대상 ID 오름차순으로 1원씩 배분하며 CUSTOM 합계는 총액과 같아야 합니다. "
                    + "scheduleId는 선택이며 같은 모임의 미삭제 일정이어야 합니다. 일정 참석 여부로 대상을 제한하지 않습니다. "
                    + "dueAt은 한국 시간 기준이며 생성 시각 이후여야 합니다. "
                    + "정산은 OPEN, 청구는 PENDING으로 함께 저장됩니다. 생성 요청의 중복 방지는 제공하지 않습니다. "
                    + "moeasy_access_token 쿠키와 XSRF-TOKEN 쿠키, X-XSRF-TOKEN 헤더가 필요합니다.",
            parameters = @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true,
                    description = "GET /api/v1/auth/csrf 응답의 result.token", schema = @Schema(type = "string")))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", useReturnTypeSchema = true, description = "정산 생성 성공",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"isSuccess":true,"code":"SETTLEMENT201_1","message":"정산 생성에 성공했습니다.","result":{"settlementId":1}}
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "필드·JSON 형식 또는 정산 업무 규칙 검증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class),
                            examples = {
                                    @ExampleObject(name = "필드 검증", value = """
                                            {"isSuccess":false,"code":"VALID400_1","message":"검증에 실패했습니다.","result":{"title":["정산 제목은 필수입니다."]}}
                                            """),
                                    @ExampleObject(name = "JSON 형식", value = """
                                            {"isSuccess":false,"code":"COMMON400_1","message":"잘못된 요청입니다.","result":null}
                                            """),
                                    @ExampleObject(name = "일정 선택", value = """
                                            {"isSuccess":false,"code":"SETTLEMENT400_1","message":"선택할 수 없는 일정입니다.","result":null}
                                            """),
                                    @ExampleObject(name = "대상 회원", value = """
                                            {"isSuccess":false,"code":"SETTLEMENT400_2","message":"정산 대상은 유효한 모임 회원으로 중복 없이 선택해야 합니다.","result":null}
                                            """),
                                    @ExampleObject(name = "조건부 필드", value = """
                                            {"isSuccess":false,"code":"SETTLEMENT400_3","message":"분할 방식에 맞는 대상·금액 필드를 전달해야 합니다.","result":null}
                                            """),
                                    @ExampleObject(name = "금액 검증", value = """
                                            {"isSuccess":false,"code":"SETTLEMENT400_4","message":"정산 금액 또는 회원별 청구액 합계가 올바르지 않습니다.","result":null}
                                            """),
                                    @ExampleObject(name = "납부 기한", value = """
                                            {"isSuccess":false,"code":"SETTLEMENT400_5","message":"납부 기한은 정산 생성 시각 이후여야 합니다.","result":null}
                                            """)
                            })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증 쿠키 누락·만료 또는 유효하지 않은 Access Token",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {"isSuccess":false,"code":"AUTH401_1","message":"인증이 필요합니다.","result":null}
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "생성 권한 없음 또는 CSRF 검증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {"isSuccess":false,"code":"AUTH403_1","message":"요청이 거부되었습니다.","result":null}
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "모임 없음·폐쇄·삭제",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {"isSuccess":false,"code":"GROUP404_1","message":"모임을 찾을 수 없습니다.","result":null}
                                    """)))
    })
    ResponseEntity<ApiResponse<SettlementResDTO.Created>> createSettlement(
            @Positive(message = "모임 ID는 양수여야 합니다.")
            @Parameter(description = "정산을 생성할 모임 ID (member_group.id)", required = true) Long groupId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SettlementReqDTO.Create.class), examples = {
                                    @ExampleObject(name = "균등 분할", value = """
                                            {"title":"모임 회비","totalAmount":10000,"splitMode":"EQUAL","groupMemberIds":[11,12,13]}
                                            """),
                                    @ExampleObject(name = "일정별 개별 금액", value = """
                                            {"title":"식사비 정산","scheduleId":7,"totalAmount":20000,"splitMode":"CUSTOM",
                                             "memberAmounts":[{"groupMemberId":11,"amount":12000},{"groupMemberId":12,"amount":8000}]}
                                            """)
                            })) @Valid SettlementReqDTO.Create request,
            @Parameter(hidden = true) Long memberId
    );

    @Operation(summary = "정산 분할 미리보기",
            description = "폐쇄·삭제되지 않은 모임의 활성 OWNER 또는 MANAGER가 회원별 청구액을 미리 확인합니다. "
                    + "대상 ID는 group_member.id이며 같은 모임의 활성·미삭제 회원만 선택할 수 있습니다. "
                    + "EQUAL은 totalAmount와 groupMemberIds, CUSTOM은 totalAmount와 memberAmounts를 전달합니다. "
                    + "사용하지 않는 대상·금액 목록은 생략하거나 null로 전달해야 합니다. "
                    + "결과는 대상 ID 오름차순이며 amount는 나머지 추가분이 포함된 최종 청구액입니다. "
                    + "remainderAmount는 EQUAL에서 추가 배분한 0 또는 1원이고 CUSTOM에서는 0원입니다. "
                    + "정산·청구를 저장하거나 대상을 예약하지 않습니다. 생성 시 권한·대상·금액을 다시 검증합니다. "
                    + "moeasy_access_token 쿠키와 XSRF-TOKEN 쿠키, X-XSRF-TOKEN 헤더가 필요합니다.",
            parameters = @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true,
                    description = "GET /api/v1/auth/csrf 응답의 result.token", schema = @Schema(type = "string")))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", useReturnTypeSchema = true, description = "분할 미리보기 성공 (저장하지 않음)",
                    content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                            {"isSuccess":true,"code":"SETTLEMENT200_1","message":"정산 분할 미리보기에 성공했습니다.",
                             "result":{"splitMode":"EQUAL","totalAmount":500,"targetCount":3,"items":[
                               {"groupMemberId":11,"nickname":"모이지","amount":167,"remainderAmount":1},
                               {"groupMemberId":12,"nickname":"민지","amount":167,"remainderAmount":1},
                               {"groupMemberId":13,"nickname":"도윤","amount":166,"remainderAmount":0}]}}
                            """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "필드·JSON 형식 또는 분할 규칙 검증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class),
                            examples = {
                                    @ExampleObject(name = "필드 검증", value = """
                                            {"isSuccess":false,"code":"VALID400_1","message":"검증에 실패했습니다.","result":{"totalAmount":["정산 총액은 양수여야 합니다."]}}
                                            """),
                                    @ExampleObject(name = "JSON 형식", value = """
                                            {"isSuccess":false,"code":"COMMON400_1","message":"잘못된 요청입니다.","result":null}
                                            """),
                                    @ExampleObject(name = "대상 회원", value = """
                                            {"isSuccess":false,"code":"SETTLEMENT400_2","message":"정산 대상은 유효한 모임 회원으로 중복 없이 선택해야 합니다.","result":null}
                                            """),
                                    @ExampleObject(name = "조건부 필드", value = """
                                            {"isSuccess":false,"code":"SETTLEMENT400_3","message":"분할 방식에 맞는 대상·금액 필드를 전달해야 합니다.","result":null}
                                            """),
                                    @ExampleObject(name = "금액 검증", value = """
                                            {"isSuccess":false,"code":"SETTLEMENT400_4","message":"정산 금액 또는 회원별 청구액 합계가 올바르지 않습니다.","result":null}
                                            """)
                            })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증 쿠키 누락·만료 또는 유효하지 않은 Access Token",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {"isSuccess":false,"code":"AUTH401_1","message":"인증이 필요합니다.","result":null}
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "미리보기 권한 없음 또는 CSRF 검증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {"isSuccess":false,"code":"AUTH403_1","message":"요청이 거부되었습니다.","result":null}
                                    """))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "모임 없음·폐쇄·삭제",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(value = """
                                    {"isSuccess":false,"code":"GROUP404_1","message":"모임을 찾을 수 없습니다.","result":null}
                                    """)))
    })
    ResponseEntity<ApiResponse<SettlementResDTO.Preview>> previewSplit(
            @Positive(message = "모임 ID는 양수여야 합니다.")
            @Parameter(description = "분할을 미리볼 모임 ID (member_group.id)", required = true) Long groupId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SettlementReqDTO.Preview.class), examples = {
                                    @ExampleObject(name = "균등 분할", value = """
                                            {"splitMode":"EQUAL","totalAmount":500,"groupMemberIds":[13,11,12]}
                                            """),
                                    @ExampleObject(name = "회원별 금액 지정", value = """
                                            {"splitMode":"CUSTOM","totalAmount":20000,
                                             "memberAmounts":[{"groupMemberId":11,"amount":12000},{"groupMemberId":12,"amount":8000}]}
                                            """)
                            })) @Valid SettlementReqDTO.Preview request,
            @Parameter(hidden = true) Long memberId
    );
}
