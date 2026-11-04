package com.moeasy.moeasybe.domain.schedule;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.moeasy.moeasybe.domain.schedule.realtime.*;
import java.time.*;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.*;

@ExtendWith(MockitoExtension.class)
class AvailabilitySocketHandlerTest {
    @Test @DisplayName("JWT 만료 시 변경 내용을 보내지 않고 연결을 종료한다")
    void connect_expiredJwt_closesWithoutSending() throws Exception {
        // given
        Clock clock=Clock.fixed(Instant.parse("2026-11-04T03:00:00Z"),ZoneOffset.UTC);
        var access=mock(AvailabilitySubscriptionAccess.class);
        var session=mock(WebSocketSession.class);
        when(session.getId()).thenReturn("expired");
        when(session.getAttributes()).thenReturn(Map.of("expiresAt",clock.instant(),"groupId",1L,"scheduleId",2L,"memberId",3L));
        var handler=new AvailabilitySocketHandler(access,clock);
        // when
        handler.afterConnectionEstablished(session);
        // then
        verify(session).close(CloseStatus.POLICY_VIOLATION);
        verify(session,never()).sendMessage(any());
        verifyNoInteractions(access);
    }
}
