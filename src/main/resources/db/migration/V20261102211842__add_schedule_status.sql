-- 취소와 삭제를 구분하고 기존 일정은 정상 상태로 유지한다.
ALTER TABLE schedule
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'SCHEDULED',
    ADD CONSTRAINT ck_schedule_status CHECK (status IN ('SCHEDULED', 'CANCELLED')),
    ADD INDEX ix_schedule_group_created (group_id, deleted_at, created_at DESC, id DESC);
