package com.moeasy.moeasybe.domain.group.controller.docs;

import com.moeasy.moeasybe.domain.group.dto.request.GroupJoinRequestReqDTO;
import com.moeasy.moeasybe.domain.group.dto.response.GroupJoinRequestResDTO;
import com.moeasy.moeasybe.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;

@Tag(name = "Group Join Request", description = "승인제 그룹 가입 신청·취소·조회·승인·거절")
public interface GroupJoinRequestControllerDocs {

    @Operation(summary = "가입 신청", description = "소개글은 필수·공백 불가·최대 500자입니다. ACTIVE APPROVAL 그룹에서 정원을 확인합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "JOIN_REQUEST_CREATED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청·페이지 입력 검증 실패 또는 OPEN 그룹 신청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "그룹 없음·삭제·폐쇄 또는 해당 그룹의 신청 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "그룹 보관·기존 가입·대기 중복·정원 초과·강퇴·신청 상태 충돌. 세부 코드는 그룹 API 명세 참조")
    })
    ResponseEntity<ApiResponse<GroupJoinRequestResDTO.Created>> create(
            @Parameter(description = "그룹 ID", required = true) Long groupId,
            GroupJoinRequestReqDTO.Create request,
            @Parameter(hidden = true) Long memberId
    );

    @Operation(summary = "대기 신청 취소", description = "ARCHIVED에서도 허용합니다. 대기 신청이 없으면 GROUP409_7입니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "JOIN_REQUEST_CANCELLED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청·페이지 입력 검증 실패 또는 OPEN 그룹 신청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "그룹 없음·삭제·폐쇄 또는 해당 그룹의 신청 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "그룹 보관·기존 가입·대기 중복·정원 초과·강퇴·신청 상태 충돌. 세부 코드는 그룹 API 명세 참조")
    })
    ResponseEntity<ApiResponse<Void>> cancel(
            @Parameter(description = "그룹 ID", required = true) Long groupId,
            @Parameter(hidden = true) Long memberId
    );

    @Operation(summary = "대기 신청 목록 조회", description = "삭제되지 않은 ACTIVE OWNER·MANAGER만 조회합니다. PENDING을 createdAt ASC, id ASC로 정렬합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "JOIN_REQUESTS_FETCHED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청·페이지 입력 검증 실패 또는 OPEN 그룹 신청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "그룹 없음·삭제·폐쇄 또는 해당 그룹의 신청 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "삭제되지 않은 ACTIVE OWNER·MANAGER 권한 필요")
    })
    ResponseEntity<ApiResponse<GroupJoinRequestResDTO.PendingList>> getPending(
            @Parameter(description = "그룹 ID", required = true) Long groupId,
            @ParameterObject GroupJoinRequestReqDTO.Page pagination,
            @Parameter(hidden = true) Long memberId
    );

    @Operation(summary = "내 최근 신청 조회", description = "createdAt DESC, id DESC 기준 최근 신청이며 이력이 없으면 result=null입니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "JOIN_REQUEST_FETCHED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청·페이지 입력 검증 실패 또는 OPEN 그룹 신청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "그룹 없음·삭제·폐쇄 또는 해당 그룹의 신청 없음")
    })
    ResponseEntity<ApiResponse<GroupJoinRequestResDTO.Detail>> getMine(
            @Parameter(description = "그룹 ID", required = true) Long groupId,
            @Parameter(hidden = true) Long memberId
    );

    @Operation(summary = "가입 신청 승인", description = "ACTIVE OWNER·MANAGER가 정원을 재검사하고 멤버십을 생성·복구합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "JOIN_REQUEST_APPROVED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청·페이지 입력 검증 실패 또는 OPEN 그룹 신청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "그룹 없음·삭제·폐쇄 또는 해당 그룹의 신청 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "삭제되지 않은 ACTIVE OWNER·MANAGER 권한 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "그룹 보관·기존 가입·대기 중복·정원 초과·강퇴·신청 상태 충돌. 세부 코드는 그룹 API 명세 참조")
    })
    ResponseEntity<ApiResponse<Void>> approve(
            @Parameter(description = "그룹 ID", required = true) Long groupId,
            @Parameter(description = "가입 신청 ID", required = true) Long requestId,
            @Parameter(hidden = true) Long memberId
    );

    @Operation(summary = "가입 신청 거절", description = "ACTIVE OWNER·MANAGER가 처리합니다. 거절 사유는 선택·최대 500자이며 {}를 허용합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "JOIN_REQUEST_REJECTED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청·페이지 입력 검증 실패 또는 OPEN 그룹 신청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "그룹 없음·삭제·폐쇄 또는 해당 그룹의 신청 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "삭제되지 않은 ACTIVE OWNER·MANAGER 권한 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "그룹 보관·기존 가입·대기 중복·정원 초과·강퇴·신청 상태 충돌. 세부 코드는 그룹 API 명세 참조")
    })
    ResponseEntity<ApiResponse<Void>> reject(
            @Parameter(description = "그룹 ID", required = true) Long groupId,
            @Parameter(description = "가입 신청 ID", required = true) Long requestId,
            GroupJoinRequestReqDTO.Reject request,
            @Parameter(hidden = true) Long memberId
    );
}
