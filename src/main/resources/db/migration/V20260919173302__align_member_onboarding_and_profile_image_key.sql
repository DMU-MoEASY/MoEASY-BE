-- 소셜 로그인 직후에는 프로필을 입력하지 않고, 온보딩에서 회원 프로필을 완성한다.
ALTER TABLE member
    MODIFY COLUMN nickname VARCHAR(50) NULL,
    RENAME COLUMN profile_image TO profile_image_key,
    ADD COLUMN onboarding_completed BOOLEAN NOT NULL DEFAULT FALSE AFTER profile_image_key;
