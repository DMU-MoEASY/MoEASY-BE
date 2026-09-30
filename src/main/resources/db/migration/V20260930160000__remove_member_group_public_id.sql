-- Group APIs identify groups by the internal BIGINT member_group.id.
-- Drop the public UUID index and column when present; dropping the column also
-- removes any other index that depends on it.
SET @member_group_has_public_id_index = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'member_group'
      AND index_name = 'uq_member_group_public_id'
      AND column_name = 'public_id'
);

SET @member_group_drop_public_id_index_sql = IF(
    @member_group_has_public_id_index > 0,
    'ALTER TABLE member_group DROP INDEX uq_member_group_public_id',
    'SELECT 1'
);
PREPARE member_group_drop_public_id_index_statement FROM @member_group_drop_public_id_index_sql;
EXECUTE member_group_drop_public_id_index_statement;
DEALLOCATE PREPARE member_group_drop_public_id_index_statement;

SET @member_group_has_public_id_column = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'member_group'
      AND column_name = 'public_id'
);

SET @member_group_drop_public_id_column_sql = IF(
    @member_group_has_public_id_column > 0,
    'ALTER TABLE member_group DROP COLUMN public_id',
    'SELECT 1'
);
PREPARE member_group_drop_public_id_column_statement FROM @member_group_drop_public_id_column_sql;
EXECUTE member_group_drop_public_id_column_statement;
DEALLOCATE PREPARE member_group_drop_public_id_column_statement;
