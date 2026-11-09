package com.moeasy.moeasybe.domain.schedule.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ScheduleCursorTest {

    @ParameterizedTest
    @CsvSource({"from, 999", "from, 10000", "to, 999", "to, 10000", "createdAt, 999", "createdAt, 10000"})
    @DisplayName("커서의 모든 날짜는 DB 저장 범위 밖이면 거부한다")
    void decode_dateOutsideDatabaseRange_rejectsCursor(String field, int year) {
        // given
        LocalDateTime from = LocalDateTime.of(2026, 11, 1, 0, 0);
        LocalDateTime to = from.plusMonths(1);
        LocalDateTime createdAt = from.plusDays(1);
        // 기간 순서가 아닌 날짜 범위 검증으로 거부되는지 확인한다.
        if (field.equals("from")) {
            from = from.withYear(year);
            to = from.plusDays(1);
        } else if (field.equals("to")) {
            to = to.withYear(year);
            from = to.minusDays(1);
        } else {
            createdAt = createdAt.withYear(year);
        }
        String token = new ScheduleCursor(1L, from, to, createdAt, 1L).encode();

        // when, then
        assertThrows(IllegalArgumentException.class, () -> ScheduleCursor.decode(token));
    }

    @Test
    @DisplayName("정상 커서는 DB 날짜 경계와 생성 시각의 마이크로초를 보존한다")
    void decode_validCursor_preservesDatesAndMicroseconds() {
        // given
        ScheduleCursor cursor = new ScheduleCursor(1L,
                LocalDateTime.of(1000, 1, 1, 0, 0),
                LocalDateTime.of(9999, 12, 31, 23, 59, 59),
                LocalDateTime.of(2026, 11, 1, 12, 0, 0, 123456000), 2L);

        // when
        ScheduleCursor result = ScheduleCursor.decode(cursor.encode());

        // then
        assertEquals(cursor, result);
    }
}
