package com.moeasy.moeasybe.domain.schedule.realtime;

import java.time.*;
import java.util.concurrent.*;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.*;

@Component @RequiredArgsConstructor
public class AvailabilitySocketHandler extends TextWebSocketHandler {
    private final AvailabilitySubscriptionAccess access;
    private final Clock clock;
    private final ConcurrentMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    @Override public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), new ConcurrentWebSocketSessionDecorator(session, 3000, 8192));
        send(session.getId(), "CONNECTED");
    }
    @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) { sessions.remove(session.getId()); }
    @Override protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        session.close(CloseStatus.POLICY_VIOLATION); // REST is the only command channel.
    }
    @Override public void handleTransportError(WebSocketSession session, Throwable error) { close(session); }
    public void invalidate(AvailabilityChanged event) {
        sessions.forEach((id, s) -> {
            Long groupId = (Long) s.getAttributes().get("groupId");
            Long scheduleId = (Long) s.getAttributes().get("scheduleId");
            if (groupId.equals(event.groupId()) && (event.scheduleId() == null || scheduleId.equals(event.scheduleId())))
                send(id, event.type());
        });
    }
    @Scheduled(fixedDelayString = "${availability.socket-check-ms:1000}", initialDelayString = "${availability.socket-check-ms:1000}")
    public void revalidate() {
        sessions.forEach((id, s) -> {
            if (!authorized(s)) close(s);
        });
    }
    private boolean authorized(WebSocketSession s) {
        try {
            Instant expiry = (Instant) s.getAttributes().get("expiresAt");
            if (expiry == null || !expiry.isAfter(clock.instant())) return false;
            access.require((Long) s.getAttributes().get("scheduleId"), (Long) s.getAttributes().get("memberId"));
            return true;
        } catch (RuntimeException e) { return false; }
    }
    private void send(String id, String type) {
        WebSocketSession s = sessions.get(id);
        if (s == null) return;
        if (!authorized(s)) { close(s); return; }
        try {
            s.sendMessage(new TextMessage("{\"type\":\"" + type + "\",\"scheduleId\":"
                    + s.getAttributes().get("scheduleId") + "}"));
        } catch (Exception e) { close(s); }
    }
    private void close(WebSocketSession s) {
        sessions.remove(s.getId());
        try { s.close(CloseStatus.POLICY_VIOLATION); } catch (Exception ignored) { }
    }
}
