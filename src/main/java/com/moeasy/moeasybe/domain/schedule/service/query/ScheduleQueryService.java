package com.moeasy.moeasybe.domain.schedule.service.query;

import com.moeasy.moeasybe.domain.schedule.converter.ScheduleConverter;
import com.moeasy.moeasybe.domain.schedule.dto.response.ScheduleResDTO;
import com.moeasy.moeasybe.domain.schedule.entity.Schedule;
import com.moeasy.moeasybe.domain.schedule.exception.ScheduleException;
import com.moeasy.moeasybe.domain.schedule.exception.code.ScheduleErrorCode;
import com.moeasy.moeasybe.domain.schedule.repository.ScheduleRepository;
import com.moeasy.moeasybe.domain.schedule.service.support.ScheduleAccess;
import com.moeasy.moeasybe.domain.schedule.service.support.ScheduleCursor;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ScheduleQueryService {
    private final ScheduleRepository schedules;
    private final ScheduleAccess access;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ScheduleResDTO.Detail getSchedule(Long id, Long memberId) {
        if (id == null || memberId == null) throw new IllegalArgumentException("필수 인자가 누락되었습니다.");
        Schedule s = schedules.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ScheduleException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));
        access.require(s.getGroup().getId(), memberId, false, false);
        return ScheduleConverter.toDetail(s);
    }

    @Transactional(readOnly = true)
    public ScheduleResDTO.Page getSchedules(Long groupId, LocalDateTime from, LocalDateTime to,
            Integer size, String cursor, Long memberId) {
        access.require(groupId, memberId, false, false);
        int limit = size == null ? 20 : size;
        if (limit < 1 || limit > 100 || (from == null) != (to == null)) {
            throw new IllegalArgumentException("잘못된 조회 조건입니다.");
        }
        ScheduleCursor c = cursor == null ? null : ScheduleCursor.decode(cursor);
        if (c != null && (!c.groupId().equals(groupId) || from != null
                && (!c.from().equals(from) || !c.to().equals(to)))) {
            throw new IllegalArgumentException("커서의 조회 조건이 다릅니다.");
        }
        if (from == null) {
            from = c == null ? LocalDate.now(clock.withZone(ZoneId.of("Asia/Seoul")))
                    .withDayOfMonth(1).atStartOfDay() : c.from();
            to = c == null ? from.plusMonths(1) : c.to();
        }
        if (!from.isBefore(to) || from.getYear() < 1000 || to.getYear() > 9999
                || from.getNano() != 0 || to.getNano() != 0) throw new IllegalArgumentException("잘못된 조회 기간입니다.");
        List<Schedule> found = schedules.findWindow(groupId, from, to,
                c == null ? null : c.createdAt(), c == null ? null : c.id(), PageRequest.of(0, limit + 1));
        boolean hasNext = found.size() > limit;
        List<Schedule> items = found.subList(0, Math.min(limit, found.size()));
        String next = null;
        if (hasNext) {
            Schedule last = items.getLast();
            next = new ScheduleCursor(groupId, from, to, last.getCreatedAt(), last.getId()).encode();
        }
        return ScheduleConverter.toPage(from, to, items, next, hasNext);
    }
}
