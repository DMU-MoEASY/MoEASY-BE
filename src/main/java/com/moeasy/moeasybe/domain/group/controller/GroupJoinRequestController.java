package com.moeasy.moeasybe.domain.group.controller;

import com.moeasy.moeasybe.domain.group.controller.docs.GroupJoinRequestControllerDocs;
import com.moeasy.moeasybe.domain.group.dto.request.GroupJoinRequestReqDTO;
import com.moeasy.moeasybe.domain.group.dto.response.GroupJoinRequestResDTO;
import com.moeasy.moeasybe.domain.group.exception.code.GroupSuccessCode;
import com.moeasy.moeasybe.domain.group.service.command.GroupJoinRequestCommandService;
import com.moeasy.moeasybe.domain.group.service.query.GroupJoinRequestQueryService;
import com.moeasy.moeasybe.global.apiPayload.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/groups/{groupId}/join-requests")
public class GroupJoinRequestController implements GroupJoinRequestControllerDocs {
    private final GroupJoinRequestCommandService commandService;
    private final GroupJoinRequestQueryService queryService;

    @Override
    @PostMapping("")
    public ResponseEntity<ApiResponse<GroupJoinRequestResDTO.Created>> create(
            @PathVariable Long groupId,
            @Valid @RequestBody GroupJoinRequestReqDTO.Create request,
            @AuthenticationPrincipal Long memberId
    ) {
        GroupJoinRequestResDTO.Created result = commandService.create(groupId, request, memberId);
        return ResponseEntity.status(GroupSuccessCode.JOIN_REQUEST_CREATED.getStatus())
                .body(ApiResponse.onSuccess(GroupSuccessCode.JOIN_REQUEST_CREATED, result));
    }

    @Override
    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @PathVariable Long groupId,
            @AuthenticationPrincipal Long memberId
    ) {
        commandService.cancel(groupId, memberId);
        return ResponseEntity.status(GroupSuccessCode.JOIN_REQUEST_CANCELLED.getStatus())
                .body(ApiResponse.onSuccess(GroupSuccessCode.JOIN_REQUEST_CANCELLED, null));
    }

    @Override
    @GetMapping("")
    public ResponseEntity<ApiResponse<GroupJoinRequestResDTO.PendingList>> getPending(
            @PathVariable Long groupId,
            @Valid @ModelAttribute GroupJoinRequestReqDTO.Page pagination,
            @AuthenticationPrincipal Long memberId
    ) {
        GroupJoinRequestResDTO.PendingList result = queryService.getPending(groupId, pagination.page(), pagination.size(), memberId);
        return ResponseEntity.status(GroupSuccessCode.JOIN_REQUESTS_FETCHED.getStatus())
                .body(ApiResponse.onSuccess(GroupSuccessCode.JOIN_REQUESTS_FETCHED, result));
    }

    @Override
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<GroupJoinRequestResDTO.Detail>> getMine(
            @PathVariable Long groupId,
            @AuthenticationPrincipal Long memberId
    ) {
        GroupJoinRequestResDTO.Detail result = queryService.getMine(groupId, memberId);
        return ResponseEntity.status(GroupSuccessCode.JOIN_REQUEST_FETCHED.getStatus())
                .body(ApiResponse.onSuccess(GroupSuccessCode.JOIN_REQUEST_FETCHED, result));
    }

    @Override
    @PostMapping("/{requestId}/approve")
    public ResponseEntity<ApiResponse<Void>> approve(
            @PathVariable Long groupId,
            @PathVariable Long requestId,
            @AuthenticationPrincipal Long memberId
    ) {
        commandService.approve(groupId, requestId, memberId);
        return ResponseEntity.status(GroupSuccessCode.JOIN_REQUEST_APPROVED.getStatus())
                .body(ApiResponse.onSuccess(GroupSuccessCode.JOIN_REQUEST_APPROVED, null));
    }

    @Override
    @PostMapping("/{requestId}/reject")
    public ResponseEntity<ApiResponse<Void>> reject(
            @PathVariable Long groupId,
            @PathVariable Long requestId,
            @Valid @RequestBody GroupJoinRequestReqDTO.Reject request,
            @AuthenticationPrincipal Long memberId
    ) {
        commandService.reject(groupId, requestId, request, memberId);
        return ResponseEntity.status(GroupSuccessCode.JOIN_REQUEST_REJECTED.getStatus())
                .body(ApiResponse.onSuccess(GroupSuccessCode.JOIN_REQUEST_REJECTED, null));
    }
}
