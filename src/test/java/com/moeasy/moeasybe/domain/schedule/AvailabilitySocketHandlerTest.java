package com.moeasy.moeasybe.domain.schedule;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.moeasy.moeasybe.domain.schedule.realtime.AvailabilityChanged;
import com.moeasy.moeasybe.domain.schedule.realtime.AvailabilitySocketHandler;
import com.moeasy.moeasybe.domain.schedule.realtime.AvailabilitySubscriptionAccess;
import com.moeasy.moeasybe.global.apiPayload.code.GeneralErrorCode;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

@ExtendWith(MockitoExtension.class)
class AvailabilitySocketHandlerTest {
    private static final Instant NOW = Instant.parse("2026-11-04T03:00:00Z");
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    @Mock private AvailabilitySubscriptionAccess access;

    @Test
    @DisplayName("JWT 만료 시 변경 내용을 보내지 않고 연결을 종료한다")
    void connect_expiredJwt_closesWithoutSending() throws Exception {
        // given
        WebSocketSession session = session("expired", 2L, 3L, NOW);
        AvailabilitySocketHandler handler = new AvailabilitySocketHandler(access, clock);

        // when
        handler.afterConnectionEstablished(session);

        // then
        verify(session).close(CloseStatus.POLICY_VIOLATION);
        verify(session, never()).sendMessage(any());
        verifyNoInteractions(access);
    }

    @Test
    @DisplayName("같은 회원·일정의 중복 연결은 검사 회차마다 한 번만 권한을 조회한다")
    void revalidate_duplicateSubscription_queriesOncePerPass() {
        // given
        AvailabilitySocketHandler handler = new AvailabilitySocketHandler(access, clock);
        handler.afterConnectionEstablished(session("first", 2L, 3L, NOW.plusSeconds(60)));
        handler.afterConnectionEstablished(session("second", 2L, 3L, NOW.plusSeconds(60)));
        clearInvocations(access);

        // when
        handler.revalidate();
        handler.revalidate();

        // then
        verify(access, times(2)).require(2L, 3L);
    }

    @ParameterizedTest
    @CsvSource({"2, 4", "3, 3"})
    @DisplayName("일정이나 회원이 다르면 접근 검사 결과를 공유하지 않는다")
    void revalidate_differentSubscription_queriesSeparately(Long secondScheduleId, Long secondMemberId) {
        // given
        AvailabilitySocketHandler handler = new AvailabilitySocketHandler(access, clock);
        handler.afterConnectionEstablished(session("first", 2L, 3L, NOW.plusSeconds(60)));
        handler.afterConnectionEstablished(session("second", secondScheduleId, secondMemberId, NOW.plusSeconds(60)));
        clearInvocations(access);

        // when
        handler.revalidate();

        // then
        verify(access).require(2L, 3L);
        verify(access).require(secondScheduleId, secondMemberId);
    }

    @Test
    @DisplayName("하나의 변경 알림은 중복 연결의 권한을 한 번 조회하고 각 연결로 전송한다")
    void invalidate_duplicateSubscription_queriesOnceAndSendsToBoth() throws Exception {
        // given
        WebSocketSession first = session("first", 2L, 3L, NOW.plusSeconds(60));
        WebSocketSession second = session("second", 2L, 3L, NOW.plusSeconds(60));
        AvailabilitySocketHandler handler = new AvailabilitySocketHandler(access, clock);
        handler.afterConnectionEstablished(first);
        handler.afterConnectionEstablished(second);
        clearInvocations(access, first, second);

        // when
        handler.invalidate(new AvailabilityChanged(1L, 2L, "RESPONSE_CHANGED"));

        // then
        verify(access).require(2L, 3L);
        verify(first).sendMessage(argThat(message -> message.getPayload().toString().contains("RESPONSE_CHANGED")));
        verify(second).sendMessage(argThat(message -> message.getPayload().toString().contains("RESPONSE_CHANGED")));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("같은 구독이어도 만료된 세션만 종료하고 유효한 세션의 권한을 검사한다")
    void check_sameSubscriptionWithDifferentExpiry_closesOnlyExpiredSession(boolean notification) throws Exception {
        // given
        WebSocketSession expired = session("expired", 2L, 3L, NOW.plusSeconds(60));
        WebSocketSession valid = session("valid", 2L, 3L, NOW.plusSeconds(60));
        AvailabilitySocketHandler handler = new AvailabilitySocketHandler(access, clock);
        handler.afterConnectionEstablished(expired);
        handler.afterConnectionEstablished(valid);
        expired.getAttributes().put("expiresAt", NOW);
        clearInvocations(access, expired, valid);

        // when
        if (notification) {
            handler.invalidate(new AvailabilityChanged(1L, 2L, "CHANGED"));
        } else {
            handler.revalidate();
        }

        // then
        verify(access).require(2L, 3L);
        verify(expired).close(CloseStatus.POLICY_VIOLATION);
        verify(expired, never()).sendMessage(any());
        verify(valid, never()).close(any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("자격 상실은 중복 연결 모두를 종료하고 공용 알림도 차단한다")
    void check_revokedSubscription_closesAllWithoutSending(boolean notification) throws Exception {
        // given
        WebSocketSession first = session("first", 2L, 3L, NOW.plusSeconds(60));
        WebSocketSession second = session("second", 2L, 3L, NOW.plusSeconds(60));
        AvailabilitySocketHandler handler = new AvailabilitySocketHandler(access, clock);
        handler.afterConnectionEstablished(first);
        handler.afterConnectionEstablished(second);
        when(access.require(2L, 3L)).thenThrow(new GeneralException(GeneralErrorCode.FORBIDDEN));
        clearInvocations(access, first, second);

        // when
        if (notification) {
            handler.invalidate(new AvailabilityChanged(1L, 2L, "ROSTER_CHANGED"));
        } else {
            handler.revalidate();
        }

        // then
        verify(access).require(2L, 3L);
        verify(first).close(CloseStatus.POLICY_VIOLATION);
        verify(second).close(CloseStatus.POLICY_VIOLATION);
        verify(first, never()).sendMessage(any());
        verify(second, never()).sendMessage(any());
    }

    @Test
    @DisplayName("이전 알림의 허용 결과는 다음 알림에서 재사용하지 않는다")
    void invalidate_revokedAfterPreviousEvent_rechecksAccess() throws Exception {
        // given
        WebSocketSession session = session("member", 2L, 3L, NOW.plusSeconds(60));
        AvailabilitySocketHandler handler = new AvailabilitySocketHandler(access, clock);
        handler.afterConnectionEstablished(session);
        handler.invalidate(new AvailabilityChanged(1L, 2L, "CHANGED"));
        when(access.require(2L, 3L)).thenThrow(new GeneralException(GeneralErrorCode.FORBIDDEN));
        clearInvocations(access, session);

        // when
        handler.invalidate(new AvailabilityChanged(1L, 2L, "ROSTER_CHANGED"));

        // then
        verify(access).require(2L, 3L);
        verify(session).close(CloseStatus.POLICY_VIOLATION);
        verify(session, never()).sendMessage(any());
    }

    private WebSocketSession session(String id, Long scheduleId, Long memberId, Instant expiry) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>(Map.of("expiresAt", expiry,
                "groupId", 1L, "scheduleId", scheduleId, "memberId", memberId));
        when(session.getId()).thenReturn(id);
        when(session.getAttributes()).thenReturn(attributes);
        return session;
    }
}
