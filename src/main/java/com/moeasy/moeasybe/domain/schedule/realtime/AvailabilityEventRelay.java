package com.moeasy.moeasybe.domain.schedule.realtime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
@Component @RequiredArgsConstructor @Slf4j
public class AvailabilityEventRelay {
    private final StringRedisTemplate redis;
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onChanged(AvailabilityChanged event) {
        try { redis.convertAndSend(AvailabilityRealtimeConfig.CHANNEL,
                event.groupId() + ":" + (event.scheduleId() == null ? "*" : event.scheduleId()) + ":" + event.type()); }
        catch (RuntimeException e) { log.warn("Availability notification unavailable; clients must resynchronize"); }
    }
}
