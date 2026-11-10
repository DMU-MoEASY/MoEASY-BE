-- local 프로파일에서만 적용되는 #38 시연 데이터. 운영 db/migration에 옮기지 않는다.
-- 시연 회원 380001 / 검색어 추천38: 일반17 + 낮은 관련성2 + 활성 광고2.
SET @running=(SELECT id FROM category WHERE code='RUNNING');
SET @study=(SELECT id FROM category WHERE code='STUDY');
SET @region=(SELECT id FROM region WHERE code='11680');
INSERT INTO member(id,social_type,social_id,nickname,primary_region_id,onboarding_completed) VALUES
    (380001,'KAKAO','local-feed38-viewer','추천38시연회원',@region,TRUE),
    (380002,'KAKAO','local-feed38-owner','추천38시연운영자',@region,TRUE);
INSERT INTO member_category(member_id,category_id) VALUES (380001,@running);
INSERT INTO member_group(id,category_id,name,description,region_code,place_name,address,max_members,join_policy,status,tier,created_at,deleted_at) VALUES
    (380101, @running, '[추천38] 일반01', '로컬 추천 피드 시연', '11680', '시연 장소', '시연 주소', 20, 'APPROVAL', 'ACTIVE', 'bronze', '2026-01-01 12:00:00', NULL),
    (380102, @running, '[추천38] 일반02', '로컬 추천 피드 시연', '11680', '시연 장소', '시연 주소', 20, 'APPROVAL', 'ACTIVE', 'bronze', '2026-01-01 12:00:00', NULL),
    (380103, @running, '[추천38] 일반03', '로컬 추천 피드 시연', '11680', '시연 장소', '시연 주소', 20, 'APPROVAL', 'ACTIVE', 'bronze', '2026-01-01 12:00:00', NULL),
    (380104, @running, '[추천38] 일반04', '로컬 추천 피드 시연', '11680', '시연 장소', '시연 주소', 20, 'APPROVAL', 'ACTIVE', 'bronze', '2026-01-01 12:00:00', NULL),
    (380105, @running, '[추천38] 일반05', '로컬 추천 피드 시연', '11680', '시연 장소', '시연 주소', 20, 'APPROVAL', 'ACTIVE', 'bronze', '2026-01-01 12:00:00', NULL),
    (380106, @running, '[추천38] 일반06', '로컬 추천 피드 시연', '11680', '시연 장소', '시연 주소', 20, 'APPROVAL', 'ACTIVE', 'bronze', '2026-01-01 12:00:00', NULL),
    (380107, @running, '[추천38] 일반07', '로컬 추천 피드 시연', '11680', '시연 장소', '시연 주소', 20, 'APPROVAL', 'ACTIVE', 'bronze', '2026-01-01 12:00:00', NULL),
    (380108, @running, '[추천38] 일반08', '로컬 추천 피드 시연', '11680', '시연 장소', '시연 주소', 20, 'APPROVAL', 'ACTIVE', 'bronze', '2026-01-01 12:00:00', NULL),
    (380109, @running, '[추천38] 일반09', '로컬 추천 피드 시연', '11680', '시연 장소', '시연 주소', 20, 'APPROVAL', 'ACTIVE', 'bronze', '2026-01-01 12:00:00', NULL),
    (380110, @running, '[추천38] 일반10', '로컬 추천 피드 시연', '11680', '시연 장소', '시연 주소', 20, 'APPROVAL', 'ACTIVE', 'bronze', '2026-01-01 12:00:00', NULL),
    (380111, @running, '[추천38] 일반11', '로컬 추천 피드 시연', '11680', '시연 장소', '시연 주소', 20, 'APPROVAL', 'ACTIVE', 'bronze', '2026-01-01 12:00:00', NULL),
    (380112, @running, '[추천38] 일반12', '로컬 추천 피드 시연', '11680', '시연 장소', '시연 주소', 20, 'APPROVAL', 'ACTIVE', 'bronze', '2026-01-01 12:00:00', NULL),
    (380113, @running, '[추천38] 일반13', '로컬 추천 피드 시연', '11680', '시연 장소', '시연 주소', 20, 'APPROVAL', 'ACTIVE', 'bronze', '2026-01-01 12:00:00', NULL),
    (380114, @running, '[추천38] 일반14', '로컬 추천 피드 시연', '11680', '시연 장소', '시연 주소', 20, 'APPROVAL', 'ACTIVE', 'bronze', '2026-01-01 12:00:00', NULL),
    (380115, @running, '[추천38] 일반15', '로컬 추천 피드 시연', '11680', '시연 장소', '시연 주소', 20, 'APPROVAL', 'ACTIVE', 'bronze', '2026-01-01 12:00:00', NULL),
    (380116, @running, '[추천38] 일반16', '로컬 추천 피드 시연', '11680', '시연 장소', '시연 주소', 20, 'APPROVAL', 'ACTIVE', 'bronze', '2026-01-01 12:00:00', NULL),
    (380117, @running, '[추천38] 일반17', '로컬 추천 피드 시연', '11680', '시연 장소', '시연 주소', 20, 'APPROVAL', 'ACTIVE', 'bronze', '2026-01-01 12:00:00', NULL),
    (380118, @study, '[추천38] 발견01', '로컬 추천 피드 시연', '11110', '시연 장소', '시연 주소', 20, 'APPROVAL', 'ACTIVE', 'bronze', '2026-01-01 12:00:00', NULL),
    (380119, @study, '[추천38] 발견02', '로컬 추천 피드 시연', '11110', '시연 장소', '시연 주소', 20, 'APPROVAL', 'ACTIVE', 'bronze', '2026-01-01 12:00:00', NULL),
    (380120, @running, '[추천38] 가입', '로컬 추천 피드 시연', '11680', '시연 장소', '시연 주소', 20, 'APPROVAL', 'ACTIVE', 'bronze', '2026-01-01 12:00:00', NULL),
    (380121, @running, '[추천38] 대기', '로컬 추천 피드 시연', '11680', '시연 장소', '시연 주소', 20, 'APPROVAL', 'ACTIVE', 'bronze', '2026-01-01 12:00:00', NULL),
    (380122, @running, '[추천38] 강퇴', '로컬 추천 피드 시연', '11680', '시연 장소', '시연 주소', 20, 'APPROVAL', 'ACTIVE', 'bronze', '2026-01-01 12:00:00', NULL),
    (380123, @running, '[추천38] 만석', '로컬 추천 피드 시연', '11680', '시연 장소', '시연 주소', 1, 'APPROVAL', 'ACTIVE', 'bronze', '2026-01-01 12:00:00', NULL),
    (380124, @running, '[추천38] 보관', '로컬 추천 피드 시연', '11680', '시연 장소', '시연 주소', 20, 'APPROVAL', 'ARCHIVED', 'bronze', '2026-01-01 12:00:00', NULL),
    (380125, @running, '[추천38] 폐쇄', '로컬 추천 피드 시연', '11680', '시연 장소', '시연 주소', 20, 'APPROVAL', 'CLOSED', 'bronze', '2026-01-01 12:00:00', NULL),
    (380126, @running, '[추천38] 삭제', '로컬 추천 피드 시연', '11680', '시연 장소', '시연 주소', 20, 'APPROVAL', 'ACTIVE', 'bronze', '2026-01-01 12:00:00', CURRENT_TIMESTAMP(6));
