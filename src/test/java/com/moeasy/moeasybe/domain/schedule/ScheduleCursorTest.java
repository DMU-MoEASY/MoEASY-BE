package com.moeasy.moeasybe.domain.schedule;

import static org.junit.jupiter.api.Assertions.*;

import com.moeasy.moeasybe.domain.schedule.service.support.ScheduleCursor;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ScheduleCursorTest {

    @ParameterizedTest
    @CsvSource({"from,0999-01-01T00:00", "from,+10000-01-01T00:00",
            "to,0999-01-01T00:00", "to,+10000-01-01T00:00",
            "createdAt,0999-01-01T00:00", "createdAt,+10000-01-01T00:00"})
    @DisplayName("커서의 모든 날짜는 DB에서 지원하는 연도 범위여야 한다")
    void decode_outOfRangeDate_rejectsCursor(String field, String value) {
        // given
        String from = field.equals("from") ? value : "2026-11-01T00:00";
        String to = field.equals("to") ? value : "2026-12-01T00:00";
        String createdAt = field.equals("createdAt") ? value : "2026-11-02T12:00:00.123456";
        String raw = "1|1|" + from + "|" + to + "|" + createdAt + "|2";
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));

        // when
        var exception = assertThrows(IllegalArgumentException.class, () -> ScheduleCursor.decode(token));

        // then
        assertEquals("잘못된 커서입니다.", exception.getMessage());
    }

    @Test
    @DisplayName("커서의 DB 연도 경계와 생성 시각의 마이크로초를 보존한다")
    void decode_validBoundaries_preservesMicroseconds() {
        // given
        var cursor = new ScheduleCursor(1L, LocalDateTime.of(1000, 1, 1, 0, 0),
                LocalDateTime.of(9999, 12, 31, 23, 59, 59),
                LocalDateTime.of(2026, 11, 2, 12, 0, 0, 123456000), 2L);

        // when
        var decoded = ScheduleCursor.decode(cursor.encode());

        // then
        assertEquals(cursor, decoded);
    }
}
