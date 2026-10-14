-- Issue #14: preserve the request and actor when a group is soft deleted.
-- The application writes closed_at and member_group.deleted_at from the same instant.

CREATE TABLE group_deletion_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    group_id BIGINT NOT NULL,
    deleted_by_member_id BIGINT NOT NULL,
    reason VARCHAR(255) NOT NULL,
    confirmation_text VARCHAR(100) NOT NULL,
    archive_content BOOLEAN NOT NULL,
    closed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_group_deletion_history_group UNIQUE (group_id),
    CONSTRAINT fk_group_deletion_history_group FOREIGN KEY (group_id) REFERENCES member_group (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_group_deletion_history_member FOREIGN KEY (deleted_by_member_id) REFERENCES member (id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
