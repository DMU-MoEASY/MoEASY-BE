package com.moeasy.moeasybe.domain.schedule.service.query;

import com.moeasy.moeasybe.domain.group.enums.GroupStatus;
import com.moeasy.moeasybe.domain.schedule.converter.AvailabilityConverter;
import com.moeasy.moeasybe.domain.schedule.dto.response.AvailabilityResDTO;
import com.moeasy.moeasybe.domain.schedule.enums.*;
import com.moeasy.moeasybe.domain.schedule.service.support.AvailabilitySupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service @RequiredArgsConstructor
public class AvailabilityQueryService {
    private final AvailabilitySupport support;
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AvailabilityResDTO.Detail getAvailability(Long scheduleId, Long memberId) {
        var s = support.snapshot(support.access(scheduleId, memberId, false, false, false));
        boolean editable = s.access().group().getStatus() == GroupStatus.ACTIVE
                && s.access().schedule().getStatus() == ScheduleStatus.SCHEDULED
                && s.access().schedule().getStartsAt().isAfter(s.now())
                && s.poll().effectiveStatus(s.now()) == AvailabilityStatus.OPEN;
        return AvailabilityConverter.toDetail(s, editable);
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AvailabilityResDTO.Poll getPoll(Long scheduleId, Long memberId) {
        return getAvailability(scheduleId, memberId).poll();
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AvailabilityResDTO.Me getMe(Long scheduleId, Long memberId) {
        return getAvailability(scheduleId, memberId).me();
    }
}
