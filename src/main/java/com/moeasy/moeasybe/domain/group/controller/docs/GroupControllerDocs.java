package com.moeasy.moeasybe.domain.group.controller.docs;

import com.moeasy.moeasybe.domain.group.dto.request.GroupReqDTO;
import com.moeasy.moeasybe.domain.group.dto.response.GroupResDTO;
import com.moeasy.moeasybe.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "Group", description = "그룹 생성·조회·수정·폐쇄 API")
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
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 실패")
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
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "그룹 또는 카테고리가 없음")
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
}
