package com.moeasy.moeasybe.domain.group.controller;

import com.moeasy.moeasybe.domain.group.controller.docs.GroupControllerDocs;
import com.moeasy.moeasybe.domain.group.dto.request.GroupJoinRequestReqDTO;
import com.moeasy.moeasybe.domain.group.dto.request.GroupReqDTO;
import com.moeasy.moeasybe.domain.group.dto.response.GroupJoinRequestResDTO;
import com.moeasy.moeasybe.domain.group.dto.response.GroupResDTO;
import com.moeasy.moeasybe.domain.group.exception.code.GroupSuccessCode;
import com.moeasy.moeasybe.domain.group.service.GroupCoverImageService;
import com.moeasy.moeasybe.domain.group.service.command.GroupCommandService;
import com.moeasy.moeasybe.domain.group.service.command.GroupJoinRequestCommandService;
import com.moeasy.moeasybe.domain.group.service.query.GroupJoinRequestQueryService;
import com.moeasy.moeasybe.domain.group.service.query.GroupQueryService;
import com.moeasy.moeasybe.global.apiPayload.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
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
    private final GroupJoinRequestCommandService joinRequestCommandService;
    private final GroupJoinRequestQueryService joinRequestQueryService;
    private final GroupCoverImageService coverImageService;

    @Override
    @PostMapping("/cover-image-uploads")
    public ResponseEntity<ApiResponse<GroupResDTO.CoverImageUploadIssue>> issueCoverImageUpload(
            @Valid @RequestBody GroupReqDTO.CoverImageUploadIssue request,
            @AuthenticationPrincipal Long memberId
    ) {
        GroupResDTO.CoverImageUploadIssue result = coverImageService.issueUpload(memberId, request.contentType());
        return ResponseEntity.status(GroupSuccessCode.GROUP_COVER_UPLOAD_ISSUED.getStatus())
                .body(ApiResponse.onSuccess(GroupSuccessCode.GROUP_COVER_UPLOAD_ISSUED, result));
    }

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

    @Override
    @PostMapping("/{groupId}/join-requests")
    public ResponseEntity<ApiResponse<GroupJoinRequestResDTO.Created>> createJoinRequest(
            @PathVariable Long groupId,
            @Valid @RequestBody GroupJoinRequestReqDTO.Create request,
            @AuthenticationPrincipal Long memberId
    ) {
        GroupJoinRequestResDTO.Created result = joinRequestCommandService.create(groupId, request, memberId);
        return ResponseEntity.status(GroupSuccessCode.JOIN_REQUEST_CREATED.getStatus())
                .body(ApiResponse.onSuccess(GroupSuccessCode.JOIN_REQUEST_CREATED, result));
    }

    @Override
    @DeleteMapping("/{groupId}/join-requests/me")
    public ResponseEntity<ApiResponse<Void>> cancelJoinRequest(
            @PathVariable Long groupId,
            @AuthenticationPrincipal Long memberId
    ) {
        joinRequestCommandService.cancel(groupId, memberId);
        return ResponseEntity.status(GroupSuccessCode.JOIN_REQUEST_CANCELLED.getStatus())
                .body(ApiResponse.onSuccess(GroupSuccessCode.JOIN_REQUEST_CANCELLED, null));
    }

    @Override
    @GetMapping("/{groupId}/join-requests")
    public ResponseEntity<ApiResponse<GroupJoinRequestResDTO.PendingList>> getPendingJoinRequests(
            @PathVariable Long groupId,
            @Valid @ModelAttribute GroupJoinRequestReqDTO.Page pagination,
            @AuthenticationPrincipal Long memberId
    ) {
        GroupJoinRequestResDTO.PendingList result = joinRequestQueryService.getPending(
                groupId, pagination.page(), pagination.size(), memberId);
        return ResponseEntity.status(GroupSuccessCode.JOIN_REQUESTS_FETCHED.getStatus())
                .body(ApiResponse.onSuccess(GroupSuccessCode.JOIN_REQUESTS_FETCHED, result));
    }

    @Override
    @GetMapping("/{groupId}/join-requests/me")
    public ResponseEntity<ApiResponse<GroupJoinRequestResDTO.Detail>> getMyJoinRequest(
            @PathVariable Long groupId,
            @AuthenticationPrincipal Long memberId
    ) {
        GroupJoinRequestResDTO.Detail result = joinRequestQueryService.getMine(groupId, memberId);
        return ResponseEntity.status(GroupSuccessCode.JOIN_REQUEST_FETCHED.getStatus())
                .body(ApiResponse.onSuccess(GroupSuccessCode.JOIN_REQUEST_FETCHED, result));
    }

    @Override
    @PostMapping("/{groupId}/join-requests/{requestId}/approve")
    public ResponseEntity<ApiResponse<Void>> approveJoinRequest(
            @PathVariable Long groupId,
            @PathVariable Long requestId,
            @AuthenticationPrincipal Long memberId
    ) {
        joinRequestCommandService.approve(groupId, requestId, memberId);
        return ResponseEntity.status(GroupSuccessCode.JOIN_REQUEST_APPROVED.getStatus())
                .body(ApiResponse.onSuccess(GroupSuccessCode.JOIN_REQUEST_APPROVED, null));
    }

    @Override
    @PostMapping("/{groupId}/join-requests/{requestId}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectJoinRequest(
            @PathVariable Long groupId,
            @PathVariable Long requestId,
            @Valid @RequestBody GroupJoinRequestReqDTO.Reject request,
            @AuthenticationPrincipal Long memberId
    ) {
        joinRequestCommandService.reject(groupId, requestId, request, memberId);
        return ResponseEntity.status(GroupSuccessCode.JOIN_REQUEST_REJECTED.getStatus())
                .body(ApiResponse.onSuccess(GroupSuccessCode.JOIN_REQUEST_REJECTED, null));
    }
}
