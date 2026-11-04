package com.moeasy.moeasybe.domain.schedule.realtime;
import com.moeasy.moeasybe.domain.schedule.repository.AvailabilityPollRepository;
import com.moeasy.moeasybe.domain.schedule.service.command.AvailabilityCommandService;
import com.moeasy.moeasybe.domain.schedule.service.support.AvailabilitySupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor @Slf4j
public class AvailabilityDeadlineJob {
    private final AvailabilityPollRepository polls;
    private final AvailabilityCommandService command;
    private final AvailabilitySupport support;
    @Scheduled(fixedDelayString = "${availability.close-delay-ms:1000}", initialDelayString = "${availability.close-delay-ms:1000}")
    public void closeDue() {
        for (Long id : polls.findDueScheduleIds(support.now(), PageRequest.of(0, 100))) {
            try { command.closeDue(id); }
            catch (RuntimeException e) { log.warn("Availability deadline retry required: scheduleId={}", id); }
        }
    }
}
