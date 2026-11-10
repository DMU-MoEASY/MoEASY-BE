package com.moeasy.moeasybe.domain.schedule.controller.docs;

import com.moeasy.moeasybe.domain.schedule.dto.request.ParticipationReqDTO;
import com.moeasy.moeasybe.domain.schedule.dto.response.ParticipationResDTO;
import com.moeasy.moeasybe.global.apiPayload.ApiResponse;
import org.springframework.http.ResponseEntity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

@io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공", useReturnTypeSchema = true),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 요청 또는 명단 밖 항목"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "권한 또는 CSRF 검증 실패"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "모임 또는 일정 없음"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "상태·시간·버전 충돌")
})
@Tag(name = "참석 신청 및 수동 출석")
public interface ParticipationControllerDocs {

    @Operation(summary = "현재 활성 명단의 참석 신청 조회")
    ResponseEntity<ApiResponse<ParticipationResDTO.Rsvp>> responses(@Positive Long scheduleId, @Parameter(hidden = true) Long memberId);

    @Operation(summary = "시작 전 본인 참석 신청")
    ResponseEntity<ApiResponse<ParticipationResDTO.Response>> respond(@Positive Long scheduleId, @Valid ParticipationReqDTO.Rsvp request, @Parameter(hidden = true) Long memberId);

    @Operation(summary = "본인 참석 신청 철회")
    ResponseEntity<ApiResponse<Void>> withdraw(@Positive Long scheduleId, @Parameter(hidden = true) Long memberId);

    @Operation(summary = "운영진 출석 명단 조회")
    ResponseEntity<ApiResponse<ParticipationResDTO.Attendance>> attendance(@Positive Long scheduleId, @Parameter(hidden = true) Long memberId);

    @Operation(summary = "수동 출석 전체 교체")
    ResponseEntity<ApiResponse<ParticipationResDTO.Attendance>> replace(@Positive Long scheduleId, @Valid ParticipationReqDTO.Replace request, @Parameter(hidden = true) Long memberId);

    @Operation(summary = "종료 후 집계 완료")
    ResponseEntity<ApiResponse<ParticipationResDTO.Attendance>> complete(@Positive Long scheduleId, @Valid ParticipationReqDTO.Version request, @Parameter(hidden = true) Long memberId);

    @Operation(summary = "출석 집계 재개방")
    ResponseEntity<ApiResponse<ParticipationResDTO.Attendance>> reopen(@Positive Long scheduleId, @Valid ParticipationReqDTO.Version request, @Parameter(hidden = true) Long memberId);
}
