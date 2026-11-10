-- 추천 후보는 활성 미삭제 모임만 찾고, 현재 인원은 모임별 활성 미삭제 멤버십만 센다.
-- 기존 (group_id,status) 인덱스는 다른 조회에서도 사용하므로 유지한다.
CREATE INDEX ix_group_member_feed_count ON group_member (group_id, status, deleted_at);
CREATE INDEX ix_member_group_feed_eligible ON member_group (status, deleted_at);
