package com.moeasy.moeasybe.domain.schedule.controller.docs;

import com.moeasy.moeasybe.domain.schedule.dto.request.ScheduleReqDTO;
import com.moeasy.moeasybe.domain.schedule.dto.response.ScheduleResDTO;
import com.moeasy.moeasybe.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;
import org.springframework.http.ResponseEntity;

@Tag(name = "Schedule", description = "모임 일정 관리 API")
public interface ScheduleControllerDocs {

    @Operation(summary = "일정 목록 조회",
            description = "활성 구성원이 조회합니다. "
                    + "보관 모임은 조회 가능하며 폐쇄·삭제된 모임은 제외합니다. "
                    + "기간은 [from,to), 생략 시 한국 시간 이번 달입니다. "
                    + "정렬은 createdAt DESC, id DESC입니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "일정 목록 조회 성공", useReturnTypeSchema = true,
                    content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "입력 형식·ID·기간·커서 검증 실패: COMMON400_1 또는 VALID400_1",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "기존 인증 오류 계약: 인증 쿠키 누락·만료·유효하지 않은 토큰",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "활성 구성원이 아님",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "모임 없음·폐쇄·삭제: SCHEDULE404_2",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    })
    ResponseEntity<ApiResponse<ScheduleResDTO.Page>> getSchedules(
            @Positive @Parameter(description = "모임 ID") Long groupId,
            @Parameter(description = "조회 시작 (한국 시간 yyyy-MM-dd'T'HH:mm:ss). to와 함께 전달") LocalDateTime from,
            @Parameter(description = "조회 끝 (미포함). from과 함께 전달") LocalDateTime to,
            @Parameter(description = "페이지 크기 (1~100, 기본 20)") Integer size,
            @Parameter(description = "이전 응답의 nextCursor. 모임·기간은 최초 조회와 동일해야 합니다") String cursor,
            @Parameter(hidden = true) Long memberId
    );

    @Operation(summary = "일정 생성",
            description = "활성 OWNER·MANAGER만 생성할 수 있습니다. "
                    + "시작은 현재보다 미래, 종료는 시작보다 미래여야 합니다. "
                    + "시간은 한국 시간 yyyy-MM-dd'T'HH:mm:ss이며 오프셋·소수 초는 받지 않습니다. "
                    + "좌표와 장소 식별자는 각각 쌍으로 전달합니다. "
                    + "쓰기 요청에는 XSRF-TOKEN 쿠키와 X-XSRF-TOKEN 헤더가 필요합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "일정 생성 성공", useReturnTypeSchema = true,
                    content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "입력 형식 또는 ID 검증 실패: COMMON400_1 또는 VALID400_1",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "기존 인증 오류 계약: 인증 쿠키 누락·만료·유효하지 않은 토큰",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "역할·멤버십 권한 부족 또는 CSRF 검증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "모임 없음·폐쇄·삭제: SCHEDULE404_2",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "보관된 모임: SCHEDULE409_2",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    })
    ResponseEntity<ApiResponse<ScheduleResDTO.Created>> createSchedule(
            @Positive @Parameter(description = "모임 ID") Long groupId,
            @Valid ScheduleReqDTO.Create request,
            @Parameter(hidden = true) Long memberId
    );

    @Operation(summary = "일정 상세 조회",
            description = "일정이 실제 소속된 모임의 활성 구성원만 조회할 수 있습니다. 보관 모임은 조회 가능하며 폐쇄·삭제된 모임과 삭제된 일정은 제외합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "일정 상세 조회 성공", useReturnTypeSchema = true,
                    content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "입력 형식 또는 ID 검증 실패: COMMON400_1 또는 VALID400_1",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "기존 인증 오류 계약: 인증 쿠키 누락·만료·유효하지 않은 토큰",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "활성 구성원이 아님",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "모임 없음·폐쇄·삭제: SCHEDULE404_2, 일정 없음·삭제: SCHEDULE404_1",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    })
    ResponseEntity<ApiResponse<ScheduleResDTO.Detail>> getSchedule(
            @Positive @Parameter(description = "일정 ID") Long scheduleId,
            @Parameter(hidden = true) Long memberId
    );

    @Operation(summary = "일정 부분 수정",
            description = "활성 OWNER·MANAGER가 시작 전 SCHEDULED 일정을 수정합니다. "
                    + "생략한 필드는 유지하며 필수 필드의 null·공백은 거부합니다. "
                    + "latitude·longitude, placeProvider·placeId는 각각 쌍으로 변경하고 두 값 모두 null이면 제거합니다. "
                    + "빈 객체와 알 수 없는 필드는 거부합니다. "
                    + "시간은 한국 시간 yyyy-MM-dd'T'HH:mm:ss이며 기존 값과 합쳐 검증합니다. "
                    + "XSRF-TOKEN 쿠키와 X-XSRF-TOKEN 헤더가 필요합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "일정 수정 성공", useReturnTypeSchema = true,
                    content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "입력 형식 또는 ID 검증 실패: COMMON400_1 또는 VALID400_1",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "기존 인증 오류 계약: 인증 쿠키 누락·만료·유효하지 않은 토큰",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "역할·멤버십 권한 부족 또는 CSRF 검증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "모임 없음·폐쇄·삭제: SCHEDULE404_2, 일정 없음·삭제: SCHEDULE404_1",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "이미 시작했거나 취소된 일정: SCHEDULE409_1, 보관된 모임: SCHEDULE409_2",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    })
    ResponseEntity<ApiResponse<ScheduleResDTO.Detail>> updateSchedule(
            @Positive @Parameter(description = "일정 ID") Long scheduleId,
            @Valid
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ScheduleReqDTO.Update.class),
                            examples = {
                                    @ExampleObject(name = "제목 변경", value = "{\"title\":\"변경한 모임\"}"),
                                    @ExampleObject(name = "좌표 제거", value = "{\"latitude\":null,\"longitude\":null}")
                            }))
            ScheduleReqDTO.Update request,
            @Parameter(hidden = true) Long memberId
    );

    @Operation(summary = "일정 취소",
            description = "활성 OWNER·MANAGER가 시작 전 SCHEDULED 일정을 CANCELLED로 변경합니다. "
                    + "재취소는 409이며 기존 정산은 유지합니다. "
                    + "XSRF-TOKEN 쿠키와 X-XSRF-TOKEN 헤더가 필요합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "일정 취소 성공", useReturnTypeSchema = true,
                    content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "입력 형식 또는 ID 검증 실패: COMMON400_1 또는 VALID400_1",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "기존 인증 오류 계약: 인증 쿠키 누락·만료·유효하지 않은 토큰",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "역할·멤버십 권한 부족 또는 CSRF 검증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "모임 없음·폐쇄·삭제: SCHEDULE404_2, 일정 없음·삭제: SCHEDULE404_1",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "이미 시작했거나 취소된 일정: SCHEDULE409_1, 보관된 모임: SCHEDULE409_2",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    })
    ResponseEntity<ApiResponse<Void>> cancelSchedule(
            @Positive @Parameter(description = "일정 ID") Long scheduleId,
            @Parameter(hidden = true) Long memberId
    );

    @Operation(summary = "일정 삭제",
            description = "활성 OWNER만 소프트 삭제할 수 있습니다. "
                    + "진행 중·종료·취소 일정도 삭제 가능하며 재삭제는 404입니다. "
                    + "기존 정산과 연관 데이터는 유지합니다. "
                    + "XSRF-TOKEN 쿠키와 X-XSRF-TOKEN 헤더가 필요합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "일정 삭제 성공", useReturnTypeSchema = true,
                    content = @Content(mediaType = "application/json")),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "입력 형식 또는 ID 검증 실패: COMMON400_1 또는 VALID400_1",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "기존 인증 오류 계약: 인증 쿠키 누락·만료·유효하지 않은 토큰",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "역할·멤버십 권한 부족 또는 CSRF 검증 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "모임 없음·폐쇄·삭제: SCHEDULE404_2, 일정 없음·삭제: SCHEDULE404_1",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "보관된 모임: SCHEDULE409_2",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ApiResponse.class)))
    })
    ResponseEntity<ApiResponse<Void>> deleteSchedule(
            @Positive @Parameter(description = "일정 ID") Long scheduleId,
            @Parameter(hidden = true) Long memberId
    );
}
