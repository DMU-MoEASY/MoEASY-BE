package com.moeasy.moeasybe.domain.schedule;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import java.sql.SQLException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class ScheduleMigrationIntegrationTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    @DisplayName("기존 행의 상태를 SCHEDULED로 이행하고 허용되지 않은 상태를 DB에서 거부한다")
    void migration_existingRows_backfillsAndConstrainsStatus() throws Exception {
        // given: 실제 마이그레이션 SQL을 독립된 테스트 테이블에 적용한다.
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String table = "schedule_migration_" + suffix;
        jdbc.execute("CREATE TABLE " + table + " (id BIGINT PRIMARY KEY, group_id BIGINT NOT NULL, "
                + "deleted_at DATETIME(6), created_at DATETIME(6) NOT NULL)");
        try {
            jdbc.update("INSERT INTO " + table + " VALUES (1, 1, NULL, CURRENT_TIMESTAMP(6))");
            String sql = new ClassPathResource("db/migration/V20261102211842__add_schedule_status.sql")
                    .getContentAsString(StandardCharsets.UTF_8)
                    .replace("ALTER TABLE schedule", "ALTER TABLE " + table)
                    .replace("ck_schedule_status", "ck_schedule_status_" + suffix);
            // when
            jdbc.execute(sql);
            String status = jdbc.queryForObject("SELECT status FROM " + table + " WHERE id=1", String.class);
            // then
            DataAccessException exception = assertThrows(DataAccessException.class,
                    () -> jdbc.update("UPDATE " + table + " SET status='UNKNOWN' WHERE id=1"));
            assertAll(() -> assertEquals("SCHEDULED", status),
                    () -> assertEquals(3819, ((SQLException) exception.getMostSpecificCause()).getErrorCode()));
        } finally {
            jdbc.execute("DROP TABLE " + table);
        }
    }
}
