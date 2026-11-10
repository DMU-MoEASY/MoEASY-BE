package com.moeasy.moeasybe.domain.group.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.moeasy.moeasybe.domain.group.repository.GroupTierRepository;
import com.moeasy.moeasybe.domain.group.repository.GroupTierRepository.Job;
import com.moeasy.moeasybe.domain.group.service.command.GroupTierWorker;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class GroupTierSchedulerTest {
    @Test
    void restartPrioritizesLatestMidnightAndRecordsMissedRoundsAfterPartialFailure() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), ZoneOffset.UTC);
        LocalDate today = LocalDate.of(2026, 11, 11);
        var jobs = mock(GroupTierRepository.class);
        var worker = mock(GroupTierWorker.class);
        when(jobs.plannedThrough()).thenReturn(today.minusDays(2));
        when(jobs.missing(any(), eq(0L))).thenReturn(List.of(1L, 2L));
        when(jobs.ready(today, today.atStartOfDay(), 0)).thenReturn(List.of(1L, 2L));
        when(jobs.claim(any(), any())).thenReturn(true);
        doThrow(new IllegalStateException()).when(worker).freeze(argThat(job -> job.group() == 1));

        new GroupTierScheduler(jobs, worker, clock).recover();

        var order = inOrder(jobs, worker);
        order.verify(jobs).enqueue(today, List.of(1L, 2L), "PENDING");
        order.verify(jobs).fail(argThat(job -> job.group() == 1), eq("IllegalStateException"),
                eq(today.atStartOfDay().plusMinutes(1)));
        order.verify(worker).apply(argThat((Job job) -> job.basis().equals(today) && job.group() == 2));
        order.verify(jobs).enqueue(today.minusDays(1), List.of(1L, 2L), "SKIPPED");
        order.verify(jobs).finishPlanning(today);
    }
}
