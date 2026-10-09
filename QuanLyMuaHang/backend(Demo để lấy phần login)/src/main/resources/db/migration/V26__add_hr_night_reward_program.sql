ALTER TABLE hr_attendance_shift_policies
    ADD COLUMN counts_toward_night_reward BOOLEAN NOT NULL DEFAULT FALSE AFTER night_allowance_amount;

UPDATE hr_attendance_shift_policies
SET counts_toward_night_reward = TRUE
WHERE code = 'CN_18_5';

CREATE TABLE hr_night_reward_programs (
    id VARCHAR(36) NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE NULL,
    qualifying_night_threshold INT NOT NULL,
    eligible_policy_group VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_night_reward_program PRIMARY KEY (id),
    CONSTRAINT uk_hr_night_reward_program_code UNIQUE (code),
    CONSTRAINT ck_hr_night_reward_program_dates CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_hr_night_reward_program_threshold CHECK (qualifying_night_threshold > 0),
    CONSTRAINT ck_hr_night_reward_program_group CHECK (eligible_policy_group IN ('PRODUCTION_WORKER', 'KCS', 'OFFICE')),
    INDEX idx_hr_night_reward_program_active (active, effective_from, effective_to)
);

CREATE TABLE hr_night_reward_monthly_qualifications (
    id VARCHAR(36) NOT NULL,
    program_id VARCHAR(36) NOT NULL,
    employee_id VARCHAR(36) NOT NULL,
    employee_code VARCHAR(32) NOT NULL,
    employee_name VARCHAR(255) NOT NULL,
    attendance_month VARCHAR(7) NOT NULL,
    revision INT NOT NULL DEFAULT 1,
    status VARCHAR(32) NOT NULL,
    actual_night_shift_count INT NOT NULL DEFAULT 0,
    required_night_shift_count INT NOT NULL,
    qualification_reason VARCHAR(1000) NOT NULL,
    source_snapshot_json JSON NOT NULL,
    finalized_at DATETIME(6) NOT NULL,
    finalized_by_actor VARCHAR(320) NOT NULL,
    stale_at DATETIME(6) NULL,
    stale_by_actor VARCHAR(320) NULL,
    stale_reason VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_night_reward_monthly_qualification PRIMARY KEY (id),
    CONSTRAINT fk_hr_night_reward_month_program FOREIGN KEY (program_id) REFERENCES hr_night_reward_programs(id) ON DELETE RESTRICT,
    CONSTRAINT fk_hr_night_reward_month_employee FOREIGN KEY (employee_id) REFERENCES hr_employees(id) ON DELETE RESTRICT,
    CONSTRAINT uk_hr_night_reward_month_revision UNIQUE (program_id, employee_id, attendance_month, revision),
    CONSTRAINT ck_hr_night_reward_month_format CHECK (attendance_month REGEXP '^[0-9]{4}-[0-9]{2}$'),
    CONSTRAINT ck_hr_night_reward_month_status CHECK (status IN ('QUALIFIED', 'NOT_QUALIFIED', 'QUALIFIED_EXCEPTION', 'EXCLUDED', 'STALE')),
    CONSTRAINT ck_hr_night_reward_month_counts CHECK (actual_night_shift_count >= 0 AND required_night_shift_count > 0),
    INDEX idx_hr_night_reward_month_employee (employee_id, attendance_month, status),
    INDEX idx_hr_night_reward_month_program (program_id, attendance_month, status)
);

CREATE TABLE hr_night_reward_qualification_shifts (
    id VARCHAR(36) NOT NULL,
    qualification_id VARCHAR(36) NOT NULL,
    attendance_shift_id VARCHAR(36) NOT NULL,
    source_import_id VARCHAR(36) NOT NULL,
    work_date DATE NOT NULL,
    shift_code VARCHAR(40) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_night_reward_qualification_shift PRIMARY KEY (id),
    CONSTRAINT fk_hr_night_reward_source_qualification FOREIGN KEY (qualification_id)
        REFERENCES hr_night_reward_monthly_qualifications(id) ON DELETE CASCADE,
    CONSTRAINT uk_hr_night_reward_source_shift UNIQUE (qualification_id, attendance_shift_id),
    INDEX idx_hr_night_reward_source_import (source_import_id),
    INDEX idx_hr_night_reward_source_qualification (qualification_id, work_date)
);

