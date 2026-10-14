-- Preserve which member received each Group cover upload key.
-- A Group can accept a key only after checking this issuance record and the S3 object.

CREATE TABLE group_cover_upload (
    id BIGINT NOT NULL AUTO_INCREMENT,
    object_key VARCHAR(500) NOT NULL,
    member_id BIGINT NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    issued_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_group_cover_upload_object_key UNIQUE (object_key),
    CONSTRAINT fk_group_cover_upload_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX ix_group_cover_upload_member (member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
