package com.moeasy.moeasybe.domain.schedule;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.moeasy.moeasybe.domain.schedule.realtime.*;
import com.moeasy.moeasybe.domain.schedule.realtime.AvailabilitySubscriptionAccess.Subscription;
import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.*;

@ExtendWith(MockitoExtension.class)
class AvailabilitySocketHandlerTest {
    @Mock AvailabilitySubscriptionAccess access;
    private static final Instant NOW = Instant.parse("2026-11-04T03:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private AvailabilitySocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AvailabilitySocketHandler(access, clock);
    }

    @Test
    @DisplayName("JWT 만료 시 변경 내용을 보내지 않고 연결을 종료한다")
    void connect_expiredJwt_closesWithoutSending() throws Exception {
        // given
        var session = session("expired", 1L, 2L, 3L, NOW);
        // when
        handler.afterConnectionEstablished(session);
        // then
        verify(session).close(CloseStatus.POLICY_VIOLATION);
        verify(session, never()).sendMessage(any());
        verifyNoInteractions(access);
    }

    @Test
    @DisplayName("같은 회원의 다중 연결은 하나의 권한 키로 묶고 다른 회원은 각각 판정한다")
    void invalidate_multipleSessions_batchesDistinctSubscriptions() throws Exception {
        // given
        var allowed = new Subscription(2L, 3L);
        var revoked = new Subscription(2L, 4L);
        var first = connect("first", 1L, allowed);
        var duplicate = connect("duplicate", 1L, allowed);
        var other = connect("other", 1L, revoked);
        when(access.findAuthorized(Set.of(allowed, revoked))).thenReturn(Set.of(allowed));
        // when
        handler.invalidate(new AvailabilityChanged(1L, 2L, "ROSTER_CHANGED"));
        // then
        verify(access).findAuthorized(Set.of(allowed, revoked));
        verifyNotification(first, "ROSTER_CHANGED");
        verifyNotification(duplicate, "ROSTER_CHANGED");
        verify(other).close(CloseStatus.POLICY_VIOLATION);
        verify(other, never()).sendMessage(any());
    }

    @Test
    @DisplayName("주기 검사에서 권한을 잃은 연결만 닫고 다음 검사 때 권한을 다시 조회한다")
    void revalidate_revokedPermission_rechecksOnEachSweep() throws Exception {
        // given
        var active = new Subscription(2L, 3L);
        var revoked = new Subscription(5L, 4L);
        var first = connect("active", 1L, active);
        var other = connect("revoked", 6L, revoked);
        when(access.findAuthorized(Set.of(active, revoked))).thenReturn(Set.of(active));
        when(access.findAuthorized(Set.of(active))).thenReturn(Set.of());
        // when
        handler.revalidate();
        handler.revalidate();
        // then
        verify(access).findAuthorized(Set.of(active, revoked));
        verify(access).findAuthorized(Set.of(active));
        verify(other).close(CloseStatus.POLICY_VIOLATION);
        verify(first).close(CloseStatus.POLICY_VIOLATION);
        verify(first, never()).sendMessage(any());
    }

    @Test
    @DisplayName("모임 전체 이벤트도 해당 모임의 연결만 검사하고 알림마다 권한을 다시 읽는다")
    void invalidate_groupEvent_excludesOtherGroupsAndRechecksPermission() throws Exception {
        // given
        var active = new Subscription(2L, 3L);
        var untouched = new Subscription(5L, 4L);
        var first = connect("active", 1L, active);
        var other = connect("other", 6L, untouched);
        when(access.findAuthorized(Set.of(active))).thenReturn(Set.of(active)).thenReturn(Set.of());
        // when
        handler.invalidate(new AvailabilityChanged(1L, null, "ROSTER_CHANGED"));
        handler.invalidate(new AvailabilityChanged(1L, null, "GROUP_CLOSED"));
        // then
        verify(access, times(2)).findAuthorized(Set.of(active));
        verifyNotification(first, "ROSTER_CHANGED");
        verify(first).close(CloseStatus.POLICY_VIOLATION);
        verify(other, never()).sendMessage(any());
        verify(other, never()).close(any());
    }