INSERT INTO group_member(group_id,member_id,role,status)
    SELECT id,380002,'OWNER','ACTIVE' FROM member_group WHERE id BETWEEN 380101 AND 380126;
INSERT INTO group_member(group_id,member_id,role,status,deleted_at) VALUES
    (380120,380001,'MEMBER','ACTIVE',NULL),
    (380122,380001,'MEMBER','KICKED',CURRENT_TIMESTAMP(6));
INSERT INTO group_join_request(group_id,member_id,introduction,status) VALUES
    (380121,380001,'시연 대기 신청','PENDING');
-- 기존 티어/출석 이력 규칙을 지키기 위해 활성 미삭제 멤버십의 기간도 연다.
INSERT INTO membership_period(group_member_id,starts_at)
    SELECT id,CURRENT_TIMESTAMP(6) FROM group_member
    WHERE group_id BETWEEN 380101 AND 380126 AND status='ACTIVE' AND deleted_at IS NULL;
INSERT INTO feed_advertisement(id,advertiser_name,title,image_url,landing_url,active,starts_at,ends_at) VALUES
    (380001,'MoEASY 시연 A','새로운 모험을 함께해요','/feed-ads/ad-a.svg','https://example.com/?banner=A',TRUE,'2000-01-01','2100-01-01'),
    (380002,'MoEASY 시연 B','이번 주말의 새로운 시작','/feed-ads/ad-b.svg','https://example.com/?banner=B',TRUE,'2000-01-01','2100-01-01'),
    (380003,'시작 전','미래 광고','/feed-ads/ad-a.svg','https://example.com',TRUE,'2100-01-01','2101-01-01'),
    (380004,'만료','지난 광고','/feed-ads/ad-a.svg','https://example.com',TRUE,'2000-01-01','2001-01-01'),
    (380005,'비활성','중지 광고','/feed-ads/ad-a.svg','https://example.com',FALSE,'2000-01-01','2100-01-01');
