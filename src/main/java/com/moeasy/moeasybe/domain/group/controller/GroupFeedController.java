package com.moeasy.moeasybe.domain.group.controller;

import com.moeasy.moeasybe.domain.group.controller.docs.GroupFeedControllerDocs;
import com.moeasy.moeasybe.domain.group.dto.request.GroupFeedReqDTO;
import com.moeasy.moeasybe.domain.group.dto.response.GroupFeedResDTO;
import com.moeasy.moeasybe.domain.group.exception.code.GroupSuccessCode;
import com.moeasy.moeasybe.domain.group.service.query.GroupFeedQueryService;
import com.moeasy.moeasybe.global.apiPayload.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/v1/groups")
public class GroupFeedController implements GroupFeedControllerDocs {
    private final GroupFeedQueryService queryService;

    @Override
    @GetMapping
    public ResponseEntity<ApiResponse<GroupFeedResDTO.Feed>> getFeed(
            @ModelAttribute GroupFeedReqDTO.Search request, @AuthenticationPrincipal Long memberId) {
        var result = queryService.getFeed(memberId, request);
        return ResponseEntity.status(GroupSuccessCode.GROUP_FEED_FETCHED.getStatus())
                .body(ApiResponse.onSuccess(GroupSuccessCode.GROUP_FEED_FETCHED, result));
    }
}
