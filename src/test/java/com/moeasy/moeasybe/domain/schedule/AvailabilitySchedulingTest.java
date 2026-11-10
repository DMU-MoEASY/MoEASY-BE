package com.moeasy.moeasybe.domain.schedule;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.moeasy.moeasybe.domain.schedule.realtime.AvailabilityDeadlineJob;
import com.moeasy.moeasybe.domain.schedule.realtime.AvailabilitySocketHandler;
import com.moeasy.moeasybe.domain.schedule.realtime.AvailabilitySubscriptionAccess;
import com.moeasy.moeasybe.domain.schedule.realtime.AvailabilitySubscriptionAccess.Subscription;
import com.moeasy.moeasybe.domain.schedule.repository.AvailabilityPollRepository;
import com.moeasy.moeasybe.domain.schedule.service.command.AvailabilityCommandService;
import com.moeasy.moeasybe.domain.schedule.service.support.AvailabilitySupport;
import com.moeasy.moeasybe.global.config.AvailabilitySchedulingConfig;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.WebSocketSession;

@ExtendWith(MockitoExtension.class)
class AvailabilitySchedulingTest {
    @Mock private AvailabilitySubscriptionAccess access;
    @Mock private AvailabilityPollRepository polls;
    @Mock private AvailabilityCommandService command;
    @Mock private AvailabilitySupport support;

    @Test
    @DisplayName("소켓 권한 조회가 지연되어도 마감 작업은 별도 스케줄러에서 실행된다")
    void scheduled_blockedSocketCheck_doesNotDelayDeadline() throws Exception {
        // given
        Instant now = Instant.parse("2026-11-10T03:00:00Z");
        CountDownLatch socketChecking = new CountDownLatch(1);
        CountDownLatch releaseSocket = new CountDownLatch(1);
        CountDownLatch deadlineProcessed = new CountDownLatch(1);
        AvailabilitySocketHandler handler = new AvailabilitySocketHandler(access, Clock.fixed(now, ZoneOffset.UTC));
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("slow");
        when(session.getAttributes()).thenReturn(Map.of("groupId", 1L, "scheduleId", 2L,
                "memberId", 3L, "expiresAt", now.plusSeconds(60)));
        var subscription = new Subscription(2L, 3L);
        when(access.findAuthorized(Set.of(subscription))).thenReturn(Set.of(subscription));
        handler.afterConnectionEstablished(session);
        when(access.findAuthorized(Set.of(subscription))).thenAnswer(invocation -> {
            socketChecking.countDown();
            if (!releaseSocket.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Socket check was not released");
            }
            return Set.of(subscription);
        });
        when(support.now()).thenReturn(LocalDateTime.ofInstant(now, ZoneOffset.UTC));
        when(polls.findDueScheduleIds(any(LocalDateTime.class), eq(org.springframework.data.domain.PageRequest.of(0, 100))))
                .thenAnswer(invocation -> socketChecking.getCount() == 0 ? List.of(2L) : List.of());
        doAnswer(invocation -> {
            deadlineProcessed.countDown();
            return null;
        }).when(command).closeDue(2L);
        AvailabilityDeadlineJob job = new AvailabilityDeadlineJob(polls, command, support);
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("scheduling-test",
                Map.of("availability.close-delay-ms", "10", "availability.socket-check-ms", "10")));
        context.register(EnableSchedulingConfig.class, AvailabilitySchedulingConfig.class);
        context.registerBean(AvailabilitySocketHandler.class, () -> handler);
        context.registerBean(AvailabilityDeadlineJob.class, () -> job);
        try {
            // when
            context.refresh();
            boolean socketStarted = socketChecking.await(2, TimeUnit.SECONDS);
            boolean deadlineRanWhileSocketBlocked = deadlineProcessed.await(2, TimeUnit.SECONDS);

            // then
            assertAll(() -> assertTrue(socketStarted),
                    () -> assertTrue(deadlineRanWhileSocketBlocked),
                    () -> assertEquals(1, releaseSocket.getCount()));
        } finally {
            releaseSocket.countDown();
            context.close();
        }
    }

    @Configuration
    @EnableScheduling
    static class EnableSchedulingConfig { }
}
