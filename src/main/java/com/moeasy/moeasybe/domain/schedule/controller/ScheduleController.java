package com.moeasy.moeasybe.domain.schedule.controller;

import com.moeasy.moeasybe.domain.schedule.controller.docs.ScheduleControllerDocs;
import com.moeasy.moeasybe.domain.schedule.dto.request.ScheduleReqDTO;
import com.moeasy.moeasybe.domain.schedule.dto.response.ScheduleResDTO;
import com.moeasy.moeasybe.domain.schedule.exception.code.ScheduleSuccessCode;
import com.moeasy.moeasybe.domain.schedule.service.command.ScheduleCommandService;
import com.moeasy.moeasybe.domain.schedule.service.query.ScheduleQueryService;
import com.moeasy.moeasybe.global.apiPayload.ApiResponse;
import java.beans.PropertyEditorSupport;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
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
    private static final DateTimeFormatter QUERY_TIME_FORMATTER = DateTimeFormatter
            .ofPattern("uuuu-MM-dd'T'HH:mm:ss").withResolverStyle(ResolverStyle.STRICT);

    private final ScheduleQueryService query;
    private final ScheduleCommandService command;

    @InitBinder({"from", "to"})
    void bindQueryTimes(WebDataBinder binder) {
        binder.registerCustomEditor(LocalDateTime.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                if (text.length() != 19) {
                    throw new IllegalArgumentException("일정 조회 시각은 yyyy-MM-dd'T'HH:mm:ss 형식이어야 합니다.");
                }
                try {
                    setValue(LocalDateTime.parse(text, QUERY_TIME_FORMATTER));
                } catch (DateTimeParseException exception) {
                    throw new IllegalArgumentException("유효한 일정 조회 시각이어야 합니다.", exception);
                }
            }
        });
    }

    @Override
    @GetMapping("/groups/{groupId}/schedules")
    public ResponseEntity<ApiResponse<ScheduleResDTO.Page>> getSchedules(
            @PathVariable Long groupId,
            @RequestParam(required = false) LocalDateTime from,
            @RequestParam(required = false) LocalDateTime to,
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
