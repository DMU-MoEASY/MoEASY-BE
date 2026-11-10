package com.moeasy.moeasybe.domain.schedule.controller;

import com.moeasy.moeasybe.domain.schedule.dto.request.ParticipationReqDTO;
import com.moeasy.moeasybe.domain.schedule.dto.response.ParticipationResDTO;
import com.moeasy.moeasybe.global.apiPayload.ApiResponse;
import org.springframework.http.ResponseEntity;

import com.moeasy.moeasybe.domain.schedule.controller.docs.ParticipationControllerDocs;
import com.moeasy.moeasybe.domain.schedule.exception.code.ScheduleSuccessCode;
import com.moeasy.moeasybe.domain.schedule.service.command.ParticipationCommandService;
import com.moeasy.moeasybe.domain.schedule.service.query.ParticipationQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/schedules/{scheduleId}")
public class ParticipationController implements ParticipationControllerDocs {
    private final ParticipationCommandService command;
    private final ParticipationQueryService query;

    @Override
    @GetMapping("/rsvp")
    public ResponseEntity<ApiResponse<ParticipationResDTO.Rsvp>> responses(@PathVariable Long scheduleId, @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.onSuccess(ScheduleSuccessCode.PARTICIPATION,
                query.rsvp(scheduleId, memberId)));
    }

    @Override
    @PutMapping("/rsvp/me")
    public ResponseEntity<ApiResponse<ParticipationResDTO.Response>> respond(@PathVariable Long scheduleId, @RequestBody ParticipationReqDTO.Rsvp request, @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.onSuccess(ScheduleSuccessCode.PARTICIPATION,
                command.respond(scheduleId, memberId, request.status())));
    }

    @Override
    @DeleteMapping("/rsvp/me")
    public ResponseEntity<ApiResponse<Void>> withdraw(@PathVariable Long scheduleId, @AuthenticationPrincipal Long memberId) {
        command.respond(scheduleId, memberId, null);
        return ResponseEntity.ok(ApiResponse.onSuccess(ScheduleSuccessCode.PARTICIPATION,
                null));
    }

    @Override
    @GetMapping("/attendance")
    public ResponseEntity<ApiResponse<ParticipationResDTO.Attendance>> attendance(@PathVariable Long scheduleId, @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.onSuccess(ScheduleSuccessCode.PARTICIPATION,
                query.attendance(scheduleId, memberId, command.prepareRoster(scheduleId, memberId))));
    }

    @Override
    @PutMapping("/attendance")
    public ResponseEntity<ApiResponse<ParticipationResDTO.Attendance>> replace(@PathVariable Long scheduleId, @RequestBody ParticipationReqDTO.Replace request, @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.onSuccess(ScheduleSuccessCode.PARTICIPATION,
                command.replace(scheduleId, memberId, request)));
    }

    @Override
    @PostMapping("/attendance/complete")
    public ResponseEntity<ApiResponse<ParticipationResDTO.Attendance>> complete(@PathVariable Long scheduleId, @RequestBody ParticipationReqDTO.Version request, @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.onSuccess(ScheduleSuccessCode.PARTICIPATION,
                command.setCompleted(scheduleId, memberId, request.version(), true)));
    }

    @Override
    @PostMapping("/attendance/reopen")
    public ResponseEntity<ApiResponse<ParticipationResDTO.Attendance>> reopen(@PathVariable Long scheduleId, @RequestBody ParticipationReqDTO.Version request, @AuthenticationPrincipal Long memberId) {
        return ResponseEntity.ok(ApiResponse.onSuccess(ScheduleSuccessCode.PARTICIPATION,
                command.setCompleted(scheduleId, memberId, request.version(), false)));
    }
}
