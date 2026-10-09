ALTER TABLE hr_excel_import_batches
    DROP CONSTRAINT ck_hr_import_type;

ALTER TABLE hr_excel_import_batches
    ADD CONSTRAINT ck_hr_import_type
        CHECK (import_type IN ('BASELINE', 'INCREASE', 'DECREASE', 'ROSTER', 'SALARY_RAISE'));

ALTER TABLE hr_employee_employment
    ADD COLUMN salary_grade VARCHAR(40) NULL AFTER allowance;

ALTER TABLE hr_employee_employment
    ADD COLUMN salary_scale_code VARCHAR(64) NULL AFTER salary_grade;

ALTER TABLE hr_employee_employment
    ADD COLUMN salary_review_cycle_months INT NULL AFTER salary_scale_code;

ALTER TABLE hr_employee_employment
    ADD COLUMN last_salary_raise_date DATE NULL AFTER salary_review_cycle_months;

ALTER TABLE hr_employee_employment
    ADD COLUMN next_salary_review_date DATE NULL AFTER last_salary_raise_date;

ALTER TABLE hr_employee_employment
    ADD CONSTRAINT ck_hr_employment_salary_review_cycle
        CHECK (salary_review_cycle_months IS NULL OR salary_review_cycle_months > 0);

CREATE TABLE hr_employee_salary_changes (
    id VARCHAR(36) NOT NULL,
    employee_id VARCHAR(36) NOT NULL,
    import_batch_id VARCHAR(36) NULL,
    source_row_number INT NULL,
    effective_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL,
    old_base_salary DECIMAL(15, 2) NOT NULL,
    new_base_salary DECIMAL(15, 2) NOT NULL,
    old_allowance DECIMAL(15, 2) NOT NULL,
    new_allowance DECIMAL(15, 2) NOT NULL,
    old_grade VARCHAR(40) NULL,
    new_grade VARCHAR(40) NOT NULL,
    salary_scale_code VARCHAR(64) NULL,
    review_cycle_months INT NOT NULL,
    next_review_date DATE NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    applied_at DATETIME(6) NULL,
    applied_by_actor VARCHAR(320) NULL,
    rolled_back_at DATETIME(6) NULL,
    rolled_back_by_actor VARCHAR(320) NULL,
    rollback_reason VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_employee_salary_change PRIMARY KEY (id),
    CONSTRAINT fk_hr_salary_change_employee FOREIGN KEY (employee_id)
        REFERENCES hr_employees (id) ON DELETE RESTRICT,
    CONSTRAINT fk_hr_salary_change_import FOREIGN KEY (import_batch_id)
        REFERENCES hr_excel_import_batches (id) ON DELETE RESTRICT,
    CONSTRAINT uk_hr_salary_change_idempotency UNIQUE (idempotency_key),
    CONSTRAINT uk_hr_salary_change_import_row UNIQUE (import_batch_id, source_row_number),
    CONSTRAINT ck_hr_salary_change_status CHECK (status IN ('SCHEDULED', 'APPLIED', 'ROLLED_BACK')),
    CONSTRAINT ck_hr_salary_change_source_row CHECK (source_row_number IS NULL OR source_row_number > 0),
    CONSTRAINT ck_hr_salary_change_amounts CHECK (
        old_base_salary >= 0 AND new_base_salary >= 0
        AND old_allowance >= 0 AND new_allowance >= 0
        AND new_base_salary + new_allowance > old_base_salary + old_allowance
    ),
    CONSTRAINT ck_hr_salary_change_cycle CHECK (review_cycle_months > 0),
    CONSTRAINT ck_hr_salary_change_lifecycle CHECK (
        (status = 'SCHEDULED' AND applied_at IS NULL AND rolled_back_at IS NULL)
        OR (status = 'APPLIED' AND applied_at IS NOT NULL AND applied_by_actor IS NOT NULL AND rolled_back_at IS NULL)
        OR (status = 'ROLLED_BACK' AND rolled_back_at IS NOT NULL AND rolled_back_by_actor IS NOT NULL AND rollback_reason IS NOT NULL)
    ),
    INDEX idx_hr_salary_change_employee_effective (employee_id, effective_date, status),
    INDEX idx_hr_salary_change_import (import_batch_id),
    INDEX idx_hr_salary_change_scheduled (status, effective_date)
);
