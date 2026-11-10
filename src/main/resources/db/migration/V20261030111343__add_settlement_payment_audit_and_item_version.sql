-- 부분 납부가 추가되어도 같은 청구의 이전 요청을 식별할 수 있도록 업무 버전을 저장한다.
ALTER TABLE settlement_item
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_settlement_item_version CHECK (version >= 0);

-- 납부 대상(청구)과 처리 주체(운영진)를 구분하고 오등록 취소 감사 정보를 보존한다.
-- 수동 납부 요청의 중복·동시성 제어는 청구 버전으로 처리하므로 별도 키는 제거한다.
ALTER TABLE settlement_payment
    DROP INDEX uq_settlement_payment_idempotency_key,
    DROP COLUMN idempotency_key,
    ADD COLUMN source VARCHAR(32) NULL,
    ADD COLUMN recorded_by_group_member_id BIGINT NULL,
    ADD COLUMN cancelled_by_group_member_id BIGINT NULL,
    ADD COLUMN cancelled_at DATETIME(6) NULL,
    ADD COLUMN cancel_reason VARCHAR(255) NULL;

-- 기존 거래 연결 여부로 출처를 이관한다. 알 수 없는 등록자·취소 정보를 임의 생성하지 않는다.
UPDATE settlement_payment
SET source = CASE WHEN account_transaction_id IS NULL THEN 'MANUAL' ELSE 'BANK' END;

ALTER TABLE settlement_payment
    MODIFY COLUMN source VARCHAR(32) NOT NULL,
    ADD CONSTRAINT ck_settlement_payment_source CHECK (source IN ('MANUAL', 'BANK')),
    DROP CHECK ck_settlement_payment_amount,
    ADD CONSTRAINT ck_settlement_payment_amount
        CHECK (amount > 0 AND amount <= 9223372036854775807),
    ADD CONSTRAINT fk_settlement_payment_recorder
        FOREIGN KEY (recorded_by_group_member_id) REFERENCES group_member (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    ADD CONSTRAINT fk_settlement_payment_canceller
        FOREIGN KEY (cancelled_by_group_member_id) REFERENCES group_member (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    -- 과거 CANCELLED 기록은 취소 감사 정보가 없을 수 있다. 새 감사 정보는 세 필드를 함께 기록한다.
    ADD CONSTRAINT ck_settlement_payment_cancel_audit CHECK (
        (cancelled_by_group_member_id IS NULL AND cancelled_at IS NULL AND cancel_reason IS NULL)
        OR (status = 'CANCELLED' AND cancelled_by_group_member_id IS NOT NULL
            AND cancelled_at IS NOT NULL AND cancel_reason IS NOT NULL
            AND CHAR_LENGTH(TRIM(cancel_reason)) > 0)
    ),
    ADD INDEX ix_settlement_payment_item_created (settlement_item_id, created_at, id);
