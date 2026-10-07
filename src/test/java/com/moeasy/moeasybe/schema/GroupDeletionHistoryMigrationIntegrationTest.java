package com.moeasy.moeasybe.schema;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class GroupDeletionHistoryMigrationIntegrationTest {
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("삭제 이력은 Group당 한 건이고 Group·수행자 FK 및 필수 컬럼을 가진다")
    void migration_constraints_matchDeletionContract() {
        // given
        String table = "group_deletion_history";

        // when
        Integer uniqueGroup = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.statistics
                WHERE table_schema = DATABASE() AND table_name = ?
                  AND index_name = 'uq_group_deletion_history_group'
                  AND column_name = 'group_id' AND non_unique = 0
                """, Integer.class, table);
        Integer foreignKeys = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.key_column_usage
                WHERE constraint_schema = DATABASE() AND table_name = ?
                  AND constraint_name IN ('fk_group_deletion_history_group', 'fk_group_deletion_history_member')
                  AND referenced_table_name IS NOT NULL
                """, Integer.class, table);
        Integer requiredColumns = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = ?
                  AND column_name IN ('group_id', 'deleted_by_member_id', 'reason',
                    'confirmation_text', 'archive_content', 'closed_at')
                  AND is_nullable = 'NO'
                """, Integer.class, table);
        Integer restrictRules = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.referential_constraints
                WHERE constraint_schema = DATABASE() AND table_name = ?
                  AND constraint_name IN ('fk_group_deletion_history_group', 'fk_group_deletion_history_member')
                  AND delete_rule = 'RESTRICT' AND update_rule = 'RESTRICT'
                """, Integer.class, table);

        // then
        assertThat(uniqueGroup).isEqualTo(1);
        assertThat(foreignKeys).isEqualTo(2);
        assertThat(requiredColumns).isEqualTo(6);
        assertThat(restrictRules).isEqualTo(2);
    }
}