CREATE TABLE hr_night_reward_exceptions (
    id VARCHAR(36) NOT NULL,
    program_id VARCHAR(36) NOT NULL,
    employee_id VARCHAR(36) NOT NULL,
    employee_code VARCHAR(32) NOT NULL,
    attendance_month VARCHAR(7) NOT NULL,
    exception_type VARCHAR(40) NOT NULL,
    status VARCHAR(16) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    evidence_reference VARCHAR(1000) NULL,
    reviewed_at DATETIME(6) NULL,
    reviewed_by_actor VARCHAR(320) NULL,
    review_reason VARCHAR(1000) NULL,
    cancelled_at DATETIME(6) NULL,
    cancelled_by_actor VARCHAR(320) NULL,
    cancellation_reason VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_night_reward_exception PRIMARY KEY (id),
    CONSTRAINT fk_hr_night_reward_exception_program FOREIGN KEY (program_id) REFERENCES hr_night_reward_programs(id) ON DELETE RESTRICT,
    CONSTRAINT fk_hr_night_reward_exception_employee FOREIGN KEY (employee_id) REFERENCES hr_employees(id) ON DELETE RESTRICT,
    CONSTRAINT ck_hr_night_reward_exception_status CHECK (status IN ('DRAFT', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_hr_night_reward_exception_type CHECK (exception_type IN ('COMPANY_PRODUCTION_PLAN', 'COMPANY_MAINTENANCE', 'COMPANY_REDUCED_ORDERS', 'OTHER_COMPANY_APPROVED')),
    INDEX idx_hr_night_reward_exception_month (program_id, attendance_month, status),
    INDEX idx_hr_night_reward_exception_employee (employee_id, attendance_month)
);

CREATE TABLE hr_night_reward_ledger (
    id VARCHAR(36) NOT NULL,
    program_id VARCHAR(36) NOT NULL,
    employee_id VARCHAR(36) NOT NULL,
    employee_code VARCHAR(32) NOT NULL,
    qualification_id VARCHAR(36) NULL,
    track_code VARCHAR(32) NOT NULL,
    entry_type VARCHAR(32) NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_night_reward_ledger PRIMARY KEY (id),
    CONSTRAINT fk_hr_night_reward_ledger_program FOREIGN KEY (program_id) REFERENCES hr_night_reward_programs(id) ON DELETE RESTRICT,
    CONSTRAINT fk_hr_night_reward_ledger_employee FOREIGN KEY (employee_id) REFERENCES hr_employees(id) ON DELETE RESTRICT,
    CONSTRAINT fk_hr_night_reward_ledger_qualification FOREIGN KEY (qualification_id)
        REFERENCES hr_night_reward_monthly_qualifications(id) ON DELETE RESTRICT,
    CONSTRAINT uk_hr_night_reward_ledger_qualification UNIQUE (qualification_id, track_code, entry_type),
    CONSTRAINT ck_hr_night_reward_ledger_track CHECK (track_code IN ('PERIODIC_NIGHT', 'LOYALTY_NIGHT')),
    CONSTRAINT ck_hr_night_reward_ledger_type CHECK (entry_type IN ('MONTH_CREDIT', 'MONTH_REVERSAL', 'CYCLE_MATURED')),
    INDEX idx_hr_night_reward_ledger_progress (program_id, employee_id, track_code, created_at)
);

CREATE TABLE hr_night_reward_entitlements (
    id VARCHAR(36) NOT NULL,
    program_id VARCHAR(36) NOT NULL,
    employee_id VARCHAR(36) NOT NULL,
    employee_code VARCHAR(32) NOT NULL,
    track_code VARCHAR(32) NOT NULL,
    qualified_months_at_maturity INT NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    status VARCHAR(16) NOT NULL,
    source_snapshot_json JSON NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_night_reward_entitlement PRIMARY KEY (id),
    CONSTRAINT fk_hr_night_reward_entitlement_program FOREIGN KEY (program_id) REFERENCES hr_night_reward_programs(id) ON DELETE RESTRICT,
    CONSTRAINT fk_hr_night_reward_entitlement_employee FOREIGN KEY (employee_id) REFERENCES hr_employees(id) ON DELETE RESTRICT,
    CONSTRAINT ck_hr_night_reward_entitlement_track CHECK (track_code IN ('PERIODIC_NIGHT', 'LOYALTY_NIGHT')),
    CONSTRAINT ck_hr_night_reward_entitlement_status CHECK (status IN ('DRAFT', 'STALE')),
    CONSTRAINT ck_hr_night_reward_entitlement_months CHECK (qualified_months_at_maturity > 0),
    INDEX idx_hr_night_reward_entitlement_employee (employee_id, track_code, status)
);

INSERT INTO hr_night_reward_programs (
    id, code, name, effective_from, qualifying_night_threshold, eligible_policy_group, active,
    created_by_actor, updated_by_actor
) VALUES (
    'night-reward-2026-production', 'NIGHT_REWARD_2026', 'Thưởng gắn bó ca đêm', '2026-08-01', 10, 'PRODUCTION_WORKER', TRUE,
    'system', 'system'
) ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    updated_by_actor = VALUES(updated_by_actor);
