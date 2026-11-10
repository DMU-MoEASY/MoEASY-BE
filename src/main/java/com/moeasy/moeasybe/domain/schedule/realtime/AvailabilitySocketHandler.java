package com.moeasy.moeasybe.domain.schedule.realtime;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
@RequiredArgsConstructor
public class AvailabilitySocketHandler extends TextWebSocketHandler {
    private final AvailabilitySubscriptionAccess access;
    private final Clock clock;
    private final ConcurrentMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    private record Subscription(Long scheduleId, Long memberId) { }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), new ConcurrentWebSocketSessionDecorator(session, 3000, 8192));
        send(session.getId(), "CONNECTED", new HashMap<>());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        session.close(CloseStatus.POLICY_VIOLATION); // REST is the only command channel.
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable error) {
        close(session);
    }

    public void invalidate(AvailabilityChanged event) {
        Map<Subscription, Boolean> authorization = new HashMap<>();
        sessions.forEach((id, session) -> {
            Long groupId = (Long) session.getAttributes().get("groupId");
            Long scheduleId = (Long) session.getAttributes().get("scheduleId");
            if (groupId.equals(event.groupId()) && (event.scheduleId() == null || scheduleId.equals(event.scheduleId()))) {
                send(id, event.type(), authorization);
            }
        });
    }

    @Scheduled(scheduler = "availabilitySocketScheduler",
            fixedDelayString = "${availability.socket-check-ms:1000}",
            initialDelayString = "${availability.socket-check-ms:1000}")
    public void revalidate() {
        Map<Subscription, Boolean> authorization = new HashMap<>();
        sessions.forEach((id, session) -> {
            if (!authorized(session, authorization)) {
                close(session);
            }
        });
    }

    private boolean authorized(WebSocketSession session, Map<Subscription, Boolean> authorization) {
        Instant expiry = (Instant) session.getAttributes().get("expiresAt");
        if (expiry == null || !expiry.isAfter(clock.instant())) {
            return false;
        }
        Subscription subscription = new Subscription((Long) session.getAttributes().get("scheduleId"),
                (Long) session.getAttributes().get("memberId"));
        return authorization.computeIfAbsent(subscription, this::hasAccess);
    }

    private boolean hasAccess(Subscription subscription) {
        try {
            access.require(subscription.scheduleId(), subscription.memberId());
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void send(String id, String type, Map<Subscription, Boolean> authorization) {
        WebSocketSession session = sessions.get(id);
        if (session == null) {
            return;
        }
        if (!authorized(session, authorization)) {
            close(session);
            return;
        }
        try {
            session.sendMessage(new TextMessage("{\"type\":\"" + type + "\",\"scheduleId\":"
                    + session.getAttributes().get("scheduleId") + "}"));
        } catch (Exception exception) {
            close(session);
        }
    }

    private void close(WebSocketSession session) {
        sessions.remove(session.getId());
        try {
            session.close(CloseStatus.POLICY_VIOLATION);
        } catch (Exception ignored) { }
    }
}
