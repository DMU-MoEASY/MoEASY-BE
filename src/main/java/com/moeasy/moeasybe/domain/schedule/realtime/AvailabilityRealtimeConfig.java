package com.moeasy.moeasybe.domain.schedule.realtime;

import com.moeasy.moeasybe.global.config.CorsProperties;
import com.moeasy.moeasybe.global.security.jwt.JwtTokenProvider;
import com.moeasy.moeasybe.domain.auth.config.AuthCookieNames;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.*;
import org.springframework.http.*;
import org.springframework.http.server.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Configuration @EnableWebSocket @EnableScheduling @RequiredArgsConstructor
public class AvailabilityRealtimeConfig implements WebSocketConfigurer {
    public static final String CHANNEL = "moeasy:availability:changed";
    private final AvailabilitySocketHandler handler;
    private final AvailabilitySubscriptionAccess access;
    private final CorsProperties cors;
    private final JwtTokenProvider jwt;

    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/schedules/{scheduleId}/availability")
                .setAllowedOrigins(cors.allowedOrigins().toArray(String[]::new))
                .addInterceptors(new HandshakeInterceptor() {
                    @Override public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                            WebSocketHandler wsHandler, Map<String, Object> attributes) {
                        // A read-only socket uses cookie JWT and a strict Origin allowlist. All writes retain REST CSRF.
                        if (!cors.allowedOrigins().contains(request.getHeaders().getOrigin())) {
                            response.setStatusCode(HttpStatus.FORBIDDEN); return false;
                        }
                        try {
                            if (request.getPrincipal() == null) { response.setStatusCode(HttpStatus.UNAUTHORIZED); return false; }
                            Long memberId = Long.valueOf(request.getPrincipal().getName());
                            String[] path = request.getURI().getPath().split("/");
                            Long scheduleId = Long.valueOf(path[3]);
                            Long groupId = access.require(scheduleId, memberId);
                            var servlet = ((ServletServerHttpRequest) request).getServletRequest();
                            String token = Arrays.stream(Optional.ofNullable(servlet.getCookies()).orElse(new jakarta.servlet.http.Cookie[0]))
                                    .filter(c -> AuthCookieNames.ACCESS_TOKEN.equals(c.getName())).map(jakarta.servlet.http.Cookie::getValue)
                                    .findFirst().orElseThrow();
                            attributes.put("expiresAt", jwt.decode(token).getExpiresAt());
                            attributes.put("memberId", memberId); attributes.put("scheduleId", scheduleId); attributes.put("groupId", groupId);
                            return true;
                        } catch (RuntimeException e) { response.setStatusCode(HttpStatus.FORBIDDEN); return false; }
                    }
                    @Override public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                            WebSocketHandler wsHandler, Exception exception) { }
                });
    }
    @Bean public RedisMessageListenerContainer availabilityRedisListener(RedisConnectionFactory connection) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connection);
        container.addMessageListener((message, pattern) -> {
            String[] parts = new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8).split(":", 3);
            if (parts.length == 3) {
                try { handler.invalidate(new AvailabilityChanged(Long.valueOf(parts[0]),
                        parts[1].equals("*") ? null : Long.valueOf(parts[1]), parts[2])); }
                catch (NumberFormatException ignored) { }
            }
        }, new ChannelTopic(CHANNEL));
        return container;
    }
}
