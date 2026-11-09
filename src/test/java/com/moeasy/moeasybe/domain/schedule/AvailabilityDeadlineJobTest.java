package com.moeasy.moeasybe.domain.schedule;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.moeasy.moeasybe.domain.schedule.realtime.AvailabilityDeadlineJob;
import com.moeasy.moeasybe.domain.schedule.repository.AvailabilityPollRepository;
import com.moeasy.moeasybe.domain.schedule.service.command.AvailabilityCommandService;
import com.moeasy.moeasybe.domain.schedule.service.support.AvailabilitySupport;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class AvailabilityDeadlineJobTest {
    @Mock private AvailabilityPollRepository polls;
    @Mock private AvailabilityCommandService command;
    @Mock private AvailabilitySupport support;

    @Test
    @DisplayName("마감 실패는 예외를 로그에 남기고 다음 일정을 계속 처리한다")
    void closeDue_failedSchedule_logsCauseAndContinues() {
        // given
        LocalDateTime now = LocalDateTime.of(2026, 11, 10, 12, 0);
        when(support.now()).thenReturn(now);
        when(polls.findDueScheduleIds(now, PageRequest.of(0, 100))).thenReturn(List.of(10L, 20L));
        RuntimeException failure = new IllegalStateException("deadline failure");
        doThrow(failure).when(command).closeDue(10L);
        AvailabilityDeadlineJob job = new AvailabilityDeadlineJob(polls, command, support);
        Logger logger = (Logger) LoggerFactory.getLogger(AvailabilityDeadlineJob.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            // when
            job.closeDue();

            // then
            verify(command).closeDue(20L);
            assertEquals(1, appender.list.size());
            ILoggingEvent event = appender.list.getFirst();
            assertAll(() -> assertTrue(event.getFormattedMessage().contains("scheduleId=10")),
                    () -> assertNotNull(event.getThrowableProxy()),
                    () -> assertEquals(IllegalStateException.class.getName(), event.getThrowableProxy().getClassName()),
                    () -> assertEquals("deadline failure", event.getThrowableProxy().getMessage()));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
