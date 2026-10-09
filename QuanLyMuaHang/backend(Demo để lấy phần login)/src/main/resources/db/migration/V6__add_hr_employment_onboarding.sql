-- Formal employment onboarding metadata for office and general-labor flows.
-- Existing employees remain policy version 1 and are not contract-gated.

ALTER TABLE hr_employees
    ADD COLUMN workforce_group VARCHAR(32) NOT NULL DEFAULT 'LEGACY_UNKNOWN';

ALTER TABLE hr_employees
    ADD COLUMN onboarding_source VARCHAR(32) NOT NULL DEFAULT 'LEGACY';

ALTER TABLE hr_employees
    ADD COLUMN onboarding_policy_version SMALLINT NOT NULL DEFAULT 1;

ALTER TABLE hr_employees
    ADD CONSTRAINT ck_hr_employee_workforce_group
        CHECK (workforce_group IN ('OFFICE', 'GENERAL_LABOR', 'LEGACY_UNKNOWN'));

ALTER TABLE hr_employees
    ADD CONSTRAINT ck_hr_employee_onboarding_source
        CHECK (onboarding_source IN ('PROBATION', 'DIRECT_GENERAL_LABOR', 'MANUAL', 'IMPORT', 'LEGACY'));

ALTER TABLE hr_employees
    ADD CONSTRAINT ck_hr_employee_onboarding_policy
        CHECK (onboarding_policy_version >= 1);

CREATE INDEX idx_hr_employees_workforce_status
    ON hr_employees (workforce_group, employment_status, employee_code);

CREATE TABLE hr_employment_contracts (
    id VARCHAR(36) NOT NULL,
    employee_id VARCHAR(36) NOT NULL,
    source_probation_candidate_id VARCHAR(36) NULL,
    contract_type VARCHAR(32) NOT NULL,
    contract_number VARCHAR(100) NOT NULL,
    sign_date DATE NOT NULL,
    effective_from DATE NOT NULL,
    effective_until DATE NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'READY',
    idempotency_key VARCHAR(100) NOT NULL,
    activated_at DATETIME(6) NULL,
    activated_by_actor VARCHAR(320) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_employment_contracts PRIMARY KEY (id),
    CONSTRAINT uk_hr_employment_contract_number UNIQUE (contract_number),
    CONSTRAINT uk_hr_employment_contract_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_hr_employment_contract_employee
        FOREIGN KEY (employee_id) REFERENCES hr_employees (id) ON DELETE CASCADE,
    CONSTRAINT fk_hr_employment_contract_probation
        FOREIGN KEY (source_probation_candidate_id) REFERENCES hr_probation_candidates (id) ON DELETE RESTRICT,
    CONSTRAINT ck_hr_employment_contract_type
        CHECK (contract_type IN ('FIXED_TERM_12_MONTHS', 'INDEFINITE')),
    CONSTRAINT ck_hr_employment_contract_status
        CHECK (status IN ('READY', 'EFFECTIVE', 'VOIDED')),
    CONSTRAINT ck_hr_employment_contract_dates CHECK (
        (contract_type = 'FIXED_TERM_12_MONTHS' AND effective_until IS NOT NULL AND effective_until > effective_from)
        OR (contract_type = 'INDEFINITE' AND effective_until IS NULL)
    ),
    CONSTRAINT ck_hr_employment_contract_activation CHECK (
        (status = 'EFFECTIVE' AND activated_at IS NOT NULL AND activated_by_actor IS NOT NULL)
        OR (status <> 'EFFECTIVE')
    ),
    CONSTRAINT ck_hr_employment_contract_row_version CHECK (row_version >= 0)
);

CREATE INDEX idx_hr_employment_contract_employee_status
    ON hr_employment_contracts (employee_id, status, effective_from);

CREATE INDEX idx_hr_employment_contract_probation
    ON hr_employment_contracts (source_probation_candidate_id);
