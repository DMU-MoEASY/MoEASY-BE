package com.moeasy.moeasybe.domain.group.service.command;

import com.moeasy.moeasybe.domain.group.entity.Group;
import com.moeasy.moeasybe.domain.group.enums.GroupStatus;
import com.moeasy.moeasybe.domain.group.repository.*;
import com.moeasy.moeasybe.domain.group.repository.GroupTierRepository.Job;
import com.moeasy.moeasybe.domain.group.service.GroupTierPolicy;
import com.moeasy.moeasybe.domain.schedule.repository.ParticipationRepository;
import java.time.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class GroupTierWorker {
    private final GroupRepository groups;
    private final GroupTierRepository jobs;
    private final ParticipationRepository participation;
    private final Clock clock;

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void freeze(Job job) {
        Group group = groups.findByIdForUpdate(job.group()).orElseThrow();
        var input = jobs.owned(job);
        if (input == null || input.frozen()) return;
        if (group.getDeletedAt() != null || group.getStatus() == GroupStatus.CLOSED) {
            jobs.skip(job);
            return;
        }
        var at = LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Seoul")));
        var until = job.basis().atStartOfDay();
        var from = until.minusDays(56);
        participation.seal(job.group(), from, until, null);
        jobs.freeze(job, at, participation.totals(job.group(), from, until), jobs.active(job.group()));
    }

    @Transactional
    public void apply(Job job) {
        groups.findByIdForUpdate(job.group()).orElseThrow();
        var input = jobs.owned(job);
        if (input == null || !input.frozen()) return;
        jobs.apply(job, GroupTierPolicy.calculate(input.activities(), input.evaluated(), input.numerator(), input.denominator(), input.members()));
    }
}
