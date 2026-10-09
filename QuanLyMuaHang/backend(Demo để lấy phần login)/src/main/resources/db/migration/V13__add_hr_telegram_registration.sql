CREATE TABLE hr_telegram_registrations (
    id VARCHAR(36) NOT NULL,
    employee_id VARCHAR(36) NULL,
    entered_employee_code VARCHAR(32) NULL,
    phone_number VARCHAR(32) NULL,
    telegram_user_id BIGINT NULL,
    telegram_chat_id BIGINT NULL,
    telegram_username VARCHAR(255) NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    last_attempt_at DATETIME(6) NULL,
    reviewed_at DATETIME(6) NULL,
    reviewed_by_actor VARCHAR(320) NULL,
    review_note VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL DEFAULT 'telegram',
    updated_by_actor VARCHAR(320) NOT NULL DEFAULT 'telegram',
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_telegram_registrations PRIMARY KEY (id),
    CONSTRAINT fk_hr_telegram_registration_employee FOREIGN KEY (employee_id) REFERENCES hr_employees(id),
    INDEX idx_hr_telegram_registration_status (status, created_at),
    INDEX idx_hr_telegram_registration_user (telegram_user_id, status),
    INDEX idx_hr_telegram_registration_employee (employee_id, created_at)
);

CREATE TABLE hr_employee_telegram_bindings (
    id VARCHAR(36) NOT NULL,
    employee_id VARCHAR(36) NOT NULL,
    telegram_user_id BIGINT NULL,
    telegram_chat_id BIGINT NULL,
    telegram_username VARCHAR(255) NULL,
    phone_number VARCHAR(32) NULL,
    status VARCHAR(16) NOT NULL,
    linked_at DATETIME(6) NULL,
    revoked_at DATETIME(6) NULL,
    revoked_reason VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL DEFAULT 'telegram',
    updated_by_actor VARCHAR(320) NOT NULL DEFAULT 'telegram',
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_employee_telegram_bindings PRIMARY KEY (id),
    CONSTRAINT uk_hr_telegram_binding_employee UNIQUE (employee_id),
    CONSTRAINT fk_hr_telegram_binding_employee FOREIGN KEY (employee_id) REFERENCES hr_employees(id),
    INDEX idx_hr_telegram_binding_user_status (telegram_user_id, status),
    INDEX idx_hr_telegram_binding_status (status)
);

INSERT INTO hr_system_settings (setting_key, setting_value, category, description, created_by_actor, updated_by_actor)
VALUES
('telegram.bot.username', '', 'TELEGRAM', 'Username bot Telegram không có ký tự @', 'system', 'system'),
('telegram.enabled', 'false', 'TELEGRAM', 'Cho phép tiếp nhận đăng ký Telegram', 'system', 'system')
ON DUPLICATE KEY UPDATE setting_key = VALUES(setting_key);
