package com.moeasy.moeasybe.domain.schedule.realtime;

import com.moeasy.moeasybe.domain.schedule.realtime.AvailabilitySubscriptionAccess.Subscription;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.*;

@Component
@RequiredArgsConstructor
public class AvailabilitySocketHandler extends TextWebSocketHandler {
    private final AvailabilitySubscriptionAccess access;
    private final Clock clock;
    private final ConcurrentMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        var decorated = new ConcurrentWebSocketSessionDecorator(session, 3000, 8192);
        sessions.put(session.getId(), decorated);
        authorizedSessions(List.of(decorated)).forEach(s -> send(s, "CONNECTED"));
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
        var targets = sessions.values().stream().filter(s ->
                Objects.equals(s.getAttributes().get("groupId"), event.groupId())
                && (event.scheduleId() == null
                || Objects.equals(s.getAttributes().get("scheduleId"), event.scheduleId()))).toList();
        authorizedSessions(targets).forEach(s -> send(s, event.type()));
    }

    @Scheduled(scheduler = "availabilitySocketScheduler",
            fixedDelayString = "${availability.socket-check-ms:1000}",
            initialDelayString = "${availability.socket-check-ms:1000}")
    public void revalidate() {
        authorizedSessions(new ArrayList<>(sessions.values()));
    }

    private List<WebSocketSession> authorizedSessions(Collection<WebSocketSession> candidates) {
        List<WebSocketSession> pending = new ArrayList<>();
        for (var session : candidates) {
            if (unexpired(session)) pending.add(session);
            else close(session);
        }
        if (pending.isEmpty()) return List.of();
        Set<Subscription> authorized;
        try {
            var subscriptions = pending.stream().map(this::subscription).collect(Collectors.toSet());
            authorized = access.findAuthorized(subscriptions);
        } catch (RuntimeException e) {
            pending.forEach(this::close);
            return List.of();
        }
        List<WebSocketSession> allowed = new ArrayList<>();
        for (var session : pending) {
            if (authorized.contains(subscription(session)) && unexpired(session)) allowed.add(session);
            else close(session);
        }
        return allowed;
    }

    private Subscription subscription(WebSocketSession session) {
        return new Subscription((Long) session.getAttributes().get("scheduleId"),
                (Long) session.getAttributes().get("memberId"));
    }

    private boolean unexpired(WebSocketSession session) {
        Instant expiry = (Instant) session.getAttributes().get("expiresAt");
        return expiry != null && expiry.isAfter(clock.instant());
    }

    private void send(WebSocketSession session, String type) {
        if (sessions.get(session.getId()) != session) return;
        if (!unexpired(session)) {
            close(session);
            return;
        }
        try {
            session.sendMessage(new TextMessage("{\"type\":\"" + type + "\",\"scheduleId\":"
                    + session.getAttributes().get("scheduleId") + "}"));
        } catch (Exception e) {
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
