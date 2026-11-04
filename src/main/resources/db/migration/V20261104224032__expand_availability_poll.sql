-- No runtime availability code existed before #29; the inspected local DB has zero polls.
-- Fail before changing schema if another environment has legacy polls: their daily settings
-- and original CONFIRMED times cannot be reconstructed safely. Export and migrate explicitly.
CREATE TEMPORARY TABLE availability_migration_guard (
    row_count BIGINT NOT NULL, CONSTRAINT ck_availability_empty CHECK (row_count = 0)
);
INSERT INTO availability_migration_guard SELECT COUNT(*) FROM availability_poll;
DROP TEMPORARY TABLE availability_migration_guard;

ALTER TABLE schedule ADD COLUMN time_revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE availability_poll
    ADD COLUMN start_date DATE NOT NULL,
    ADD COLUMN day_count INT NOT NULL,
    ADD COLUMN start_hour INT NOT NULL,
    ADD COLUMN end_hour INT NOT NULL,
    ADD COLUMN revision BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN confirmed_start_at DATETIME(6) NULL,
    ADD COLUMN confirmed_end_at DATETIME(6) NULL,
    ADD COLUMN original_start_at DATETIME(6) NULL,
    ADD COLUMN original_end_at DATETIME(6) NULL,
    ADD COLUMN confirmed_time_revision BIGINT NULL,
    ADD COLUMN confirmation_token VARCHAR(64) NULL,
    ADD CONSTRAINT ck_availability_daily CHECK (day_count IN (3,5,7,14) AND start_hour BETWEEN 0 AND 23 AND end_hour BETWEEN 1 AND 24 AND end_hour > start_hour),
    ADD INDEX ix_availability_due (status, vote_deadline_at, id);

CREATE TABLE availability_response (
    id BIGINT NOT NULL AUTO_INCREMENT,
    availability_poll_id BIGINT NOT NULL,
    group_member_id BIGINT NOT NULL,
    membership_joined_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_availability_response UNIQUE (availability_poll_id, group_member_id),
    CONSTRAINT fk_availability_response_poll FOREIGN KEY (availability_poll_id) REFERENCES availability_poll(id) ON DELETE RESTRICT,
    CONSTRAINT fk_availability_response_member FOREIGN KEY (group_member_id) REFERENCES group_member(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
