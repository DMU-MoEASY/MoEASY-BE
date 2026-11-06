package com.moeasy.moeasybe.domain.schedule.service.command;

import com.moeasy.moeasybe.domain.schedule.repository.ScheduleRepository;
import com.moeasy.moeasybe.domain.group.repository.GroupRepository;
import com.moeasy.moeasybe.domain.schedule.dto.request.ParticipationReqDTO.*;
import com.moeasy.moeasybe.domain.schedule.dto.response.ParticipationResDTO.*;
import com.moeasy.moeasybe.domain.schedule.enums.ScheduleStatus;
import com.moeasy.moeasybe.domain.schedule.exception.ScheduleException;
import com.moeasy.moeasybe.domain.schedule.exception.code.ScheduleErrorCode;
import com.moeasy.moeasybe.domain.schedule.repository.ParticipationRepository;
import com.moeasy.moeasybe.domain.schedule.service.query.ParticipationQueryService;
import com.moeasy.moeasybe.domain.schedule.service.support.ScheduleAccess;
import com.moeasy.moeasybe.domain.schedule.service.support.ScheduleAccess.Access;
import java.time.LocalDateTime;
import java.util.HashSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(isolation = Isolation.READ_COMMITTED)
public class ParticipationCommandService {
    private final ScheduleAccess support;
    private final GroupRepository groups;
    private final ScheduleRepository schedules;
    private final ParticipationRepository repository;
    private final ParticipationQueryService query;

    public Response respond(long id, long member, ResponseStatus status) {
        var access = support.access(id, member, true, false, false);
        LocalDateTime at = support.now();
        if (access.schedule().getStatus() != ScheduleStatus.SCHEDULED || !at.isBefore(access.schedule().getStartsAt()))
            throw new ScheduleException(ScheduleErrorCode.SCHEDULE_NOT_EDITABLE);
        if (status == null) {
            repository.withdraw(id, access.actor().getId());
            return null;
        }
        repository.respond(id, access.actor().getId(), status.name(), at);
        return new Response(status.name());
    }

    public LocalDateTime prepareRoster(long id, long member) {
        long group = schedules.findGroupId(id).orElseThrow(() -> new ScheduleException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));
        groups.findByIdForUpdate(group).orElseThrow();
        var access = support.access(id, member, false, true, false);
        var at = support.now();
        seal(access, at);
        return at;
    }

    private void seal(Access access, LocalDateTime at) {
        if (!at.isBefore(access.schedule().getEndsAt())) {
            repository.seal(access.group().getId(), access.schedule().getStartsAt(), at, access.schedule().getId());
        }
    }

    public Attendance replace(long id, long member, Replace request) {
        var access = support.access(id, member, true, true, false);
        var at = support.now();
        seal(access, at);
        var detail = query.detail(access, at);
        require(detail.version() == request.version() && detail.canEdit());
        var targets = new HashSet<Long>();
        detail.members().forEach(m -> targets.add(m.groupMemberId()));
        for (Entry entry : request.entries()) {
            if (!targets.remove(entry.groupMemberId())) throw new IllegalArgumentException("중복되거나 명단 밖의 멤버입니다.");
        }
        repository.replace(sessionId(id), request.entries(), access.actor().getId(), at);
        return query.detail(access, at);
    }

    public Attendance setCompleted(long id, long member, long version, boolean completed) {
        var access = support.access(id, member, true, true, false);
        var at = support.now();
        require(access.schedule().getStatus() == ScheduleStatus.SCHEDULED && !at.isBefore(access.schedule().getEndsAt()));
        seal(access, at);
        var session = repository.session(id);
        require(session.version() == version);
        if (completed != session.completed()) {
            if (completed) repository.complete(sessionId(id), id, access.actor().getId(), at);
            else repository.reopen(session.id());
        }
        return query.detail(access, at);
    }

    private long sessionId(long id) {
        var session = repository.session(id);
        return session.id() == 0 ? repository.createSession(id).id() : session.id();
    }

    private void require(boolean condition) {
        if (!condition) throw new ScheduleException(ScheduleErrorCode.ATTENDANCE_CONFLICT);
    }
}
