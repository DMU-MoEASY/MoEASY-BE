package com.moeasy.moeasybe.domain.schedule.service.command;

import com.moeasy.moeasybe.domain.group.entity.Group;
import com.moeasy.moeasybe.domain.schedule.converter.ScheduleConverter;
import com.moeasy.moeasybe.domain.schedule.dto.request.ScheduleReqDTO;
import com.moeasy.moeasybe.domain.schedule.dto.response.ScheduleResDTO;
import com.moeasy.moeasybe.domain.schedule.entity.Schedule;
import com.moeasy.moeasybe.domain.schedule.enums.ScheduleStatus;
import com.moeasy.moeasybe.domain.schedule.exception.ScheduleException;
import com.moeasy.moeasybe.domain.schedule.exception.code.ScheduleErrorCode;
import com.moeasy.moeasybe.domain.schedule.repository.ScheduleRepository;
import com.moeasy.moeasybe.domain.schedule.service.support.ScheduleAccess;
import com.moeasy.moeasybe.domain.schedule.service.support.ScheduleInput;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ScheduleCommandService {
    private final ScheduleRepository schedules;
    private final ScheduleAccess access;
    private final ScheduleInput input;
    private final Clock clock;
    private final org.springframework.context.ApplicationEventPublisher events;

    @Transactional
    public ScheduleResDTO.Created createSchedule(Long groupId, ScheduleReqDTO.Create request, Long memberId) {
        Group group = access.require(groupId, memberId, true, false);
        input.validate(request, now());
        return ScheduleConverter.toCreated(schedules.save(ScheduleConverter.toEntity(group, request)));
    }

    @Transactional
    public ScheduleResDTO.Detail updateSchedule(Long id, ScheduleReqDTO.Update request, Long memberId) {
        Schedule s = lock(id, memberId, false);
        LocalDateTime now = now();
        requireEditable(s, now);
        ScheduleReqDTO.Create r = input.merge(s, request);
        input.validate(r, now);
        s.update(r.title(), r.description(), r.startsAt(), r.endsAt(), r.locationName(), r.locationAddress(),
                r.latitude(), r.longitude(), r.placeProvider(), r.placeId());
        schedules.flush();
        changed(s, "SCHEDULE_CHANGED");
        return ScheduleConverter.toDetail(s);
    }

    @Transactional
    public void cancelSchedule(Long id, Long memberId) {
        Schedule s = lock(id, memberId, false);
        requireEditable(s, now());
        s.cancel();
        changed(s, "SCHEDULE_CANCELLED");
    }

    @Transactional
    public void deleteSchedule(Long id, Long memberId) {
        Schedule s = lock(id, memberId, true);
        s.softDelete(now());
        changed(s, "SCHEDULE_DELETED");
    }

    private void changed(Schedule s, String type) {
        events.publishEvent(new com.moeasy.moeasybe.domain.schedule.realtime.AvailabilityChanged(s.getGroup().getId(), s.getId(), type));
    }

    private Schedule lock(Long id, Long memberId, boolean ownerOnly) {
        if (id == null || memberId == null) throw new IllegalArgumentException("필수 인자가 누락되었습니다.");
        Long groupId = schedules.findGroupId(id)
                .orElseThrow(() -> new ScheduleException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));
        // 모임 폐쇄와 같은 순서로 잠근다. 일정은 모임 잠금 뒤에 최신 상태로 조회한다.
        access.require(groupId, memberId, true, ownerOnly);
        return schedules.findByIdForUpdate(id)
                .orElseThrow(() -> new ScheduleException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));
    }
    private void requireEditable(Schedule s, LocalDateTime now) {
        if (s.getStatus() != ScheduleStatus.SCHEDULED || !s.getStartsAt().isAfter(now)) {
            throw new ScheduleException(ScheduleErrorCode.SCHEDULE_NOT_EDITABLE);
        }
    }
    private LocalDateTime now() { return LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Seoul"))); }
}
