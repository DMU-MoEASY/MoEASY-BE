package com.moeasy.moeasybe.domain.member.controller;

import com.moeasy.moeasybe.domain.member.dto.request.MemberReqDTO;
import com.moeasy.moeasybe.domain.member.dto.response.MemberResDTO;
import com.moeasy.moeasybe.domain.member.exception.code.MemberSuccessCode;
import com.moeasy.moeasybe.domain.member.service.query.MemberQueryService;
import com.moeasy.moeasybe.global.apiPayload.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController implements MemberControllerDocs {

    private final MemberQueryService memberQueryService;

    @Override
    @GetMapping("/nicknames/availability")
    public ResponseEntity<ApiResponse<MemberResDTO.NicknameAvailability>> getNicknameAvailability(
            @Valid @ModelAttribute MemberReqDTO.NicknameAvailability request
    ) {
        MemberResDTO.NicknameAvailability result = memberQueryService.getNicknameAvailability(request.nickname());
        MemberSuccessCode code = MemberSuccessCode.NICKNAME_AVAILABILITY_FETCH_SUCCESS;
        return ResponseEntity.status(code.getStatus()).body(ApiResponse.onSuccess(code, result));
    }
}
