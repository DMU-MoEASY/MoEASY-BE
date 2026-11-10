CREATE TABLE attendance_cutover (
    id TINYINT PRIMARY KEY CHECK (id = 1),
    established_at DATETIME(6) NOT NULL
);
INSERT INTO attendance_cutover VALUES (1, UTC_TIMESTAMP(6) + INTERVAL 9 HOUR);

CREATE TABLE membership_period (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    group_member_id BIGINT NOT NULL,
    starts_at DATETIME(6) NOT NULL,
    ends_at DATETIME(6),
    open_member BIGINT GENERATED ALWAYS AS (IF(ends_at IS NULL, group_member_id, NULL)) STORED UNIQUE,
    CHECK (ends_at IS NULL OR ends_at >= starts_at),
    FOREIGN KEY (group_member_id) REFERENCES group_member(id) ON DELETE RESTRICT ON UPDATE RESTRICT
);
INSERT INTO membership_period(group_member_id, starts_at)
SELECT gm.id, c.established_at FROM group_member gm JOIN member m ON m.id = gm.member_id
CROSS JOIN attendance_cutover c
WHERE gm.status='ACTIVE' AND gm.deleted_at IS NULL AND m.status='ACTIVE' AND m.deleted_at IS NULL;

ALTER TABLE schedule_rsvp ADD period_id BIGINT,
    ADD FOREIGN KEY (period_id) REFERENCES membership_period(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE attendance_session MODIFY qr_token_hash CHAR(64) NULL, MODIFY expires_at DATETIME(6) NULL,
    ADD kind VARCHAR(8) NOT NULL DEFAULT 'QR',
    ADD manual_schedule BIGINT GENERATED ALWAYS AS (IF(kind='MANUAL',schedule_id,NULL)) STORED UNIQUE,
    ADD CHECK ((kind='QR' AND qr_token_hash IS NOT NULL AND expires_at IS NOT NULL)
            OR (kind='MANUAL' AND qr_token_hash IS NULL)),
    ADD version BIGINT NOT NULL DEFAULT 0, ADD completed_at DATETIME(6), ADD completed_by BIGINT,
    ADD FOREIGN KEY (completed_by) REFERENCES group_member(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE attendance ADD recorded_by BIGINT,
    ADD FOREIGN KEY (recorded_by) REFERENCES group_member(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
CREATE TABLE schedule_end_snapshot (
    schedule_id BIGINT PRIMARY KEY, ends_at DATETIME(6) NOT NULL, recoverable BOOLEAN NOT NULL,
    member_count INT UNSIGNED NOT NULL DEFAULT 0, sealed BOOLEAN NOT NULL DEFAULT FALSE,
    FOREIGN KEY (schedule_id) REFERENCES schedule(id) ON DELETE RESTRICT ON UPDATE RESTRICT
);
CREATE TABLE schedule_end_member (
    schedule_id BIGINT NOT NULL, group_member_id BIGINT NOT NULL, attending BOOLEAN NOT NULL,
    PRIMARY KEY (schedule_id,group_member_id),
    FOREIGN KEY (schedule_id) REFERENCES schedule_end_snapshot(schedule_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    FOREIGN KEY (group_member_id) REFERENCES group_member(id) ON DELETE RESTRICT ON UPDATE RESTRICT
);
ALTER TABLE schedule ADD INDEX ix_schedule_tier(group_id, status, ends_at);
ALTER TABLE member_group ADD tier VARCHAR(8) NOT NULL DEFAULT 'bronze' CHECK (tier IN ('bronze','silver','gold')),
    ADD tier_as_of DATE;
CREATE TABLE group_tier_job (
    basis DATE NOT NULL, group_id BIGINT NOT NULL, state VARCHAR(12) NOT NULL DEFAULT 'PENDING',
    owner_token CHAR(36), lease_until DATETIME(6), attempts INT UNSIGNED NOT NULL DEFAULT 0,
    failure VARCHAR(120), frozen_at DATETIME(6), activities BIGINT UNSIGNED, evaluated BIGINT UNSIGNED,
    numerator BIGINT UNSIGNED, denominator BIGINT UNSIGNED, active_members BIGINT UNSIGNED,
    score DECIMAL(12,8), result VARCHAR(8), policy_version INT NOT NULL DEFAULT 1,
    PRIMARY KEY (basis,group_id), INDEX ix_tier_retry(basis,state,lease_until,group_id),
    CHECK (numerator <= denominator AND evaluated <= activities),
    FOREIGN KEY (group_id) REFERENCES member_group(id) ON DELETE RESTRICT ON UPDATE RESTRICT
);
