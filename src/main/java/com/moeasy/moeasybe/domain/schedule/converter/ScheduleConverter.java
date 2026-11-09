package com.moeasy.moeasybe.domain.schedule.converter;

import com.moeasy.moeasybe.domain.group.entity.Group;
import com.moeasy.moeasybe.domain.schedule.dto.request.ScheduleReqDTO;
import com.moeasy.moeasybe.domain.schedule.dto.response.ScheduleResDTO;
import com.moeasy.moeasybe.domain.schedule.entity.Schedule;
import java.time.LocalDateTime;
import java.util.List;

public final class ScheduleConverter {
    private ScheduleConverter() {
    }

    public static Schedule toEntity(Group group, ScheduleReqDTO.Create request) {
        return Schedule.builder()
                .group(group)
                .title(request.title())
                .description(request.description())
                .startsAt(request.startsAt())
                .endsAt(request.endsAt())
                .locationName(request.locationName())
                .locationAddress(request.locationAddress())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .placeProvider(request.placeProvider())
                .placeId(request.placeId())
                .build();
    }

    public static ScheduleResDTO.Created toCreated(Schedule schedule) {
        return ScheduleResDTO.Created.builder()
                .scheduleId(schedule.getId())
                .build();
    }

    public static ScheduleResDTO.Detail toDetail(Schedule schedule) {
        return ScheduleResDTO.Detail.builder()
                .scheduleId(schedule.getId())
                .groupId(schedule.getGroup().getId())
                .title(schedule.getTitle())
                .description(schedule.getDescription())
                .startsAt(schedule.getStartsAt())
                .endsAt(schedule.getEndsAt())
                .locationName(schedule.getLocationName())
                .locationAddress(schedule.getLocationAddress())
                .latitude(schedule.getLatitude())
                .longitude(schedule.getLongitude())
                .placeProvider(schedule.getPlaceProvider())
                .placeId(schedule.getPlaceId())
                .status(schedule.getStatus())
                .createdAt(schedule.getCreatedAt())
                .updatedAt(schedule.getUpdatedAt())
                .build();
    }

    public static ScheduleResDTO.Summary toSummary(Schedule schedule) {
        return ScheduleResDTO.Summary.builder()
                .scheduleId(schedule.getId())
                .groupId(schedule.getGroup().getId())
                .title(schedule.getTitle())
                .startsAt(schedule.getStartsAt())
                .endsAt(schedule.getEndsAt())
                .locationName(schedule.getLocationName())
                .status(schedule.getStatus())
                .build();
    }

    public static ScheduleResDTO.Page toPage(LocalDateTime from, LocalDateTime to,
            List<Schedule> items, String cursor, boolean hasNext) {
        return ScheduleResDTO.Page.builder()
                .from(from)
                .to(to)
                .items(items.stream().map(ScheduleConverter::toSummary).toList())
                .nextCursor(cursor)
                .hasNext(hasNext)
                .build();
    }
}
