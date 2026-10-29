package com.moeasy.moeasybe.domain.group.controller.docs;

import com.moeasy.moeasybe.domain.group.dto.request.GroupJoinRequestReqDTO;
import com.moeasy.moeasybe.domain.group.dto.request.GroupReqDTO;
import com.moeasy.moeasybe.domain.group.dto.response.GroupJoinRequestResDTO;
import com.moeasy.moeasybe.domain.group.dto.response.GroupResDTO;
import com.moeasy.moeasybe.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Group", description = "그룹 생성·조회·수정·폐쇄 및 가입 신청 관리 API")
public interface GroupControllerDocs {

    @Operation(
            summary = "그룹 생성",
            description = "인증된 회원이 그룹을 생성합니다. 생성한 회원은 활성 OWNER로 등록됩니다. "
                    + "회원 식별자는 JWT 인증 principal에서 가져옵니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "그룹 생성 성공",
                    content = @Content(schema = @Schema(implementation = GroupResDTO.Created.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 검증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "STORAGE503_1: S3 저장소 장애")
    })
    ResponseEntity<ApiResponse<GroupResDTO.Created>> createGroup(
            @RequestBody GroupReqDTO.Create request,
            @Parameter(hidden = true) Long memberId
    );

    @Operation(
            summary = "그룹 단건 조회",
            description = "인증된 회원이 그룹 상세를 조회합니다. 그룹 멤버 여부는 조회 조건이 아닙니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "그룹 조회 성공",
                    content = @Content(schema = @Schema(implementation = GroupResDTO.Detail.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "그룹이 없거나 폐쇄됨")
    })
    ResponseEntity<ApiResponse<GroupResDTO.Detail>> getGroup(
            @Parameter(description = "조회할 그룹 ID", required = true)
            @PathVariable Long groupId,
            @Parameter(hidden = true) Long memberId
    );

    @Operation(
            summary = "그룹 부분 수정",
            description = "인증된 활성 OWNER만 그룹을 수정할 수 있습니다. 변경할 필드만 요청 본문에 포함합니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "그룹 수정 성공",
                    content = @Content(schema = @Schema(implementation = GroupResDTO.Detail.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 검증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "활성 OWNER가 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "그룹 또는 카테고리가 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "STORAGE503_1: S3 저장소 장애")
    })
    ResponseEntity<ApiResponse<GroupResDTO.Detail>> updateGroup(
            @Parameter(description = "수정할 그룹 ID", required = true)
            @PathVariable Long groupId,
            @RequestBody GroupReqDTO.Update request,
            @Parameter(hidden = true) Long memberId
    );

