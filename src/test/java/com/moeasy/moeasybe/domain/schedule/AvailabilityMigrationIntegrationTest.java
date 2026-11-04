package com.moeasy.moeasybe.domain.schedule;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.sql.Statement;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptStatementFailedException;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ByteArrayResource;

@SpringBootTest
class AvailabilityMigrationIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @ParameterizedTest @ValueSource(booleans = {false, true})
    @DisplayName("구 투표가 있으면 실제 마이그레이션의 사전 검증이 DDL 이전에 중단한다")
    void migration_legacyPollGuard_requiresEmptyPolls(boolean hasLegacy) throws Exception {
        // given: temporary tables shadow production tables only on this connection.
        String sql = new ClassPathResource("db/migration/V20261104224032__expand_availability_poll.sql")
                .getContentAsString(StandardCharsets.UTF_8).split("ALTER TABLE schedule")[0];
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TEMPORARY TABLE availability_poll (id BIGINT PRIMARY KEY)");
                if (hasLegacy) statement.execute("INSERT INTO availability_poll VALUES (1)");
                try {
                    // when
                    Runnable migrate = () -> ScriptUtils.executeSqlScript(connection, new ByteArrayResource(sql.getBytes(StandardCharsets.UTF_8)));
                    // then
                    if (hasLegacy) {
                        var exception = assertThrows(ScriptStatementFailedException.class, migrate::run);
                        assertTrue(exception.getMostSpecificCause().getMessage().contains("ck_availability_empty"));
                    } else {
                        assertDoesNotThrow(migrate::run);
                    }
                } finally {
                    statement.execute("DROP TEMPORARY TABLE IF EXISTS availability_migration_guard");
                    statement.execute("DROP TEMPORARY TABLE availability_poll");
                }
            }
            return null;
        });
    }
}
