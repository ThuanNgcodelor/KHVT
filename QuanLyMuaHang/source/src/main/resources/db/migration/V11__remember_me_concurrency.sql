ALTER TABLE remember_me_tokens
    ADD COLUMN previous_token_hash VARCHAR(64) NULL AFTER token_hash,
    ADD COLUMN last_rotated_at TIMESTAMP(6) NULL AFTER expires_at;
