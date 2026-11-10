package com.moeasy.moeasybe.schema;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.UUID;

import javax.sql.DataSource;

class SettlementPaymentMigrationIntegrationTest {

    @Test
    @DisplayName("기존 수동·은행·취소 납부를 이관하면서 원본 데이터와 알 수 없는 감사 정보의 null을 보존한다")
    void migrate_existingPayments_preservesHistory() {
        // given: 현재 애플리케이션 DB 대신 매 실행마다 생성한 별도 DB만 사용한다.
        String url =
                System.getenv()
                        .getOrDefault(
                                "DB_URL",
                                "jdbc:mysql://localhost:3306/moeasy?serverTimezone=Asia/Seoul&allowPublicKeyRetrieval=true&useSSL=false");
        String username = System.getenv().getOrDefault("MIGRATION_TEST_DB_USERNAME", "root");
        String password = System.getenv().getOrDefault("MIGRATION_TEST_DB_PASSWORD", "root1234");
        String schema = "settlement_migration_" + UUID.randomUUID().toString().replace("-", "");
        String isolatedUrl = url.replaceFirst("(jdbc:mysql://[^/]+/)[^?]*", "$1" + schema);
        if (isolatedUrl.equals(url))
            throw new IllegalArgumentException("jdbc:mysql:// 형식의 테스트 DB_URL이 필요합니다.");
        JdbcTemplate admin = new JdbcTemplate(new DriverManagerDataSource(url, username, password));
        admin.execute(
                "CREATE DATABASE `"
                        + schema
                        + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        try {
            DataSource dataSource = new DriverManagerDataSource(isolatedUrl, username, password);
            migrateTo(dataSource, "20261019003905");
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            prepareLegacyPayments(jdbc);

            // when: 실제 배포와 동일한 Flyway SQL을 실행한다.
            migrateTo(dataSource, "20261030111343");

            // then
            var payments =
                    jdbc.query(
                            """
                            SELECT p.id, p.source, p.status, p.amount, p.account_transaction_id, i.version,
                                   (p.recorded_by_group_member_id IS NULL AND p.occurred_at IS NULL
                                    AND p.cancelled_by_group_member_id IS NULL AND p.cancelled_at IS NULL
                                    AND p.cancel_reason IS NULL) AS unknown_audit
                            FROM settlement_payment p JOIN settlement_item i ON i.id = p.settlement_item_id
                            ORDER BY p.id
                            """,
                            (row, index) ->
                                    new MigratedPayment(
                                            row.getLong("id"),
                                            row.getString("source"),
                                            row.getString("status"),
                                            row.getLong("amount"),
                                            row.getObject("account_transaction_id", Long.class),
                                            row.getLong("version"),
                                            row.getBoolean("unknown_audit")));
            assertThat(payments)
                    .containsExactly(
                            new MigratedPayment(1, "MANUAL", "COMPLETED", 1000, null, 0, true),
                            new MigratedPayment(2, "BANK", "COMPLETED", 2000, 1L, 0, true),
                            new MigratedPayment(3, "MANUAL", "CANCELLED", 3000, null, 0, true));
        } finally {
            // 외부 입력이 아닌 위에서 생성한 이름의 임시 DB만 정리한다.
            admin.execute("DROP DATABASE `" + schema + "`");
        }
    }

    private void migrateTo(DataSource dataSource, String version) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(version)
                .load()
                .migrate();
    }

    private void prepareLegacyPayments(JdbcTemplate jdbc) {
        jdbc.update(
                "INSERT INTO member (id, social_type, social_id, nickname) VALUES (1, 'KAKAO',"
                    + " 'migration-test', '이관회원')");
        jdbc.update(
                """
                INSERT INTO member_group (id, category_id, name, description, region_code, place_name, address, max_members)
                SELECT 1, id, '이관모임', '설명', '11680', '장소', '주소', 10 FROM category WHERE code = 'RUNNING'
                """);
        jdbc.update(
                "INSERT INTO group_member (id, group_id, member_id, role) VALUES (1, 1, 1,"
                    + " 'OWNER')");
        jdbc.update(
                """
                INSERT INTO settlement (id, group_id, created_by_group_member_id, title, total_amount, split_mode)
                VALUES (1, 1, 1, '기존 회비', 10000, 'EQUAL')
                """);
        jdbc.update(
                "INSERT INTO settlement_item (id, settlement_id, group_member_id, amount) VALUES"
                    + " (1, 1, 1, 10000)");
        jdbc.update(
                """
                INSERT INTO group_bank_account (id, group_id, bank_code, account_number_secret_ref,
                        account_number_fingerprint, account_number_masked, account_holder)
                VALUES (1, 1, 'NH', 'test-only-ref', REPEAT('a', 64), '***1234', '이관회원')
                """);
        jdbc.update(
                """
                INSERT INTO account_transaction (id, group_bank_account_id, transaction_type,
                        transacted_at, description, amount, balance, source_hash)
                VALUES (1, 1, 'DEPOSIT', '2026-10-29 14:00:00', '기존 입금', 2000, 2000, REPEAT('b', 64))
                """);
        jdbc.update(
                """
                INSERT INTO settlement_payment (id, settlement_item_id, account_transaction_id,
                        payment_type, status, amount, idempotency_key)
                VALUES (1, 1, NULL, 'PAYMENT', 'COMPLETED', 1000, 'legacy-manual'),
                       (2, 1, 1, 'PAYMENT', 'COMPLETED', 2000, 'legacy-bank'),
                       (3, 1, NULL, 'PAYMENT', 'CANCELLED', 3000, 'legacy-cancelled')
                """);
    }

    private record MigratedPayment(
            long id,
            String source,
            String status,
            long amount,
            Long accountTransactionId,
            long version,
            boolean unknownAudit) {}
}
