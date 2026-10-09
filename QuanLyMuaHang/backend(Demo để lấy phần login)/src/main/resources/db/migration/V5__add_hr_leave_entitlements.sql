ALTER TABLE hr_working_conditions
    ADD COLUMN annual_leave_days_base DECIMAL(6, 2) NOT NULL DEFAULT 12.00;

ALTER TABLE hr_working_conditions
    ADD CONSTRAINT ck_hr_working_condition_annual_leave_base
        CHECK (annual_leave_days_base >= 0);

CREATE TABLE hr_employee_leave_entitlements (
    id VARCHAR(36) NOT NULL,
    employee_id VARCHAR(36) NOT NULL,
    leave_year SMALLINT NOT NULL,
    manual_override_days DECIMAL(6, 2) NULL,
    note VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_leave_entitlements PRIMARY KEY (id),
    CONSTRAINT uk_hr_leave_entitlement_employee_year UNIQUE (employee_id, leave_year),
    CONSTRAINT fk_hr_leave_entitlement_employee FOREIGN KEY (employee_id) REFERENCES hr_employees (id) ON DELETE CASCADE,
    CONSTRAINT ck_hr_leave_entitlement_year CHECK (leave_year >= 2000),
    CONSTRAINT ck_hr_leave_entitlement_manual_override CHECK (manual_override_days IS NULL OR manual_override_days >= 0),
    CONSTRAINT ck_hr_leave_entitlement_row_version CHECK (row_version >= 0)
);

CREATE INDEX idx_hr_leave_entitlement_year ON hr_employee_leave_entitlements (leave_year);
