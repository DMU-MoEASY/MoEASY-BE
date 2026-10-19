-- 생성 시 확정한 총액과 분할 방식을 보존한다.
-- 정산 기능 도입 전의 빈 테이블을 대상으로 한다. 기존 행이 있으면 임의의 총액·방식을
-- 채우지 않고 제약 위반으로 실패하므로, 적용 전에 별도 데이터 이관 정책을 정해야 한다.
ALTER TABLE settlement
    ADD COLUMN total_amount BIGINT UNSIGNED NOT NULL,
    ADD COLUMN split_mode VARCHAR(32) NOT NULL,
    ADD CONSTRAINT ck_settlement_total_amount
        CHECK (total_amount > 0 AND total_amount <= 9223372036854775807),
    ADD CONSTRAINT ck_settlement_split_mode CHECK (split_mode IN ('EQUAL', 'CUSTOM')),
    DROP CHECK ck_settlement_status,
    ADD CONSTRAINT ck_settlement_status CHECK (status IN ('OPEN', 'CLOSED', 'CANCELLED'));

-- 일부만 납부한 청구도 전액 납부 전까지 PENDING으로 관리한다.
UPDATE settlement_item SET status = 'PENDING' WHERE status = 'PARTIALLY_PAID';

-- Java Long으로 손실 없이 처리할 수 있는 양수 원 단위 금액만 저장한다.
ALTER TABLE settlement_item
    ADD CONSTRAINT ck_settlement_item_amount
        CHECK (amount > 0 AND amount <= 9223372036854775807),
    DROP CHECK ck_settlement_item_status,
    ADD CONSTRAINT ck_settlement_item_status
        CHECK (status IN ('PENDING', 'PAID', 'REFUNDED', 'CANCELLED'));
