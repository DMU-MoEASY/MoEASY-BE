package com.moeasy.moeasybe.domain.schedule.controller.docs;
import com.moeasy.moeasybe.domain.schedule.dto.request.AvailabilityReqDTO;
import com.moeasy.moeasybe.domain.schedule.dto.response.AvailabilityResDTO;
import com.moeasy.moeasybe.global.apiPayload.ApiResponse;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
@Tag(name = "Availability", description = "기존 일정의 가용시간 투표. 숫자 ID·Asia/Seoul·JWT 쿠키·쓰기 CSRF 인증")
@ApiResponses({@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력 오류"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 또는 CSRF 오류"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "대상 없음"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "상태·추천 토큰·복원 충돌")})
public interface AvailabilityControllerDocs {
    @Operation(summary = "투표 생성",
            description = "moeasy_access_token 쿠키와 XSRF-TOKEN 쿠키, X-XSRF-TOKEN 헤더가 필요합니다. "
                    + "GET /api/v1/auth/csrf로 토큰을 발급받아 result.token을 헤더에 전달합니다.",
            parameters = @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true,
                    description = "GET /api/v1/auth/csrf 응답의 result.token", schema = @Schema(type = "string")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "성공", useReturnTypeSchema = true)
    ResponseEntity<ApiResponse<AvailabilityResDTO.Poll>> createPoll(@Positive Long scheduleId, @Valid AvailabilityReqDTO.Configure request, @Parameter(hidden = true) Long memberId);
    @Operation(summary = "투표 설정 조회")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공", useReturnTypeSchema = true)
    ResponseEntity<ApiResponse<AvailabilityResDTO.Poll>> getPoll(@Positive Long scheduleId, @Parameter(hidden = true) Long memberId);
    @Operation(summary = "후보 설정 전체 변경·재개방·확정 복원",
            description = "moeasy_access_token 쿠키와 XSRF-TOKEN 쿠키, X-XSRF-TOKEN 헤더가 필요합니다. "
                    + "GET /api/v1/auth/csrf로 토큰을 발급받아 result.token을 헤더에 전달합니다.",
            parameters = @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true,
                    description = "GET /api/v1/auth/csrf 응답의 result.token", schema = @Schema(type = "string")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공", useReturnTypeSchema = true)
    ResponseEntity<ApiResponse<AvailabilityResDTO.Poll>> configurePoll(@Positive Long scheduleId, @Valid AvailabilityReqDTO.Configure request, @Parameter(hidden = true) Long memberId);
    @Operation(summary = "투표 Hard Delete",
            description = "moeasy_access_token 쿠키와 XSRF-TOKEN 쿠키, X-XSRF-TOKEN 헤더가 필요합니다. "
                    + "GET /api/v1/auth/csrf로 토큰을 발급받아 result.token을 헤더에 전달합니다.",
            parameters = @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true,
                    description = "GET /api/v1/auth/csrf 응답의 result.token", schema = @Schema(type = "string")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공", useReturnTypeSchema = true)
    ResponseEntity<ApiResponse<Void>> deletePoll(@Positive Long scheduleId, @Parameter(hidden = true) Long memberId);
    @Operation(summary = "본인 응답 전체 교체·빈 배열 제출",
            description = "moeasy_access_token 쿠키와 XSRF-TOKEN 쿠키, X-XSRF-TOKEN 헤더가 필요합니다. "
                    + "GET /api/v1/auth/csrf로 토큰을 발급받아 result.token을 헤더에 전달합니다.",
            parameters = @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true,
                    description = "GET /api/v1/auth/csrf 응답의 result.token", schema = @Schema(type = "string")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공", useReturnTypeSchema = true)
    ResponseEntity<ApiResponse<Void>> submit(@Positive Long scheduleId, @Valid AvailabilityReqDTO.Submit request, @Parameter(hidden = true) Long memberId);
    @Operation(summary = "본인 응답 조회")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공", useReturnTypeSchema = true)
    ResponseEntity<ApiResponse<AvailabilityResDTO.Me>> getMe(@Positive Long scheduleId, @Parameter(hidden = true) Long memberId);
    @Operation(summary = "본인 응답 철회",
            description = "moeasy_access_token 쿠키와 XSRF-TOKEN 쿠키, X-XSRF-TOKEN 헤더가 필요합니다. "
                    + "GET /api/v1/auth/csrf로 토큰을 발급받아 result.token을 헤더에 전달합니다.",
            parameters = @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true,
                    description = "GET /api/v1/auth/csrf 응답의 result.token", schema = @Schema(type = "string")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공", useReturnTypeSchema = true)
    ResponseEntity<ApiResponse<Void>> withdraw(@Positive Long scheduleId, @Parameter(hidden = true) Long memberId);
    @Operation(summary = "히트맵·현황·최적 시간 통합 조회")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공", useReturnTypeSchema = true)
    ResponseEntity<ApiResponse<AvailabilityResDTO.Detail>> getAvailability(@Positive Long scheduleId, @Parameter(hidden = true) Long memberId);
    @Operation(summary = "추천 시간 확정",
            description = "moeasy_access_token 쿠키와 XSRF-TOKEN 쿠키, X-XSRF-TOKEN 헤더가 필요합니다. "
                    + "GET /api/v1/auth/csrf로 토큰을 발급받아 result.token을 헤더에 전달합니다.",
            parameters = @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true,
                    description = "GET /api/v1/auth/csrf 응답의 result.token", schema = @Schema(type = "string")))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공", useReturnTypeSchema = true)
    ResponseEntity<ApiResponse<AvailabilityResDTO.Poll>> confirm(@Positive Long scheduleId, @Valid AvailabilityReqDTO.Confirm request, @Parameter(hidden = true) Long memberId);
}