    @Test
    @DisplayName("조회 중 JWT가 만료되면 권한이 있어도 알림을 보내지 않는다")
    void invalidate_tokenExpiresDuringQuery_closesWithoutSending() throws Exception {
        // given
        Clock moving = mock(Clock.class);
        when(moving.instant()).thenReturn(NOW);
        handler = new AvailabilitySocketHandler(access, moving);
        var subscription = new Subscription(2L, 3L);
        var session = connect("expires", 1L, subscription);
        when(access.findAuthorized(Set.of(subscription))).thenAnswer(invocation -> {
            when(moving.instant()).thenReturn(NOW.plusSeconds(60));
            return Set.of(subscription);
        });
        // when
        handler.invalidate(new AvailabilityChanged(1L, 2L, "RESPONSE_CHANGED"));
        // then
        verify(session).close(CloseStatus.POLICY_VIOLATION);
        verify(session, never()).sendMessage(any());
    }

    @Test
    @DisplayName("앞선 연결 전송 중 다른 연결의 JWT가 만료되면 전송 직전에 차단한다")
    void invalidate_tokenExpiresDuringSending_checksExpiryBeforeEverySend() throws Exception {
        // given
        var current = new AtomicReference<>(NOW);
        Clock moving = mock(Clock.class);
        when(moving.instant()).thenAnswer(invocation -> current.get());
        handler = new AvailabilitySocketHandler(access, moving);
        var subscription = new Subscription(2L, 3L);
        Runnable advanceOnSend = () -> current.set(NOW.plusSeconds(60));
        var first = connect("first", 1L, subscription, advanceOnSend);
        current.set(NOW);
        var second = connect("second", 1L, subscription, advanceOnSend);
        current.set(NOW);
        when(access.findAuthorized(Set.of(subscription))).thenReturn(Set.of(subscription));
        // when
        handler.invalidate(new AvailabilityChanged(1L, 2L, "RESPONSE_CHANGED"));
        // then
        ArgumentCaptor<WebSocketMessage<?>> messages = ArgumentCaptor.captor();
        verify(first, atMostOnce()).sendMessage(messages.capture());
        verify(second, atMostOnce()).sendMessage(messages.capture());
        assertEquals(1, messages.getAllValues().size());
        var closed = ArgumentCaptor.forClass(CloseStatus.class);
        verify(first, atMostOnce()).close(closed.capture());
        verify(second, atMostOnce()).close(closed.capture());
        assertEquals(List.of(CloseStatus.POLICY_VIOLATION), closed.getAllValues());
    }

    @Test
    @DisplayName("권한 조회 실패 시 대상 연결을 닫고 알림을 보내지 않는다")
    void invalidate_databaseFailure_closesWithoutSending() throws Exception {
        // given
        var subscription = new Subscription(2L, 3L);
        var session = connect("failure", 1L, subscription);
        when(access.findAuthorized(Set.of(subscription))).thenThrow(new IllegalStateException("unavailable"));
        // when
        handler.invalidate(new AvailabilityChanged(1L, 2L, "RESPONSE_CHANGED"));
        // then
        verify(session).close(CloseStatus.POLICY_VIOLATION);
        verify(session, never()).sendMessage(any());
    }


    @Test
    @DisplayName("동일 구독의 여러 연결은 각 주기 검사마다 한 번의 일괄 조회로 판정한다")
    void revalidate_duplicateSubscription_queriesOncePerPass() throws Exception {
        // given
        var subscription = new Subscription(2L, 3L);
        var first = connect("first", 1L, subscription);
        var duplicate = connect("duplicate", 1L, subscription);

        // when
        handler.revalidate();
        handler.revalidate();

        // then
        verify(access, times(2)).findAuthorized(Set.of(subscription));
        verify(first, never()).close(any());
        verify(duplicate, never()).close(any());
    }