    @Operation(
            summary = "그룹 폐쇄",
            description = "인증된 활성 OWNER가 그룹을 소프트 폐쇄합니다. 삭제 이력의 수행자는 JWT 인증 회원입니다."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "그룹 폐쇄 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "요청 검증 실패 또는 확인 문구 불일치"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "활성 OWNER가 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "그룹이 없음")
    })
    ResponseEntity<ApiResponse<Void>> closeGroup(
            @Parameter(description = "폐쇄할 그룹 ID", required = true)
            @PathVariable Long groupId,
            @RequestBody GroupReqDTO.Close request,
            @Parameter(hidden = true) Long memberId
    );

    @Operation(
            parameters = @Parameter(
                    name = "X-XSRF-TOKEN",
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "GET /api/v1/auth/csrf의 result.token. 발급된 XSRF-TOKEN 쿠키도 유지해야 합니다."
            ),
            summary = "가입 신청", description = "소개글은 필수·공백 불가·최대 500자입니다. ACTIVE APPROVAL 그룹에서 정원을 확인합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "JOIN_REQUEST_CREATED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "입력 형식·소개글 검증 실패 또는 GROUP400_3: 승인제 그룹이 아님"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "CSRF 토큰 누락 또는 불일치"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "GROUP404_1: 그룹 없음·삭제·폐쇄 또는 MEMBER404_1: 회원 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "GROUP409_1: 그룹 보관, GROUP409_2: 이미 가입, GROUP409_3: 중복 대기 신청, GROUP409_4: 정원 초과, GROUP409_5: 강퇴")
    })
    ResponseEntity<ApiResponse<GroupJoinRequestResDTO.Created>> createJoinRequest(
            @Parameter(description = "그룹 ID", required = true) Long groupId,
            GroupJoinRequestReqDTO.Create request,
            @Parameter(hidden = true) Long memberId
    );

    @Operation(
            parameters = @Parameter(
                    name = "X-XSRF-TOKEN",
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "GET /api/v1/auth/csrf의 result.token. 발급된 XSRF-TOKEN 쿠키도 유지해야 합니다."
            ),
            summary = "대기 신청 취소", description = "ARCHIVED에서도 허용합니다. 대기 신청이 없으면 GROUP409_7입니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "JOIN_REQUEST_CANCELLED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "그룹 ID 형식 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "CSRF 토큰 누락 또는 불일치"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "GROUP404_1: 그룹 없음·삭제·폐쇄"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "GROUP409_7: 취소할 대기 신청 없음")
    })
    ResponseEntity<ApiResponse<Void>> cancelJoinRequest(
            @Parameter(description = "그룹 ID", required = true) Long groupId,
            @Parameter(hidden = true) Long memberId
    );

    @Operation(summary = "대기 신청 목록 조회", description = "삭제되지 않은 ACTIVE OWNER·MANAGER만 조회합니다. PENDING을 createdAt ASC, id ASC로 정렬합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "JOIN_REQUESTS_FETCHED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "그룹 ID·페이지 입력 형식 또는 범위 오류, offset 상한 초과"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "GROUP404_1: 그룹 없음·삭제·폐쇄"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "삭제되지 않은 ACTIVE OWNER·MANAGER 권한 필요")
    })
    ResponseEntity<ApiResponse<GroupJoinRequestResDTO.PendingList>> getPendingJoinRequests(
            @Parameter(description = "그룹 ID", required = true) Long groupId,
            @ParameterObject GroupJoinRequestReqDTO.Page pagination,
            @Parameter(hidden = true) Long memberId
    );

    @Operation(summary = "내 최근 신청 조회", description = "createdAt DESC, id DESC 기준 최근 신청이며 이력이 없으면 result=null입니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "JOIN_REQUEST_FETCHED: 신청 이력이 없으면 result=null"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "그룹 ID 형식 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "GROUP404_1: 그룹 없음·삭제·폐쇄")
    })
    ResponseEntity<ApiResponse<GroupJoinRequestResDTO.Detail>> getMyJoinRequest(
            @Parameter(description = "그룹 ID", required = true) Long groupId,
            @Parameter(hidden = true) Long memberId
    );

    @Operation(
            parameters = @Parameter(
                    name = "X-XSRF-TOKEN",
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "GET /api/v1/auth/csrf의 result.token. 발급된 XSRF-TOKEN 쿠키도 유지해야 합니다."
            ),
            summary = "가입 신청 승인", description = "ACTIVE OWNER·MANAGER가 정원을 재검사하고 멤버십을 생성·복구합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "JOIN_REQUEST_APPROVED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "그룹 ID·신청 ID 형식 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "GROUP404_1: 그룹 없음·삭제·폐쇄 또는 GROUP404_3: 해당 그룹의 신청 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "CSRF 토큰 누락·불일치 또는 삭제되지 않은 ACTIVE OWNER·MANAGER 권한 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "GROUP409_1: 그룹 보관, GROUP409_2: 이미 가입, GROUP409_4: 정원 초과, GROUP409_5: 강퇴, GROUP409_6: 이미 처리된 신청")
    })
    ResponseEntity<ApiResponse<Void>> approveJoinRequest(
            @Parameter(description = "그룹 ID", required = true) Long groupId,
            @Parameter(description = "가입 신청 ID", required = true) Long requestId,
            @Parameter(hidden = true) Long memberId
    );

    @Operation(
            parameters = @Parameter(
                    name = "X-XSRF-TOKEN",
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "GET /api/v1/auth/csrf의 result.token. 발급된 XSRF-TOKEN 쿠키도 유지해야 합니다."
            ),
            summary = "가입 신청 거절", description = "ACTIVE OWNER·MANAGER가 처리합니다. 거절 사유는 선택·최대 500자이며 {}를 허용합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "JOIN_REQUEST_REJECTED"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "그룹 ID·신청 ID 형식 또는 거절 사유 검증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "GROUP404_1: 그룹 없음·삭제·폐쇄 또는 GROUP404_3: 해당 그룹의 신청 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "CSRF 토큰 누락·불일치 또는 삭제되지 않은 ACTIVE OWNER·MANAGER 권한 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "GROUP409_1: 그룹 보관, GROUP409_6: 이미 처리된 신청")
    })
    ResponseEntity<ApiResponse<Void>> rejectJoinRequest(
            @Parameter(description = "그룹 ID", required = true) Long groupId,
            @Parameter(description = "가입 신청 ID", required = true) Long requestId,
            GroupJoinRequestReqDTO.Reject request,
            @Parameter(hidden = true) Long memberId
    );
}
