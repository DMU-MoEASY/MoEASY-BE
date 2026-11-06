package com.moeasy.moeasybe.domain.group.service;

import java.time.temporal.ChronoUnit;
import com.moeasy.moeasybe.domain.group.repository.GroupTierRepository;
import com.moeasy.moeasybe.domain.group.repository.GroupTierRepository.Job;
import com.moeasy.moeasybe.domain.group.service.command.GroupTierWorker;
import java.time.*;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class GroupTierScheduler {
    private final GroupTierRepository jobs;
    private final GroupTierWorker worker;
    private final Clock clock;

    @Scheduled(cron = "${group.tier.recovery-cron:0 * * * * *}", zone = "Asia/Seoul")
    public void recover() {
        long start = System.nanoTime();
        LocalDate day = now().toLocalDate();
        LocalDate planned = jobs.plannedThrough();
        jobs.skipOlder(day);
        if (planned.isBefore(day)) enqueue(day, "PENDING");
        long after = 0;
        int processed = 0;
        while (true) {
            var page = jobs.ready(day, now(), after);
            if (page.isEmpty()) break;
            for (long group : page) {
                Job job = new Job(day, group, UUID.randomUUID().toString());
                if (!jobs.claim(job, now())) continue;
                try {
                    worker.freeze(job);
                    worker.apply(job);
                    processed++;
                } catch (RuntimeException ex) {
                    jobs.fail(job, ex.getClass().getSimpleName(), now().plusMinutes(1).truncatedTo(ChronoUnit.MINUTES));
                    log.error("Tier calculation failed basis={} group={}", day, group, ex);
                }
            }
            after = page.getLast();
        }
        // Keep the checkpoint behind unfinished pages, even if the latest day already completed.
        for (LocalDate missing = planned.plusDays(1); missing.isBefore(day); missing = missing.plusDays(1)) {
            enqueue(missing, "SKIPPED");
            jobs.finishPlanning(missing);
        }
        if (planned.isBefore(day)) jobs.finishPlanning(day);
        if (processed>0) log.info("Tier calculation basis={} processed={} elapsedMs={}", day, processed, (System.nanoTime()-start)/1_000_000);
    }

    private void enqueue(LocalDate day, String state) {
        long after = 0;
        while (true) {
            var ids = jobs.missing(day, after);
            if (ids.isEmpty()) return;
            jobs.enqueue(day, ids, state);
            after = ids.getLast();
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Seoul")));
    }
}