    @Test
    @DisplayName("회원은 같아도 일정이 다르면 각각의 구독 권한을 판정한다")
    void revalidate_sameMemberOnDifferentSchedules_checksBothSubscriptions() throws Exception {
        // given
        var active = new Subscription(2L, 3L);
        var revoked = new Subscription(5L, 3L);
        var first = connect("first", 1L, active);
        var other = connect("other", 1L, revoked);
        when(access.findAuthorized(Set.of(active, revoked))).thenReturn(Set.of(active));

        // when
        handler.revalidate();

        // then
        verify(access).findAuthorized(Set.of(active, revoked));
        verify(first, never()).close(any());
        verify(other).close(CloseStatus.POLICY_VIOLATION);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    @DisplayName("동일 구독이어도 만료된 연결만 닫고 유효한 연결은 유지한다")
    void check_sameSubscriptionWithDifferentExpiry_closesOnlyExpiredSession(boolean notification) throws Exception {
        // given
        var current = new AtomicReference<>(NOW);
        Clock moving = mock(Clock.class);
        when(moving.instant()).thenAnswer(invocation -> current.get());
        handler = new AvailabilitySocketHandler(access, moving);
        var subscription = new Subscription(2L, 3L);
        var expired = connect("expired", 1L, subscription);
        var valid = session("valid", 1L, 2L, 3L, NOW.plusSeconds(120));
        handler.afterConnectionEstablished(valid);
        current.set(NOW.plusSeconds(60));
        clearInvocations(access, expired, valid);

        // when
        if (notification) handler.invalidate(new AvailabilityChanged(1L, 2L, "CHANGED"));
        else handler.revalidate();

        // then
        verify(access).findAuthorized(Set.of(subscription));
        verify(expired).close(CloseStatus.POLICY_VIOLATION);
        verify(expired, never()).sendMessage(any());
        verify(valid, never()).close(any());
        if (notification) verifyNotification(valid, "CHANGED");
        else verify(valid, never()).sendMessage(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    @DisplayName("구독 자격을 잃으면 중복 연결 모두 종료하고 알림을 보내지 않는다")
    void check_revokedSubscription_closesAllWithoutSending(boolean notification) throws Exception {
        // given
        var subscription = new Subscription(2L, 3L);
        var first = connect("first", 1L, subscription);
        var duplicate = connect("duplicate", 1L, subscription);
        when(access.findAuthorized(Set.of(subscription))).thenReturn(Set.of());

        // when
        if (notification) handler.invalidate(new AvailabilityChanged(1L, 2L, "ROSTER_CHANGED"));
        else handler.revalidate();

        // then
        verify(access).findAuthorized(Set.of(subscription));
        verify(first).close(CloseStatus.POLICY_VIOLATION);
        verify(duplicate).close(CloseStatus.POLICY_VIOLATION);
        verify(first, never()).sendMessage(any());
        verify(duplicate, never()).sendMessage(any());
    }

    private WebSocketSession connect(String id, Long group, Subscription subscription) throws Exception {
        return connect(id, group, subscription, () -> {});
    }

    private WebSocketSession connect(String id, Long group, Subscription subscription, Runnable onSend) throws Exception {
        var session = session(id, group, subscription.scheduleId(), subscription.memberId(), NOW.plusSeconds(60), onSend);
        when(access.findAuthorized(Set.of(subscription))).thenReturn(Set.of(subscription));
        handler.afterConnectionEstablished(session);
        clearInvocations(access, session);
        return session;
    }

    private WebSocketSession session(String id, Long group, Long schedule, Long member, Instant expires) {
        return session(id, group, schedule, member, expires, () -> {});
    }

    private WebSocketSession session(String id, Long group, Long schedule, Long member, Instant expires, Runnable onSend) {
        var session = mock(WebSocketSession.class, invocation -> {
            if (invocation.getMethod().getName().equals("sendMessage")) onSend.run();
            return RETURNS_DEFAULTS.answer(invocation);
        });
        when(session.getId()).thenReturn(id);
        when(session.getAttributes()).thenReturn(Map.of("expiresAt", expires, "groupId", group,
                "scheduleId", schedule, "memberId", member));
        return session;
    }

    private void verifyNotification(WebSocketSession session, String type) throws Exception {
        ArgumentCaptor<WebSocketMessage<?>> message = ArgumentCaptor.captor();
        verify(session).sendMessage(message.capture());
        assertEquals("{\"type\":\"" + type + "\",\"scheduleId\":2}", message.getValue().getPayload());
    }
}
