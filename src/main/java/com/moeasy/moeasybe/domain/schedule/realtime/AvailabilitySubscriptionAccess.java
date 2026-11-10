package com.moeasy.moeasybe.domain.schedule.realtime;

import com.moeasy.moeasybe.domain.schedule.repository.ScheduleRepository;
import com.moeasy.moeasybe.domain.schedule.service.support.AvailabilitySupport;
import com.moeasy.moeasybe.global.apiPayload.code.GeneralErrorCode;
import com.moeasy.moeasybe.global.apiPayload.exception.GeneralException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AvailabilitySubscriptionAccess {
    private static final int BATCH_SIZE = 128;
    private final AvailabilitySupport support;
    private final ScheduleRepository schedules;

    public record Subscription(Long scheduleId, Long memberId) {}

    // 권한 결과는 현재 검사에서만 사용하고 다음 알림이나 주기 검사에 재사용하지 않는다.
    @Transactional(readOnly = true)
    public Set<Subscription> findAuthorized(Set<Subscription> subscriptions) {
        if (subscriptions == null || subscriptions.stream().anyMatch(s ->
                s == null || s.scheduleId() == null || s.memberId() == null)) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST);
        }
        var requested = new ArrayList<>(subscriptions);
        Set<Subscription> authorized = new HashSet<>();
        for (int start = 0; start < requested.size(); start += BATCH_SIZE) {
            var batch = requested.subList(start, Math.min(start + BATCH_SIZE, requested.size()));
            var scheduleIds = batch.stream().map(Subscription::scheduleId).collect(Collectors.toSet());
            var memberIds = batch.stream().map(Subscription::memberId).collect(Collectors.toSet());
            for (var member : schedules.findSubscriptionMembers(scheduleIds, memberIds)) {
                var subscription = new Subscription(member.getScheduleId(), member.getMemberId());
                if (subscriptions.contains(subscription)) authorized.add(subscription);
            }
        }
        return authorized;
    }

    @Transactional(readOnly = true)
    public Long require(Long scheduleId, Long memberId) {
        return support.access(scheduleId, memberId, false, false, false).group().getId();
    }
}
