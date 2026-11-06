package com.moeasy.moeasybe.domain.schedule.service.query;

import com.moeasy.moeasybe.domain.group.enums.GroupStatus;
import com.moeasy.moeasybe.domain.schedule.dto.response.ParticipationResDTO.*;
import com.moeasy.moeasybe.domain.schedule.enums.ScheduleStatus;
import com.moeasy.moeasybe.domain.schedule.repository.ParticipationRepository;
import com.moeasy.moeasybe.domain.schedule.service.support.ScheduleAccess;
import com.moeasy.moeasybe.domain.schedule.service.support.ScheduleAccess.Access;
import java.time.LocalDateTime;
import com.moeasy.moeasybe.domain.schedule.converter.ParticipationConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ParticipationQueryService {
    private final ScheduleAccess support;
    private final ParticipationRepository repository;

    public Rsvp rsvp(long id, long member) {
        Access access = support.access(id, member, false, false, false);
        var schedule = access.schedule();
        var members = repository.responses(id, access.group().getId());
        return ParticipationConverter.toRsvp(schedule, access.actor().getId(), members,
                writable(access) && support.now().isBefore(schedule.getStartsAt()));
    }

    public Attendance attendance(long id, long member, LocalDateTime at) {
        return detail(support.access(id, member, false, true, false), at);
    }

    public Attendance detail(Access access, LocalDateTime at) {
        var schedule = access.schedule();
        long id = schedule.getId();
        var session = repository.session(id);
        boolean ended = !at.isBefore(schedule.getEndsAt());
        boolean available = writable(access) && !at.isBefore(schedule.getStartsAt());
        return new Attendance(id, session.completed() ? "COMPLETED" : "PENDING", session.version(),
                available && !session.completed(), available && ended && !session.completed(), available && ended && session.completed(),
                ended, ended && repository.recoverable(id), repository.records(id, access.group().getId(), ended, schedule.getStartsAt(), session.id()));
    }

    private boolean writable(Access access) {
        return access.group().getStatus() == GroupStatus.ACTIVE && access.schedule().getStatus() == ScheduleStatus.SCHEDULED;
    }
}
