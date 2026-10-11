CREATE TABLE remember_me_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    series VARCHAR(64) NOT NULL UNIQUE,
    token_hash VARCHAR(64) NOT NULL,
    user_agent VARCHAR(255),
    ip_address VARCHAR(45),
    expires_at TIMESTAMP(6) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_remember_me_user FOREIGN KEY (user_id) REFERENCES user_accounts (id) ON DELETE CASCADE,
    INDEX ix_remember_me_user_id (user_id),
    INDEX ix_remember_me_expires_at (expires_at)
);
