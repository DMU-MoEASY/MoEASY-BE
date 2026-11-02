package com.moeasy.moeasybe.domain.schedule.controller;

import com.moeasy.moeasybe.domain.schedule.dto.request.ScheduleReqDTO;
import com.moeasy.moeasybe.domain.schedule.dto.response.ScheduleResDTO;
import com.moeasy.moeasybe.global.apiPayload.ApiResponse;
import org.springframework.http.ResponseEntity;
import java.time.LocalDateTime;
import com.moeasy.moeasybe.domain.schedule.controller.docs.ScheduleControllerDocs;
import com.moeasy.moeasybe.domain.schedule.exception.code.ScheduleSuccessCode;
import com.moeasy.moeasybe.domain.schedule.service.command.ScheduleCommandService;
import com.moeasy.moeasybe.domain.schedule.service.query.ScheduleQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Validated
@RequestMapping("/api/v1")
public class ScheduleController implements ScheduleControllerDocs {
    private final ScheduleQueryService query;
    private final ScheduleCommandService command;

    @Override
    @GetMapping("/groups/{groupId}/schedules")
    public ResponseEntity<ApiResponse<ScheduleResDTO.Page>> getSchedules(
            @PathVariable Long groupId,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime to,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String cursor,
            @AuthenticationPrincipal Long memberId) {
        ScheduleResDTO.Page result = query.getSchedules(groupId, from, to, size, cursor, memberId);
        return ResponseEntity.status(ScheduleSuccessCode.LIST_FETCHED.getStatus())
                .body(ApiResponse.onSuccess(ScheduleSuccessCode.LIST_FETCHED, result));
    }

    @Override
    @PostMapping("/groups/{groupId}/schedules")
    public ResponseEntity<ApiResponse<ScheduleResDTO.Created>> createSchedule(
            @PathVariable Long groupId,
            @RequestBody ScheduleReqDTO.Create request,
            @AuthenticationPrincipal Long memberId) {
        ScheduleResDTO.Created result = command.createSchedule(groupId, request, memberId);
        return ResponseEntity.status(ScheduleSuccessCode.CREATED.getStatus())
                .body(ApiResponse.onSuccess(ScheduleSuccessCode.CREATED, result));
    }

    @Override
    @GetMapping("/schedules/{scheduleId}")
    public ResponseEntity<ApiResponse<ScheduleResDTO.Detail>> getSchedule(
            @PathVariable Long scheduleId,
            @AuthenticationPrincipal Long memberId) {
        ScheduleResDTO.Detail result = query.getSchedule(scheduleId, memberId);
        return ResponseEntity.status(ScheduleSuccessCode.DETAIL_FETCHED.getStatus())
                .body(ApiResponse.onSuccess(ScheduleSuccessCode.DETAIL_FETCHED, result));
    }

    @Override
    @PatchMapping("/schedules/{scheduleId}")
    public ResponseEntity<ApiResponse<ScheduleResDTO.Detail>> updateSchedule(
            @PathVariable Long scheduleId,
            @RequestBody ScheduleReqDTO.Update request,
            @AuthenticationPrincipal Long memberId) {
        ScheduleResDTO.Detail result = command.updateSchedule(scheduleId, request, memberId);
        return ResponseEntity.status(ScheduleSuccessCode.UPDATED.getStatus())
                .body(ApiResponse.onSuccess(ScheduleSuccessCode.UPDATED, result));
    }

    @Override
    @PostMapping("/schedules/{scheduleId}/cancel")
    public ResponseEntity<ApiResponse<Void>> cancelSchedule(
            @PathVariable Long scheduleId,
            @AuthenticationPrincipal Long memberId) {
        command.cancelSchedule(scheduleId, memberId);
        return ResponseEntity.status(ScheduleSuccessCode.CANCELLED.getStatus())
                .body(ApiResponse.onSuccess(ScheduleSuccessCode.CANCELLED, null));
    }

    @Override
    @DeleteMapping("/schedules/{scheduleId}")
    public ResponseEntity<ApiResponse<Void>> deleteSchedule(
            @PathVariable Long scheduleId,
            @AuthenticationPrincipal Long memberId) {
        command.deleteSchedule(scheduleId, memberId);
        return ResponseEntity.status(ScheduleSuccessCode.DELETED.getStatus())
                .body(ApiResponse.onSuccess(ScheduleSuccessCode.DELETED, null));
    }
}
