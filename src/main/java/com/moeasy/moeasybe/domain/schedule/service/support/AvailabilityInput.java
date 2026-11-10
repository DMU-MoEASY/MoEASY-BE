package com.moeasy.moeasybe.domain.schedule.service.support;

import com.moeasy.moeasybe.domain.schedule.dto.request.AvailabilityReqDTO;
import com.moeasy.moeasybe.domain.schedule.entity.AvailabilityPoll;
import com.moeasy.moeasybe.domain.schedule.exception.AvailabilityException;
import com.moeasy.moeasybe.domain.schedule.exception.code.AvailabilityErrorCode;
import java.time.*;
import java.util.*;

public final class AvailabilityInput {
    private AvailabilityInput() { }
    public static void validate(AvailabilityReqDTO.Configure r, LocalDateTime now) {
        if (r == null || r.startDate() == null || r.dayCount() == null || r.startHour() == null
                || r.endHour() == null || r.voteDeadlineAt() == null) invalid();
        if (!Set.of(3, 5, 7, 14).contains(r.dayCount()) || r.startHour() < 0 || r.endHour() > 24
                || r.startHour() >= r.endHour() || !r.voteDeadlineAt().isAfter(now)
                || r.startDate().getYear() < 1000 || r.startDate().getYear() > 9999 || r.startDate().plusDays(r.dayCount()).getYear() > 9999
                || r.voteDeadlineAt().getNano() != 0 || r.voteDeadlineAt().getYear() < 1000 || r.voteDeadlineAt().getYear() > 9999) invalid();
    }
    public static List<LocalDateTime> slots(AvailabilityPoll p) {
        List<LocalDateTime> result = new ArrayList<>();
        for (int day = 0; day < p.getDayCount(); day++) {
            LocalDateTime midnight = p.getStartDate().plusDays(day).atStartOfDay();
            for (int minute = p.getStartHour() * 60; minute < p.getEndHour() * 60; minute += 30) {
                result.add(midnight.plusMinutes(minute));
            }
        }
        return List.copyOf(result);
    }
    public static void invalid() { throw new AvailabilityException(AvailabilityErrorCode.INVALID_INPUT); }
}
