-- 지역의 사용 가능 여부는 deleted_at으로 관리한다.
ALTER TABLE region DROP COLUMN active;
