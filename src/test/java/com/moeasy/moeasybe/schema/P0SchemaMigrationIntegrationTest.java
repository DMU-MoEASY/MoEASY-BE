package com.moeasy.moeasybe.schema;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class P0SchemaMigrationIntegrationTest {

    private static final int P0_TABLE_COUNT = 32;
    private static final int P0_FOREIGN_KEY_COUNT = 52;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesTheApprovedP0Schema() {
        Integer tableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN (
                      'category', 'member', 'member_category', 'member_group',
                      'group_member', 'group_join_request', 'schedule', 'schedule_rsvp', 'availability_poll',
                      'availability_slot', 'availability_vote', 'attendance_session', 'attendance',
                      'group_poll', 'group_poll_option', 'group_poll_vote', 'group_post',
                      'group_post_image', 'group_post_comment', 'group_post_like', 'review',
                      'schedule_review_image', 'bank_api_credential', 'group_bank_account',
                      'account_transaction', 'fee_rule', 'settlement', 'settlement_item',
                      'settlement_payment', 'receipt', 'group_chat_room', 'group_chat_message'
                  )
                """, Integer.class);

        Integer socialIdentityIndexCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT index_name)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'member'
                  AND index_name = 'uq_member_social_identity'
                  AND non_unique = 0
                """, Integer.class);

        Integer socialLoginColumns = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'member'
                  AND column_name IN ('social_type', 'social_id')
                  AND is_nullable = 'NO'
                """, Integer.class);

        Integer memberIdentityTableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name = 'member_identity'
                """, Integer.class);

        Integer publicIdColumnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND column_name = 'public_id'
                """, Integer.class);

        Integer memberColumnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'member'
                  AND column_name IN ('social_id', 'social_type', 'profile_image_key', 'manner_temp', 'onboarding_completed')
                """, Integer.class);

        Integer deprecatedMemberColumnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'member'
                  AND column_name IN ('profile_image', 'manner_temperature')
                """, Integer.class);

        Integer nullableNicknameCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'member'
                  AND column_name = 'nickname'
                  AND is_nullable = 'YES'
                """, Integer.class);

        Integer memberGroupForeignKeyCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.key_column_usage
                WHERE constraint_schema = DATABASE()
                  AND referenced_table_name = 'member_group'
                  AND column_name = 'group_id'
                """, Integer.class);

        Integer foreignKeyCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE()
                  AND constraint_type = 'FOREIGN KEY'
                """, Integer.class);

        Integer nonRestrictForeignKeyCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.referential_constraints
                WHERE constraint_schema = DATABASE()
                  AND (delete_rule <> 'RESTRICT' OR update_rule <> 'RESTRICT')
                """, Integer.class);

        Integer softDeleteColumns = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND column_name = 'deleted_at'
                  AND is_nullable = 'YES'
                """, Integer.class);

        Integer plainAccountNumberColumns = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND column_name = 'account_number'
                """, Integer.class);

        Integer slotCheckConstraint = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE()
                  AND table_name = 'availability_slot'
                  AND constraint_name = 'ck_availability_slot_alignment'
                  AND constraint_type = 'CHECK'
                """, Integer.class);

        assertThat(tableCount).isEqualTo(P0_TABLE_COUNT);
        assertThat(socialIdentityIndexCount).isEqualTo(1);
        assertThat(socialLoginColumns).isEqualTo(2);
        assertThat(memberIdentityTableCount).isZero();
        assertThat(publicIdColumnCount).isZero();
        assertThat(memberColumnCount).isEqualTo(5);
        assertThat(deprecatedMemberColumnCount).isZero();
        assertThat(nullableNicknameCount).isEqualTo(1);
        assertThat(memberGroupForeignKeyCount).isEqualTo(9);
        assertThat(foreignKeyCount).isEqualTo(P0_FOREIGN_KEY_COUNT);
        assertThat(nonRestrictForeignKeyCount).isZero();
        assertThat(softDeleteColumns).isGreaterThanOrEqualTo(10);
        assertThat(plainAccountNumberColumns).isZero();
        assertThat(slotCheckConstraint).isEqualTo(1);
    }
}
