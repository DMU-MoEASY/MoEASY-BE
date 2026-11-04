package com.moeasy.moeasybe.domain.schedule.controller;
import com.moeasy.moeasybe.domain.schedule.dto.request.AvailabilityReqDTO;
import com.moeasy.moeasybe.domain.schedule.dto.response.AvailabilityResDTO;
import com.moeasy.moeasybe.global.apiPayload.ApiResponse;
import org.springframework.http.ResponseEntity;
import com.moeasy.moeasybe.domain.schedule.controller.docs.AvailabilityControllerDocs;
import com.moeasy.moeasybe.domain.schedule.exception.code.AvailabilitySuccessCode;
import com.moeasy.moeasybe.domain.schedule.service.command.AvailabilityCommandService;
import com.moeasy.moeasybe.domain.schedule.service.query.AvailabilityQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @Validated
@RequestMapping("/api/v1/schedules/{scheduleId}")
public class AvailabilityController implements AvailabilityControllerDocs {
    private final AvailabilityCommandService command;
    private final AvailabilityQueryService query;
    @Override
    @PostMapping("/availability-poll")
    public ResponseEntity<ApiResponse<AvailabilityResDTO.Poll>> createPoll(@PathVariable Long scheduleId, @RequestBody AvailabilityReqDTO.Configure request, @AuthenticationPrincipal Long memberId) {
        AvailabilityResDTO.Poll result = command.create(scheduleId, request, memberId);
        return ResponseEntity.status(AvailabilitySuccessCode.CREATED.getStatus())
                .body(ApiResponse.onSuccess(AvailabilitySuccessCode.CREATED, result));
    }
    @Override
    @GetMapping("/availability-poll")
    public ResponseEntity<ApiResponse<AvailabilityResDTO.Poll>> getPoll(@PathVariable Long scheduleId, @AuthenticationPrincipal Long memberId) {
        AvailabilityResDTO.Poll result = query.getPoll(scheduleId, memberId);
        return ResponseEntity.status(AvailabilitySuccessCode.FETCHED.getStatus())
                .body(ApiResponse.onSuccess(AvailabilitySuccessCode.FETCHED, result));
    }
    @Override
    @PatchMapping("/availability-poll")
    public ResponseEntity<ApiResponse<AvailabilityResDTO.Poll>> configurePoll(@PathVariable Long scheduleId, @RequestBody AvailabilityReqDTO.Configure request, @AuthenticationPrincipal Long memberId) {
        AvailabilityResDTO.Poll result = command.configure(scheduleId, request, memberId);
        return ResponseEntity.status(AvailabilitySuccessCode.UPDATED.getStatus())
                .body(ApiResponse.onSuccess(AvailabilitySuccessCode.UPDATED, result));
    }
    @Override
    @DeleteMapping("/availability-poll")
    public ResponseEntity<ApiResponse<Void>> deletePoll(@PathVariable Long scheduleId, @AuthenticationPrincipal Long memberId) {
        command.delete(scheduleId, memberId);
        return ResponseEntity.status(AvailabilitySuccessCode.UPDATED.getStatus())
                .body(ApiResponse.onSuccess(AvailabilitySuccessCode.UPDATED, null));
    }
    @Override
    @PutMapping("/availability/me")
    public ResponseEntity<ApiResponse<Void>> submit(@PathVariable Long scheduleId, @RequestBody AvailabilityReqDTO.Submit request, @AuthenticationPrincipal Long memberId) {
        command.submit(scheduleId, request, memberId);
        return ResponseEntity.status(AvailabilitySuccessCode.UPDATED.getStatus())
                .body(ApiResponse.onSuccess(AvailabilitySuccessCode.UPDATED, null));
    }
    @Override
    @GetMapping("/availability/me")
    public ResponseEntity<ApiResponse<AvailabilityResDTO.Me>> getMe(@PathVariable Long scheduleId, @AuthenticationPrincipal Long memberId) {
        AvailabilityResDTO.Me result = query.getMe(scheduleId, memberId);
        return ResponseEntity.status(AvailabilitySuccessCode.FETCHED.getStatus())
                .body(ApiResponse.onSuccess(AvailabilitySuccessCode.FETCHED, result));
    }
    @Override
    @DeleteMapping("/availability/me")
    public ResponseEntity<ApiResponse<Void>> withdraw(@PathVariable Long scheduleId, @AuthenticationPrincipal Long memberId) {
        command.withdraw(scheduleId, memberId);
        return ResponseEntity.status(AvailabilitySuccessCode.UPDATED.getStatus())
                .body(ApiResponse.onSuccess(AvailabilitySuccessCode.UPDATED, null));
    }
    @Override
    @GetMapping("/availability")
    public ResponseEntity<ApiResponse<AvailabilityResDTO.Detail>> getAvailability(@PathVariable Long scheduleId, @AuthenticationPrincipal Long memberId) {
        AvailabilityResDTO.Detail result = query.getAvailability(scheduleId, memberId);
        return ResponseEntity.status(AvailabilitySuccessCode.FETCHED.getStatus())
                .body(ApiResponse.onSuccess(AvailabilitySuccessCode.FETCHED, result));
    }
    @Override
    @PostMapping("/availability-poll/confirm")
    public ResponseEntity<ApiResponse<AvailabilityResDTO.Poll>> confirm(@PathVariable Long scheduleId, @RequestBody AvailabilityReqDTO.Confirm request, @AuthenticationPrincipal Long memberId) {
        AvailabilityResDTO.Poll result = command.confirm(scheduleId, request, memberId);
        return ResponseEntity.status(AvailabilitySuccessCode.UPDATED.getStatus())
                .body(ApiResponse.onSuccess(AvailabilitySuccessCode.UPDATED, result));
    }
}
