package com.moeasy.moeasybe.domain.schedule.realtime;
/** Public invalidation only; never put personal selections in this event. Null scheduleId invalidates a group. */
public record AvailabilityChanged(Long groupId, Long scheduleId, String type) { }
