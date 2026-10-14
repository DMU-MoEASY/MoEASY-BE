package com.moeasy.moeasybe.domain.group.controller;

import com.moeasy.moeasybe.domain.group.controller.docs.GroupControllerDocs;
import com.moeasy.moeasybe.domain.group.dto.request.GroupReqDTO;
import com.moeasy.moeasybe.domain.group.dto.response.GroupResDTO;
import com.moeasy.moeasybe.domain.group.exception.code.GroupSuccessCode;
import com.moeasy.moeasybe.domain.group.service.command.GroupCommandService;
import com.moeasy.moeasybe.domain.group.service.query.GroupQueryService;
import com.moeasy.moeasybe.global.apiPayload.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/groups")
public class GroupController implements GroupControllerDocs {

    private final GroupCommandService groupCommandService;
    private final GroupQueryService groupQueryService;

    @Override
    @PostMapping
    public ResponseEntity<ApiResponse<GroupResDTO.Created>> createGroup(
            @Valid @RequestBody GroupReqDTO.Create request,
            @AuthenticationPrincipal Long memberId
    ) {
        GroupResDTO.Created result = groupCommandService.createGroup(request, memberId);

        return ResponseEntity.status(GroupSuccessCode.GROUP_CREATED.getStatus())
                .body(ApiResponse.onSuccess(GroupSuccessCode.GROUP_CREATED, result));
    }

    @Override
    @GetMapping("/{groupId}")
    public ResponseEntity<ApiResponse<GroupResDTO.Detail>> getGroup(
            @PathVariable Long groupId,
            @AuthenticationPrincipal Long memberId
    ) {
        GroupResDTO.Detail result = groupQueryService.getGroup(groupId, memberId);

        return ResponseEntity.status(GroupSuccessCode.GROUP_FETCHED.getStatus())
                .body(ApiResponse.onSuccess(GroupSuccessCode.GROUP_FETCHED, result));
    }

    @Override
    @PatchMapping("/{groupId}")
    public ResponseEntity<ApiResponse<GroupResDTO.Detail>> updateGroup(
            @PathVariable Long groupId,
            @Valid @RequestBody GroupReqDTO.Update request,
            @AuthenticationPrincipal Long memberId
    ) {
        GroupResDTO.Detail result = groupCommandService.updateGroup(groupId, request, memberId);

        return ResponseEntity.status(GroupSuccessCode.GROUP_UPDATED.getStatus())
                .body(ApiResponse.onSuccess(GroupSuccessCode.GROUP_UPDATED, result));
    }

    @Override
    @DeleteMapping("/{groupId}")
    public ResponseEntity<ApiResponse<Void>> closeGroup(
            @PathVariable Long groupId,
            @Valid @RequestBody GroupReqDTO.Close request,
            @AuthenticationPrincipal Long memberId
    ) {
        groupCommandService.closeGroup(groupId, request, memberId);

        return ResponseEntity.status(GroupSuccessCode.GROUP_CLOSED.getStatus())
                .body(ApiResponse.onSuccess(GroupSuccessCode.GROUP_CLOSED, null));
    }
}
