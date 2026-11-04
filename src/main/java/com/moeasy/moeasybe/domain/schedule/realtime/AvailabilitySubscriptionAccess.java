package com.moeasy.moeasybe.domain.schedule.realtime;
import com.moeasy.moeasybe.domain.schedule.service.support.AvailabilitySupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor
public class AvailabilitySubscriptionAccess {
    private final AvailabilitySupport support;
    @Transactional(readOnly = true)
    public Long require(Long scheduleId, Long memberId) {
        return support.access(scheduleId, memberId, false, false, false).group().getId();
    }
}
