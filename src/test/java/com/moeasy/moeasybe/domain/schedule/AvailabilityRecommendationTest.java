package com.moeasy.moeasybe.domain.schedule;

import static org.junit.jupiter.api.Assertions.*;
import com.moeasy.moeasybe.domain.schedule.entity.AvailabilityPoll;
import com.moeasy.moeasybe.domain.schedule.service.support.AvailabilityInput;
import com.moeasy.moeasybe.domain.schedule.service.support.AvailabilityRecommendation;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AvailabilityRecommendationTest {
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 10, 9, 0);
    @Test @DisplayName("슬롯별 최솟값이 같아도 동일 구성원의 교집합이 없으면 추천하지 않는다")
    void calculate_disjointAttendees_returnsNoWindow() {
        // given
        var slots = List.of(START, START.plusMinutes(30));
        var attendees = Map.of(START, Set.of(1L), START.plusMinutes(30), Set.of(2L));
        // when
        var result = AvailabilityRecommendation.calculate(slots, attendees, Duration.ofHours(1), 9, 10, START.minusDays(1));
        // then
        assertTrue(result.isEmpty());
    }
    @Test @DisplayName("45분 일정은 걸치는 두 슬롯을 검사하고 실제 종료 시각을 유지한다")
    void calculate_nonMultipleDuration_requiresAllCoveredSlots() {
        // given
        var slots = List.of(START, START.plusMinutes(30), START.plusHours(1));
        var attendees = Map.of(START, Set.of(1L,2L), START.plusMinutes(30), Set.of(1L), START.plusHours(1), Set.of(1L));
        // when
        var result = AvailabilityRecommendation.calculate(slots, attendees, Duration.ofMinutes(45), 9, 11, START.minusDays(1));
        // then
        assertAll(() -> assertEquals(2, result.size()), () -> assertEquals(START, result.getFirst().startsAt()),
                () -> assertEquals(START.plusMinutes(45), result.getFirst().endsAt()), () -> assertEquals(1, result.getFirst().availableCount()));
    }
    @Test @DisplayName("일별 공백과 야간을 건너뛰지 않고 과거 시작과 0명 후보를 제외한다")
    void calculate_dailyGapAndPast_excludesInvalidWindows() {
        // given
        var slots = List.of(START, START.plusMinutes(30), START.plusDays(1));
        var attendees = Map.of(START, Set.of(1L), START.plusMinutes(30), Set.of(1L), START.plusDays(1), Set.of(1L));
        // when
        var result = AvailabilityRecommendation.calculate(slots, attendees, Duration.ofHours(1), 9, 10, START);
        // then
        assertTrue(result.isEmpty());
    }
    @ParameterizedTest @ValueSource(ints = {3,5,7,14}) @DisplayName("후보 일수와 24:00 경계에 맞게 슬롯을 생성한다")
    void slots_supportedDaysAndMidnight_hasExclusiveEnd(int days) {
        // given
        var poll = AvailabilityPoll.builder().scheduleId(1L).startDate(START.toLocalDate()).dayCount(days)
                .startHour(23).endHour(24).voteDeadlineAt(START).build();
        // when
        var slots = AvailabilityInput.slots(poll);
        // then
        assertAll(() -> assertEquals(days * 2, slots.size()), () -> assertEquals(23, slots.getFirst().getHour()),
                () -> assertEquals(START.toLocalDate().plusDays(days-1).atTime(23,30), slots.getLast()),
                () -> assertTrue(slots.stream().noneMatch(t -> t.getHour() == 0)));
    }
    @Test @DisplayName("정렬은 가능 인원 내림차순과 시작 시각 오름차순이다")
    void calculate_ties_sortsDeterministically() {
        // given
        var slots = List.of(START, START.plusMinutes(30), START.plusHours(1));
        var attendees = Map.of(START, Set.of(1L), START.plusMinutes(30), Set.of(1L,2L), START.plusHours(1), Set.of(1L,2L));
        // when
        var result = AvailabilityRecommendation.calculate(slots, attendees, Duration.ofMinutes(30), 9, 12, START.minusDays(1));
        // then
        assertEquals(List.of(START.plusMinutes(30), START.plusHours(1), START), result.stream().map(AvailabilityRecommendation.Window::startsAt).toList());
    }
}
