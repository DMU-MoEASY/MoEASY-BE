-- Issue #9 P0 initial schema for MySQL 8.4.
-- Internal relations use BIGINT AUTO_INCREMENT; externally exposed resources use UUID public_id.
-- Secret material and account-number plaintext are stored outside this database.

CREATE TABLE category (
    id BIGINT NOT NULL AUTO_INCREMENT, public_id CHAR(36) NOT NULL, code VARCHAR(32) NOT NULL, name VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id), CONSTRAINT uq_category_public_id UNIQUE (public_id), CONSTRAINT uq_category_code UNIQUE (code), CONSTRAINT uq_category_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE member (
    id BIGINT NOT NULL AUTO_INCREMENT, public_id CHAR(36) NOT NULL, nickname VARCHAR(50) NOT NULL, status_message VARCHAR(255) NULL, profile_image_key VARCHAR(500) NULL,
    manner_temperature DECIMAL(4,1) NOT NULL DEFAULT 36.5, status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id), CONSTRAINT uq_member_public_id UNIQUE (public_id), CONSTRAINT uq_member_nickname UNIQUE (nickname),
    CONSTRAINT ck_member_status CHECK (status IN ('ACTIVE','BANNED','DELETED')), CONSTRAINT ck_member_manner_temperature CHECK (manner_temperature BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE member_identity (
    id BIGINT NOT NULL AUTO_INCREMENT, member_id BIGINT NOT NULL, provider VARCHAR(32) NOT NULL, provider_subject VARCHAR(320) NOT NULL, email VARCHAR(320) NULL, password_hash VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id), CONSTRAINT uq_member_identity_provider_subject UNIQUE (provider, provider_subject), CONSTRAINT uq_member_identity_email UNIQUE (email),
    CONSTRAINT ck_member_identity_provider CHECK (provider IN ('EMAIL','KAKAO','GOOGLE')),
    CONSTRAINT ck_member_identity_credentials CHECK ((provider = 'EMAIL' AND email IS NOT NULL AND password_hash IS NOT NULL) OR (provider IN ('KAKAO','GOOGLE') AND email IS NULL AND password_hash IS NULL)),
    CONSTRAINT fk_member_identity_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX ix_member_identity_member (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE member_category (
    id BIGINT NOT NULL AUTO_INCREMENT, member_id BIGINT NOT NULL, category_id BIGINT NOT NULL, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id), CONSTRAINT uq_member_category UNIQUE (member_id, category_id),
    CONSTRAINT fk_member_category_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_member_category_category FOREIGN KEY (category_id) REFERENCES category (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE member_group (
    id BIGINT NOT NULL AUTO_INCREMENT, public_id CHAR(36) NOT NULL, category_id BIGINT NOT NULL, name VARCHAR(100) NOT NULL, cover_image_key VARCHAR(500) NULL, description TEXT NOT NULL,
    region_code VARCHAR(32) NOT NULL, place_name VARCHAR(100) NOT NULL, address VARCHAR(255) NOT NULL, latitude DECIMAL(10,7) NULL, longitude DECIMAL(10,7) NULL, max_members INT UNSIGNED NOT NULL,
    join_policy VARCHAR(32) NOT NULL DEFAULT 'APPROVAL', status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id), CONSTRAINT uq_member_group_public_id UNIQUE (public_id), CONSTRAINT ck_member_group_max_members CHECK (max_members > 0),
    CONSTRAINT ck_member_group_join_policy CHECK (join_policy IN ('APPROVAL','OPEN')), CONSTRAINT ck_member_group_status CHECK (status IN ('ACTIVE','ARCHIVED','CLOSED')),
    CONSTRAINT ck_member_group_latitude CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90), CONSTRAINT ck_member_group_longitude CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    CONSTRAINT fk_member_group_category FOREIGN KEY (category_id) REFERENCES category (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX ix_member_group_category_status (category_id,status), INDEX ix_member_group_region_status (region_code,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE group_member (
    id BIGINT NOT NULL AUTO_INCREMENT, group_id BIGINT NOT NULL, member_id BIGINT NOT NULL, role VARCHAR(32) NOT NULL DEFAULT 'MEMBER', cohort VARCHAR(20) NULL, status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    joined_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    -- Rejoining restores this single row; it must not create a duplicate membership history row.
    CONSTRAINT uq_group_member UNIQUE (group_id,member_id), CONSTRAINT ck_group_member_role CHECK (role IN ('OWNER','MANAGER','MEMBER')), CONSTRAINT ck_group_member_status CHECK (status IN ('ACTIVE','LEFT','KICKED')),
    CONSTRAINT fk_group_member_group FOREIGN KEY (group_id) REFERENCES member_group (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_group_member_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX ix_group_member_member_status (member_id,status), INDEX ix_group_member_group_status (group_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE group_join_request (
    id BIGINT NOT NULL AUTO_INCREMENT, group_id BIGINT NOT NULL, member_id BIGINT NOT NULL, introduction VARCHAR(500) NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'PENDING', rejection_reason VARCHAR(500) NULL,
    processed_by_group_member_id BIGINT NULL, processed_at DATETIME(6) NULL,
    pending_member_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'PENDING' THEN member_id ELSE NULL END) VIRTUAL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id), CONSTRAINT uq_group_join_request_pending UNIQUE (group_id,pending_member_id), CONSTRAINT ck_group_join_request_status CHECK (status IN ('PENDING','APPROVED','REJECTED','CANCELLED')),
    CONSTRAINT fk_group_join_request_group FOREIGN KEY (group_id) REFERENCES member_group (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_group_join_request_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_group_join_request_processor FOREIGN KEY (processed_by_group_member_id) REFERENCES group_member (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX ix_group_join_request_group_status (group_id,status), INDEX ix_group_join_request_member_created_at (member_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE schedule (
    id BIGINT NOT NULL AUTO_INCREMENT, public_id CHAR(36) NOT NULL, group_id BIGINT NOT NULL, title VARCHAR(100) NOT NULL, description VARCHAR(500) NOT NULL, starts_at DATETIME(6) NOT NULL, ends_at DATETIME(6) NOT NULL,
    location_name VARCHAR(100) NOT NULL, location_address VARCHAR(255) NOT NULL, latitude DECIMAL(10,7) NULL, longitude DECIMAL(10,7) NULL, place_provider VARCHAR(32) NULL, place_id VARCHAR(100) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id), CONSTRAINT uq_schedule_public_id UNIQUE (public_id), CONSTRAINT ck_schedule_time_range CHECK (ends_at > starts_at),
    CONSTRAINT ck_schedule_place_provider CHECK (place_provider IS NULL OR place_provider IN ('KAKAO','GOOGLE')),
    CONSTRAINT ck_schedule_place_identity CHECK ((place_provider IS NULL AND place_id IS NULL) OR (place_provider IS NOT NULL AND place_id IS NOT NULL)),
    CONSTRAINT ck_schedule_latitude CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90), CONSTRAINT ck_schedule_longitude CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
    CONSTRAINT fk_schedule_group FOREIGN KEY (group_id) REFERENCES member_group (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX ix_schedule_group_starts_at (group_id,starts_at), INDEX ix_schedule_starts_at (starts_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE schedule_rsvp (
    id BIGINT NOT NULL AUTO_INCREMENT, schedule_id BIGINT NOT NULL, group_member_id BIGINT NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'UNDECIDED', responded_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id), CONSTRAINT uq_schedule_rsvp UNIQUE (schedule_id,group_member_id), CONSTRAINT ck_schedule_rsvp_status CHECK (status IN ('ATTENDING','NOT_ATTENDING','UNDECIDED')),
    CONSTRAINT fk_schedule_rsvp_schedule FOREIGN KEY (schedule_id) REFERENCES schedule (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_schedule_rsvp_group_member FOREIGN KEY (group_member_id) REFERENCES group_member (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX ix_schedule_rsvp_member_status (group_member_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE availability_poll (
    id BIGINT NOT NULL AUTO_INCREMENT, public_id CHAR(36) NOT NULL, schedule_id BIGINT NOT NULL, poll_start_at DATETIME(6) NOT NULL, poll_end_at DATETIME(6) NOT NULL, vote_deadline_at DATETIME(6) NOT NULL,
    slot_minutes TINYINT UNSIGNED NOT NULL DEFAULT 30, status VARCHAR(32) NOT NULL DEFAULT 'OPEN', created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id), CONSTRAINT uq_availability_poll_public_id UNIQUE (public_id), CONSTRAINT uq_availability_poll_schedule UNIQUE (schedule_id), CONSTRAINT ck_availability_poll_time_range CHECK (poll_end_at > poll_start_at),
    CONSTRAINT ck_availability_poll_slot_minutes CHECK (slot_minutes = 30),
    CONSTRAINT ck_availability_poll_alignment CHECK (MINUTE(poll_start_at) IN (0,30) AND MINUTE(poll_end_at) IN (0,30) AND SECOND(poll_start_at)=0 AND SECOND(poll_end_at)=0 AND MICROSECOND(poll_start_at)=0 AND MICROSECOND(poll_end_at)=0 AND MOD(TIMESTAMPDIFF(MINUTE,poll_start_at,poll_end_at),30)=0),
    CONSTRAINT ck_availability_poll_status CHECK (status IN ('OPEN','CLOSED','CONFIRMED')),
    CONSTRAINT fk_availability_poll_schedule FOREIGN KEY (schedule_id) REFERENCES schedule (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE availability_slot (
    id BIGINT NOT NULL AUTO_INCREMENT, availability_poll_id BIGINT NOT NULL, slot_at DATETIME(6) NOT NULL, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id), CONSTRAINT uq_availability_slot UNIQUE (availability_poll_id,slot_at),
    CONSTRAINT ck_availability_slot_alignment CHECK (MINUTE(slot_at) IN (0,30) AND SECOND(slot_at)=0 AND MICROSECOND(slot_at)=0),
    CONSTRAINT fk_availability_slot_poll FOREIGN KEY (availability_poll_id) REFERENCES availability_poll (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE availability_vote (
    id BIGINT NOT NULL AUTO_INCREMENT, availability_slot_id BIGINT NOT NULL, group_member_id BIGINT NOT NULL, is_available BOOLEAN NOT NULL DEFAULT TRUE, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id), CONSTRAINT uq_availability_vote UNIQUE (availability_slot_id,group_member_id),
    CONSTRAINT fk_availability_vote_slot FOREIGN KEY (availability_slot_id) REFERENCES availability_slot (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_availability_vote_group_member FOREIGN KEY (group_member_id) REFERENCES group_member (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX ix_availability_vote_member (group_member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE attendance_session (
    id BIGINT NOT NULL AUTO_INCREMENT, public_id CHAR(36) NOT NULL, schedule_id BIGINT NOT NULL, qr_token_hash CHAR(64) NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id), CONSTRAINT uq_attendance_session_public_id UNIQUE (public_id), CONSTRAINT uq_attendance_session_token_hash UNIQUE (qr_token_hash), CONSTRAINT ck_attendance_session_status CHECK (status IN ('ACTIVE','CLOSED')),
    CONSTRAINT fk_attendance_session_schedule FOREIGN KEY (schedule_id) REFERENCES schedule (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX ix_attendance_session_schedule_status (schedule_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE attendance (
    id BIGINT NOT NULL AUTO_INCREMENT, attendance_session_id BIGINT NOT NULL, group_member_id BIGINT NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'PRESENT', check_in_method VARCHAR(32) NOT NULL, checked_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id), CONSTRAINT uq_attendance_session_member UNIQUE (attendance_session_id,group_member_id), CONSTRAINT ck_attendance_status CHECK (status IN ('PRESENT','LATE','ABSENT')), CONSTRAINT ck_attendance_check_in_method CHECK (check_in_method IN ('QR','GPS','MANUAL')),
    CONSTRAINT fk_attendance_session FOREIGN KEY (attendance_session_id) REFERENCES attendance_session (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_attendance_group_member FOREIGN KEY (group_member_id) REFERENCES group_member (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX ix_attendance_member_checked_at (group_member_id,checked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE group_post (
    id BIGINT NOT NULL AUTO_INCREMENT, public_id CHAR(36) NOT NULL, group_id BIGINT NOT NULL, group_member_id BIGINT NOT NULL, schedule_id BIGINT NULL, content TEXT NOT NULL, is_notice BOOLEAN NOT NULL DEFAULT FALSE, notice_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id), CONSTRAINT uq_group_post_public_id UNIQUE (public_id),
    CONSTRAINT fk_group_post_group FOREIGN KEY (group_id) REFERENCES member_group (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_group_post_group_member FOREIGN KEY (group_member_id) REFERENCES group_member (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_group_post_schedule FOREIGN KEY (schedule_id) REFERENCES schedule (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX ix_group_post_group_created_at (group_id,created_at), INDEX ix_group_post_schedule (schedule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE group_poll (
    id BIGINT NOT NULL AUTO_INCREMENT, public_id CHAR(36) NOT NULL, group_id BIGINT NOT NULL, group_post_id BIGINT NULL, created_by_group_member_id BIGINT NOT NULL, title VARCHAR(100) NOT NULL, allow_multiple BOOLEAN NOT NULL DEFAULT FALSE, deadline_at DATETIME(6) NULL, status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id), CONSTRAINT uq_group_poll_public_id UNIQUE (public_id), CONSTRAINT uq_group_poll_post UNIQUE (group_post_id), CONSTRAINT ck_group_poll_status CHECK (status IN ('OPEN','CLOSED','CANCELLED')),
    CONSTRAINT fk_group_poll_group FOREIGN KEY (group_id) REFERENCES member_group (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_group_poll_post FOREIGN KEY (group_post_id) REFERENCES group_post (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_group_poll_creator FOREIGN KEY (created_by_group_member_id) REFERENCES group_member (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX ix_group_poll_group_status_deadline (group_id,status,deadline_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE group_poll_option (
    id BIGINT NOT NULL AUTO_INCREMENT, public_id CHAR(36) NOT NULL, group_poll_id BIGINT NOT NULL, content VARCHAR(255) NOT NULL, sort_order INT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id), CONSTRAINT uq_group_poll_option_public_id UNIQUE (public_id), CONSTRAINT uq_group_poll_option_sort_order UNIQUE (group_poll_id,sort_order),
    CONSTRAINT fk_group_poll_option_poll FOREIGN KEY (group_poll_id) REFERENCES group_poll (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE group_poll_vote (
    id BIGINT NOT NULL AUTO_INCREMENT, group_poll_option_id BIGINT NOT NULL, group_member_id BIGINT NOT NULL, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id), CONSTRAINT uq_group_poll_vote UNIQUE (group_poll_option_id,group_member_id),
    CONSTRAINT fk_group_poll_vote_option FOREIGN KEY (group_poll_option_id) REFERENCES group_poll_option (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_group_poll_vote_group_member FOREIGN KEY (group_member_id) REFERENCES group_member (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX ix_group_poll_vote_member (group_member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE group_post_image (
    id BIGINT NOT NULL AUTO_INCREMENT, group_post_id BIGINT NOT NULL, image_key VARCHAR(500) NOT NULL, sort_order INT UNSIGNED NOT NULL, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id), CONSTRAINT uq_group_post_image_sort_order UNIQUE (group_post_id,sort_order),
    CONSTRAINT fk_group_post_image_post FOREIGN KEY (group_post_id) REFERENCES group_post (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE group_post_comment (
    id BIGINT NOT NULL AUTO_INCREMENT, group_post_id BIGINT NOT NULL, group_member_id BIGINT NOT NULL, content TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id), CONSTRAINT fk_group_post_comment_post FOREIGN KEY (group_post_id) REFERENCES group_post (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_group_post_comment_group_member FOREIGN KEY (group_member_id) REFERENCES group_member (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX ix_group_post_comment_post_created_at (group_post_id,created_at), INDEX ix_group_post_comment_member (group_member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE group_post_like (
    id BIGINT NOT NULL AUTO_INCREMENT, group_post_id BIGINT NOT NULL, group_member_id BIGINT NOT NULL, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id), CONSTRAINT uq_group_post_like UNIQUE (group_post_id,group_member_id),
    CONSTRAINT fk_group_post_like_post FOREIGN KEY (group_post_id) REFERENCES group_post (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_group_post_like_group_member FOREIGN KEY (group_member_id) REFERENCES group_member (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX ix_group_post_like_member (group_member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE review (
    id BIGINT NOT NULL AUTO_INCREMENT, public_id CHAR(36) NOT NULL, group_member_id BIGINT NOT NULL, schedule_id BIGINT NOT NULL, content VARCHAR(300) NOT NULL, rating TINYINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id), CONSTRAINT uq_review_public_id UNIQUE (public_id), CONSTRAINT uq_review_schedule_member UNIQUE (schedule_id,group_member_id), CONSTRAINT ck_review_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT fk_review_group_member FOREIGN KEY (group_member_id) REFERENCES group_member (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_review_schedule FOREIGN KEY (schedule_id) REFERENCES schedule (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX ix_review_member_created_at (group_member_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE schedule_review_image (
    id BIGINT NOT NULL AUTO_INCREMENT, review_id BIGINT NOT NULL, image_key VARCHAR(500) NOT NULL, sort_order INT UNSIGNED NOT NULL, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id), CONSTRAINT uq_schedule_review_image_sort_order UNIQUE (review_id,sort_order),
    CONSTRAINT fk_schedule_review_image_review FOREIGN KEY (review_id) REFERENCES review (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE bank_api_credential (
    id BIGINT NOT NULL AUTO_INCREMENT, public_id CHAR(36) NOT NULL, member_id BIGINT NOT NULL, provider VARCHAR(32) NOT NULL, secret_ref VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id), CONSTRAINT uq_bank_api_credential_public_id UNIQUE (public_id), CONSTRAINT uq_bank_api_credential_secret_ref UNIQUE (secret_ref), CONSTRAINT ck_bank_api_credential_provider CHECK (provider IN ('NH','KB','WR','IBK','SH')),
    CONSTRAINT fk_bank_api_credential_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX ix_bank_api_credential_member_provider (member_id,provider)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE group_bank_account (
    id BIGINT NOT NULL AUTO_INCREMENT, public_id CHAR(36) NOT NULL, group_id BIGINT NOT NULL, bank_api_credential_id BIGINT NULL, bank_code VARCHAR(32) NOT NULL,
    account_number_secret_ref VARCHAR(500) NOT NULL, account_number_fingerprint CHAR(64) NOT NULL, account_number_masked VARCHAR(50) NOT NULL, account_holder VARCHAR(50) NOT NULL, secret_ref VARCHAR(500) NULL, last_synced_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id), CONSTRAINT uq_group_bank_account_public_id UNIQUE (public_id), CONSTRAINT uq_group_bank_account_secret_ref UNIQUE (account_number_secret_ref), CONSTRAINT uq_group_bank_account_fingerprint UNIQUE (bank_code,account_number_fingerprint), CONSTRAINT ck_group_bank_account_bank_code CHECK (bank_code IN ('NH','KB','WR','IBK','SH')),
    CONSTRAINT fk_group_bank_account_group FOREIGN KEY (group_id) REFERENCES member_group (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_group_bank_account_credential FOREIGN KEY (bank_api_credential_id) REFERENCES bank_api_credential (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX ix_group_bank_account_group (group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE account_transaction (
    id BIGINT NOT NULL AUTO_INCREMENT, public_id CHAR(36) NOT NULL, group_bank_account_id BIGINT NOT NULL, transaction_type VARCHAR(32) NOT NULL, transacted_at DATETIME(6) NOT NULL,
    description VARCHAR(100) NOT NULL, display_name VARCHAR(100) NULL, counterparty VARCHAR(100) NULL, amount BIGINT UNSIGNED NOT NULL, balance BIGINT NOT NULL, branch VARCHAR(100) NULL, memo VARCHAR(255) NULL, source_hash CHAR(64) NOT NULL, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id), CONSTRAINT uq_account_transaction_public_id UNIQUE (public_id), CONSTRAINT uq_account_transaction_source UNIQUE (group_bank_account_id,source_hash), CONSTRAINT ck_account_transaction_type CHECK (transaction_type IN ('DEPOSIT','WITHDRAWAL')), CONSTRAINT ck_account_transaction_amount CHECK (amount > 0),
    CONSTRAINT fk_account_transaction_account FOREIGN KEY (group_bank_account_id) REFERENCES group_bank_account (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX ix_account_transaction_account_transacted_at (group_bank_account_id,transacted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE fee_rule (
    id BIGINT NOT NULL AUTO_INCREMENT, public_id CHAR(36) NOT NULL, group_id BIGINT NOT NULL, name VARCHAR(100) NOT NULL, amount BIGINT UNSIGNED NOT NULL, billing_cycle VARCHAR(32) NULL, description VARCHAR(500) NULL, status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id), CONSTRAINT uq_fee_rule_public_id UNIQUE (public_id), CONSTRAINT ck_fee_rule_billing_cycle CHECK (billing_cycle IS NULL OR billing_cycle IN ('WEEKLY','MONTHLY','QUARTERLY','SEMI_ANNUAL','ANNUAL')), CONSTRAINT ck_fee_rule_status CHECK (status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT fk_fee_rule_group FOREIGN KEY (group_id) REFERENCES member_group (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX ix_fee_rule_group_status (group_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE settlement (
    id BIGINT NOT NULL AUTO_INCREMENT, public_id CHAR(36) NOT NULL, group_id BIGINT NOT NULL, fee_rule_id BIGINT NULL, schedule_id BIGINT NULL, created_by_group_member_id BIGINT NOT NULL, title VARCHAR(100) NOT NULL, description VARCHAR(500) NULL, due_at DATETIME(6) NULL, status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id), CONSTRAINT uq_settlement_public_id UNIQUE (public_id), CONSTRAINT ck_settlement_status CHECK (status IN ('DRAFT','OPEN','CLOSED','CANCELLED')),
    CONSTRAINT fk_settlement_group FOREIGN KEY (group_id) REFERENCES member_group (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_settlement_fee_rule FOREIGN KEY (fee_rule_id) REFERENCES fee_rule (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_settlement_schedule FOREIGN KEY (schedule_id) REFERENCES schedule (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_settlement_creator FOREIGN KEY (created_by_group_member_id) REFERENCES group_member (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX ix_settlement_group_status_due_at (group_id,status,due_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE settlement_item (
    id BIGINT NOT NULL AUTO_INCREMENT, settlement_id BIGINT NOT NULL, group_member_id BIGINT NOT NULL, amount BIGINT UNSIGNED NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id), CONSTRAINT uq_settlement_item_member UNIQUE (settlement_id,group_member_id), CONSTRAINT ck_settlement_item_status CHECK (status IN ('PENDING','PARTIALLY_PAID','PAID','REFUNDED','CANCELLED')),
    CONSTRAINT fk_settlement_item_settlement FOREIGN KEY (settlement_id) REFERENCES settlement (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_settlement_item_group_member FOREIGN KEY (group_member_id) REFERENCES group_member (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX ix_settlement_item_member_status (group_member_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE settlement_payment (
    id BIGINT NOT NULL AUTO_INCREMENT, public_id CHAR(36) NOT NULL, settlement_item_id BIGINT NOT NULL, account_transaction_id BIGINT NULL, payment_type VARCHAR(32) NOT NULL, status VARCHAR(32) NOT NULL DEFAULT 'PENDING', amount BIGINT UNSIGNED NOT NULL, occurred_at DATETIME(6) NULL, idempotency_key VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id), CONSTRAINT uq_settlement_payment_public_id UNIQUE (public_id), CONSTRAINT uq_settlement_payment_idempotency_key UNIQUE (idempotency_key), CONSTRAINT ck_settlement_payment_type CHECK (payment_type IN ('PAYMENT','REFUND')), CONSTRAINT ck_settlement_payment_status CHECK (status IN ('PENDING','COMPLETED','FAILED','CANCELLED')), CONSTRAINT ck_settlement_payment_amount CHECK (amount > 0),
    CONSTRAINT fk_settlement_payment_item FOREIGN KEY (settlement_item_id) REFERENCES settlement_item (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_settlement_payment_transaction FOREIGN KEY (account_transaction_id) REFERENCES account_transaction (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX ix_settlement_payment_item_status (settlement_item_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE receipt (
    id BIGINT NOT NULL AUTO_INCREMENT, public_id CHAR(36) NOT NULL, settlement_id BIGINT NOT NULL, image_key VARCHAR(500) NOT NULL, merchant_name VARCHAR(100) NULL, amount BIGINT UNSIGNED NOT NULL, paid_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id), CONSTRAINT uq_receipt_public_id UNIQUE (public_id), CONSTRAINT ck_receipt_amount CHECK (amount > 0),
    CONSTRAINT fk_receipt_settlement FOREIGN KEY (settlement_id) REFERENCES settlement (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX ix_receipt_settlement_paid_at (settlement_id,paid_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE group_chat_room (
    id BIGINT NOT NULL AUTO_INCREMENT, public_id CHAR(36) NOT NULL, group_id BIGINT NOT NULL, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id), CONSTRAINT uq_group_chat_room_public_id UNIQUE (public_id), CONSTRAINT uq_group_chat_room_group UNIQUE (group_id),
    CONSTRAINT fk_group_chat_room_group FOREIGN KEY (group_id) REFERENCES member_group (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE group_chat_message (
    id BIGINT NOT NULL AUTO_INCREMENT, public_id CHAR(36) NOT NULL, group_chat_room_id BIGINT NOT NULL, group_member_id BIGINT NULL, message_type VARCHAR(32) NOT NULL, content TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6), deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id), CONSTRAINT uq_group_chat_message_public_id UNIQUE (public_id), CONSTRAINT ck_group_chat_message_type CHECK (message_type IN ('TEXT','IMAGE','SYSTEM')),
    CONSTRAINT ck_group_chat_message_sender CHECK ((message_type = 'SYSTEM' AND group_member_id IS NULL) OR (message_type IN ('TEXT','IMAGE') AND group_member_id IS NOT NULL)),
    CONSTRAINT fk_group_chat_message_room FOREIGN KEY (group_chat_room_id) REFERENCES group_chat_room (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_group_chat_message_group_member FOREIGN KEY (group_member_id) REFERENCES group_member (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX ix_group_chat_message_room_created_at (group_chat_room_id,created_at), INDEX ix_group_chat_message_member_created_at (group_member_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
