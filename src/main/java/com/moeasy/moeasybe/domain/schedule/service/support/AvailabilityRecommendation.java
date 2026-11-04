package com.moeasy.moeasybe.domain.schedule.service.support;

import java.time.*;
import java.util.*;

/** Pure calculation: every covered half-hour must contain the same attendee. */
public final class AvailabilityRecommendation {
    private AvailabilityRecommendation() { }
    public record Window(LocalDateTime startsAt, LocalDateTime endsAt, int availableCount) { }
    public static List<Window> calculate(List<LocalDateTime> slots, Map<LocalDateTime, Set<Long>> attendees,
            Duration duration, int startHour, int endHour, LocalDateTime now) {
        if (duration.isNegative() || duration.isZero()) return List.of();
        Set<LocalDateTime> candidates = new HashSet<>(slots);
        List<Window> result = new ArrayList<>();
        for (LocalDateTime start : slots) {
            if (!start.isAfter(now)) continue;
            LocalDateTime end = start.plus(duration);
            // Even consecutive 00:00-24:00 days remain separate daily candidate windows.
            if (end.isAfter(start.toLocalDate().atStartOfDay().plusHours(endHour))
                    || start.getHour() < startHour) continue;
            Set<Long> intersection = null;
            for (LocalDateTime t = start; t.isBefore(end); t = t.plusMinutes(30)) {
                if (!candidates.contains(t)) { intersection = Set.of(); break; }
                if (intersection == null) intersection = new HashSet<>(attendees.getOrDefault(t, Set.of()));
                else intersection.retainAll(attendees.getOrDefault(t, Set.of()));
                if (intersection.isEmpty()) break;
            }
            if (intersection != null && !intersection.isEmpty()) result.add(new Window(start, end, intersection.size()));
        }
        result.sort(Comparator.comparingInt(Window::availableCount).reversed().thenComparing(Window::startsAt));
        return List.copyOf(result);
    }
}
