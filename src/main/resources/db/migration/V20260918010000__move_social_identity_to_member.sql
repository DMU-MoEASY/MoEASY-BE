-- Social-only authentication: each member owns one Kakao or Google identity directly.
-- The UNIQUE constraint below deliberately rejects a member with multiple legacy identities;
-- resolve those records explicitly rather than silently discarding an identity.

ALTER TABLE member
    ADD COLUMN social_id VARCHAR(255) NULL AFTER id,
    ADD COLUMN social_type VARCHAR(20) NULL AFTER social_id;

ALTER TABLE member_identity
    ADD CONSTRAINT uq_member_identity_member UNIQUE (member_id);

UPDATE member AS m
JOIN member_identity AS mi ON mi.member_id = m.id
SET m.social_type = mi.provider,
    m.social_id = mi.provider_subject
WHERE mi.provider IN ('KAKAO', 'GOOGLE');

ALTER TABLE member
    MODIFY COLUMN social_type VARCHAR(20) NOT NULL,
    MODIFY COLUMN social_id VARCHAR(255) NOT NULL,
    ADD CONSTRAINT uq_member_social_identity UNIQUE (social_type, social_id),
    ADD CONSTRAINT ck_member_social_type CHECK (social_type IN ('KAKAO', 'GOOGLE'));

DROP TABLE member_identity;

ALTER TABLE member
    DROP CHECK ck_member_manner_temperature,
    RENAME COLUMN profile_image_key TO profile_image,
    RENAME COLUMN manner_temperature TO manner_temp,
    ADD CONSTRAINT ck_member_manner_temp CHECK (manner_temp BETWEEN 0 AND 100);

ALTER TABLE category
    DROP INDEX uq_category_public_id,
    DROP COLUMN public_id;

ALTER TABLE member
    DROP INDEX uq_member_public_id,
    DROP COLUMN public_id;

ALTER TABLE member_group
    DROP INDEX uq_member_group_public_id,
    DROP COLUMN public_id;

ALTER TABLE schedule
    DROP INDEX uq_schedule_public_id,
    DROP COLUMN public_id;

ALTER TABLE availability_poll
    DROP INDEX uq_availability_poll_public_id,
    DROP COLUMN public_id;

ALTER TABLE attendance_session
    DROP INDEX uq_attendance_session_public_id,
    DROP COLUMN public_id;

ALTER TABLE group_post
    DROP INDEX uq_group_post_public_id,
    DROP COLUMN public_id;

ALTER TABLE group_poll
    DROP INDEX uq_group_poll_public_id,
    DROP COLUMN public_id;

ALTER TABLE group_poll_option
    DROP INDEX uq_group_poll_option_public_id,
    DROP COLUMN public_id;

ALTER TABLE review
    DROP INDEX uq_review_public_id,
    DROP COLUMN public_id;

ALTER TABLE bank_api_credential
    DROP INDEX uq_bank_api_credential_public_id,
    DROP COLUMN public_id;

ALTER TABLE group_bank_account
    DROP INDEX uq_group_bank_account_public_id,
    DROP COLUMN public_id;

ALTER TABLE account_transaction
    DROP INDEX uq_account_transaction_public_id,
    DROP COLUMN public_id;

ALTER TABLE fee_rule
    DROP INDEX uq_fee_rule_public_id,
    DROP COLUMN public_id;

ALTER TABLE settlement
    DROP INDEX uq_settlement_public_id,
    DROP COLUMN public_id;

ALTER TABLE settlement_payment
    DROP INDEX uq_settlement_payment_public_id,
    DROP COLUMN public_id;

ALTER TABLE receipt
    DROP INDEX uq_receipt_public_id,
    DROP COLUMN public_id;

ALTER TABLE group_chat_room
    DROP INDEX uq_group_chat_room_public_id,
    DROP COLUMN public_id;

ALTER TABLE group_chat_message
    DROP INDEX uq_group_chat_message_public_id,
    DROP COLUMN public_id;
