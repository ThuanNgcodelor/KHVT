-- BookingBase HR Phase 7.
-- Adds probation candidate and Word contract history tables only.
-- These objects deliberately keep probation candidates outside official HR roster.

CREATE TABLE hr_probation_job_templates (
    id VARCHAR(36) NOT NULL,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000) NULL,
    department_id VARCHAR(36) NULL,
    position_id VARCHAR(36) NULL,
    working_condition_id VARCHAR(36) NULL,
    probation_contract_type VARCHAR(100) NULL,
    job_description VARCHAR(2000) NULL,
    base_salary DECIMAL(15, 2) NULL,
    salary_note VARCHAR(255) NULL,
    department_rule_note VARCHAR(500) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_probation_job_templates PRIMARY KEY (id),
    CONSTRAINT uk_hr_probation_job_template_code UNIQUE (code),
    CONSTRAINT uk_hr_probation_job_template_name UNIQUE (name),
    CONSTRAINT fk_hr_probation_template_department FOREIGN KEY (department_id) REFERENCES hr_departments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_hr_probation_template_position FOREIGN KEY (position_id) REFERENCES hr_positions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_hr_probation_template_condition FOREIGN KEY (working_condition_id) REFERENCES hr_working_conditions (id) ON DELETE RESTRICT,
    CONSTRAINT ck_hr_probation_template_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_hr_probation_template_sort_order CHECK (sort_order >= 0),
    CONSTRAINT ck_hr_probation_template_salary CHECK (base_salary IS NULL OR base_salary >= 0),
    CONSTRAINT ck_hr_probation_template_row_version CHECK (row_version >= 0)
);

CREATE TABLE hr_probation_candidates (
    id VARCHAR(36) NOT NULL,
    candidate_code VARCHAR(32) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    candidate_title VARCHAR(16) NULL,
    gender VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    date_of_birth DATE NULL,
    birth_place VARCHAR(500) NULL,
    nationality VARCHAR(100) NOT NULL DEFAULT 'Việt Nam',
    citizen_id VARCHAR(32) NULL,
    citizen_id_issued_date DATE NULL,
    citizen_id_issued_place VARCHAR(255) NULL,
    permanent_address VARCHAR(1000) NULL,
    phone VARCHAR(32) NULL,
    email VARCHAR(320) NULL,
    department_id VARCHAR(36) NULL,
    position_id VARCHAR(36) NULL,
    working_condition_id VARCHAR(36) NULL,
    job_template_id VARCHAR(36) NULL,
    probation_contract_type VARCHAR(100) NULL,
    probation_start_date DATE NULL,
    probation_end_date DATE NULL,
    base_salary DECIMAL(15, 2) NULL,
    salary_note VARCHAR(255) NULL,
    job_description VARCHAR(2000) NULL,
    department_rule_note VARCHAR(500) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    status_reason VARCHAR(1000) NULL,
    converted_employee_id VARCHAR(36) NULL,
    converted_at DATETIME(6) NULL,
    converted_by_actor VARCHAR(320) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_probation_candidates PRIMARY KEY (id),
    CONSTRAINT uk_hr_probation_candidate_code UNIQUE (candidate_code),
    CONSTRAINT uk_hr_probation_candidate_converted_employee UNIQUE (converted_employee_id),
    CONSTRAINT fk_hr_probation_candidate_department FOREIGN KEY (department_id) REFERENCES hr_departments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_hr_probation_candidate_position FOREIGN KEY (position_id) REFERENCES hr_positions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_hr_probation_candidate_condition FOREIGN KEY (working_condition_id) REFERENCES hr_working_conditions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_hr_probation_candidate_template FOREIGN KEY (job_template_id) REFERENCES hr_probation_job_templates (id) ON DELETE SET NULL,
    CONSTRAINT fk_hr_probation_candidate_employee FOREIGN KEY (converted_employee_id) REFERENCES hr_employees (id) ON DELETE RESTRICT,
    CONSTRAINT ck_hr_probation_candidate_gender CHECK (gender IN ('MALE', 'FEMALE', 'OTHER', 'UNKNOWN')),
    CONSTRAINT ck_hr_probation_candidate_status CHECK (status IN ('DRAFT', 'CONTRACT_CREATED', 'IN_PROBATION', 'PASSED', 'FAILED', 'CONVERTED', 'CANCELLED')),
    CONSTRAINT ck_hr_probation_candidate_dates CHECK (probation_end_date IS NULL OR probation_start_date IS NULL OR probation_end_date >= probation_start_date),
    CONSTRAINT ck_hr_probation_candidate_salary CHECK (base_salary IS NULL OR base_salary >= 0),
    CONSTRAINT ck_hr_probation_candidate_converted CHECK (
        (status = 'CONVERTED' AND converted_employee_id IS NOT NULL AND converted_at IS NOT NULL AND converted_by_actor IS NOT NULL)
        OR (status <> 'CONVERTED' AND converted_employee_id IS NULL AND converted_at IS NULL AND converted_by_actor IS NULL)
    ),
    CONSTRAINT ck_hr_probation_candidate_row_version CHECK (row_version >= 0)
);

CREATE TABLE hr_probation_contracts (
    id VARCHAR(36) NOT NULL,
    candidate_id VARCHAR(36) NOT NULL,
    contract_no VARCHAR(32) NOT NULL,
    contract_year SMALLINT NOT NULL,
    template_file_name VARCHAR(255) NOT NULL,
    template_sha256 CHAR(64) NOT NULL,
    generated_file_name VARCHAR(255) NOT NULL,
    generated_file_sha256 CHAR(64) NOT NULL,
    generated_docx MEDIUMBLOB NOT NULL,
    snapshot_payload JSON NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'GENERATED',
    generated_at DATETIME(6) NOT NULL,
    generated_by_actor VARCHAR(320) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_probation_contracts PRIMARY KEY (id),
    CONSTRAINT uk_hr_probation_contract_no_year UNIQUE (contract_no, contract_year),
    CONSTRAINT fk_hr_probation_contract_candidate FOREIGN KEY (candidate_id) REFERENCES hr_probation_candidates (id) ON DELETE RESTRICT,
    CONSTRAINT ck_hr_probation_contract_year CHECK (contract_year BETWEEN 1900 AND 2200),
    CONSTRAINT ck_hr_probation_contract_status CHECK (status IN ('GENERATED', 'VOIDED')),
    CONSTRAINT ck_hr_probation_contract_row_version CHECK (row_version >= 0)
);

CREATE INDEX idx_hr_probation_template_status_sort
    ON hr_probation_job_templates (status, sort_order, name);

CREATE INDEX idx_hr_probation_candidate_status_end
    ON hr_probation_candidates (status, probation_end_date);

CREATE INDEX idx_hr_probation_candidate_name
    ON hr_probation_candidates (full_name);

CREATE INDEX idx_hr_probation_candidate_department
    ON hr_probation_candidates (department_id, status);

CREATE INDEX idx_hr_probation_contract_candidate
    ON hr_probation_contracts (candidate_id, generated_at);
