-- MySQL may use the old unique index as the supporting index for the import_id
-- foreign key. Create its non-unique replacement first so the unique index can
-- be removed without dropping or rebuilding the foreign key.
CREATE INDEX idx_hr_payroll_campaign_import_created ON hr_payroll_campaigns(import_id, created_at);
ALTER TABLE hr_payroll_campaigns DROP INDEX uk_hr_payroll_campaign_import;
ALTER TABLE hr_payroll_campaigns
    ADD COLUMN selection_mode VARCHAR(24) NOT NULL DEFAULT 'ALL_ELIGIBLE';

CREATE TABLE hr_payroll_test_recipients (
    id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    status VARCHAR(16) NOT NULL,
    link_token_hash VARCHAR(64) NULL,
    link_expires_at DATETIME(6) NULL,
    telegram_user_id BIGINT NULL,
    telegram_chat_id BIGINT NULL,
    telegram_username VARCHAR(255) NULL,
    linked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_payroll_test_recipients PRIMARY KEY (id),
    CONSTRAINT uk_hr_payroll_test_recipient_user UNIQUE (user_id),
    CONSTRAINT uk_hr_payroll_test_recipient_token UNIQUE (link_token_hash),
    INDEX idx_hr_payroll_test_recipient_status (status, updated_at)
);

CREATE TABLE hr_payroll_test_deliveries (
    id VARCHAR(36) NOT NULL,
    import_row_id VARCHAR(36) NOT NULL,
    test_recipient_id VARCHAR(36) NOT NULL,
    employee_code VARCHAR(32) NOT NULL,
    employee_name VARCHAR(255) NOT NULL,
    message_snapshot TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    last_error VARCHAR(1000) NULL,
    sent_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_payroll_test_deliveries PRIMARY KEY (id),
    CONSTRAINT fk_hr_payroll_test_delivery_row FOREIGN KEY (import_row_id) REFERENCES hr_payroll_import_rows(id) ON DELETE CASCADE,
    CONSTRAINT fk_hr_payroll_test_delivery_recipient FOREIGN KEY (test_recipient_id) REFERENCES hr_payroll_test_recipients(id) ON DELETE RESTRICT,
    INDEX idx_hr_payroll_test_delivery_actor (created_by_actor, created_at),
    INDEX idx_hr_payroll_test_delivery_row (import_row_id, created_at)
);

ALTER TABLE hr_payroll_deliveries
    ADD COLUMN last_resend_reason VARCHAR(500) NULL;
