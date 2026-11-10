-- Store authenticated ciphertext locally; legacy secret_ref columns remain opaque locators.
ALTER TABLE bank_api_credential
    ADD COLUMN crypto_context CHAR(36) NULL,
    ADD COLUMN encrypted_api_key TEXT NULL,
    ADD COLUMN encrypted_secret_key TEXT NULL;

ALTER TABLE group_bank_account
    ADD COLUMN crypto_context CHAR(36) NULL,
    ADD COLUMN encrypted_account_number TEXT NULL,
    ADD COLUMN next_sync_at DATETIME(6) NULL,
    ADD COLUMN sync_token VARCHAR(36) NULL,
    ADD COLUMN sync_lease_until DATETIME(6) NULL,
    ADD COLUMN last_sync_error VARCHAR(32) NULL,
    ADD COLUMN active_group_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN deleted_at IS NULL THEN group_id ELSE NULL END) STORED,
    ADD CONSTRAINT uq_group_bank_account_active_group UNIQUE (active_group_id);
