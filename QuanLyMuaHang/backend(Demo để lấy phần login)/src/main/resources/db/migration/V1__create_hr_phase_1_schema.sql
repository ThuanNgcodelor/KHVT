-- BookingBase HR Phase 1.
-- This forward-only migration creates HR-owned objects only. It must not alter
-- legacy BookingBase tables and deliberately has no foreign key to `users`.

CREATE TABLE hr_excel_template_versions (
    id VARCHAR(36) NOT NULL,
    template_key VARCHAR(64) NOT NULL,
    version_code VARCHAR(32) NOT NULL,
    schema_version SMALLINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_sha256 CHAR(64) NOT NULL,
    sheet_contract JSON NOT NULL,
    contains_pii BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    effective_from DATE NULL,
    effective_until DATE NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_excel_template_versions PRIMARY KEY (id),
    CONSTRAINT uk_hr_template_key_version UNIQUE (template_key, version_code),
    CONSTRAINT uk_hr_template_file_sha UNIQUE (file_sha256),
    CONSTRAINT ck_hr_template_schema_version CHECK (schema_version > 0),
    CONSTRAINT ck_hr_template_status CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    CONSTRAINT ck_hr_template_dates CHECK (effective_until IS NULL OR effective_from IS NULL OR effective_until >= effective_from),
    CONSTRAINT ck_hr_template_row_version CHECK (row_version >= 0)
);

CREATE TABLE hr_excel_import_batches (
    id VARCHAR(36) NOT NULL,
    template_version_id VARCHAR(36) NULL,
    import_type VARCHAR(16) NOT NULL,
    source_file_name VARCHAR(255) NOT NULL,
    file_sha256 CHAR(64) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_key VARCHAR(500) NULL,
    source_sheet_name VARCHAR(100) NOT NULL,
    attempt_number INT NOT NULL DEFAULT 1,
    source_period_year SMALLINT NULL,
    source_period_month TINYINT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'UPLOADED',
    total_rows INT NOT NULL DEFAULT 0,
    valid_rows INT NOT NULL DEFAULT 0,
    warning_rows INT NOT NULL DEFAULT 0,
    invalid_rows INT NOT NULL DEFAULT 0,
    imported_rows INT NOT NULL DEFAULT 0,
    issue_summary JSON NULL,
    confirmation_key VARCHAR(100) NULL,
    parsed_at DATETIME(6) NULL,
    validated_at DATETIME(6) NULL,
    confirmed_at DATETIME(6) NULL,
    confirmed_by_actor VARCHAR(320) NULL,
    rolled_back_at DATETIME(6) NULL,
    rolled_back_by_actor VARCHAR(320) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_excel_import_batches PRIMARY KEY (id),
    CONSTRAINT uk_hr_import_file_attempt UNIQUE (file_sha256, source_sheet_name, import_type, attempt_number),
    CONSTRAINT uk_hr_import_confirmation_key UNIQUE (confirmation_key),
    CONSTRAINT fk_hr_import_template FOREIGN KEY (template_version_id) REFERENCES hr_excel_template_versions (id) ON DELETE RESTRICT,
    CONSTRAINT ck_hr_import_type CHECK (import_type IN ('BASELINE', 'INCREASE', 'DECREASE', 'ROSTER')),
    CONSTRAINT ck_hr_import_status CHECK (status IN ('UPLOADED', 'PARSED', 'VALIDATED', 'CONFIRMED', 'FAILED', 'ROLLED_BACK')),
    CONSTRAINT ck_hr_import_attempt CHECK (attempt_number > 0),
    CONSTRAINT ck_hr_import_period_year CHECK (source_period_year IS NULL OR source_period_year BETWEEN 1900 AND 2200),
    CONSTRAINT ck_hr_import_period_month CHECK (source_period_month IS NULL OR source_period_month BETWEEN 1 AND 12),
    CONSTRAINT ck_hr_import_file_size CHECK (file_size > 0),
    CONSTRAINT ck_hr_import_counts CHECK (total_rows >= 0 AND valid_rows >= 0 AND warning_rows >= 0 AND invalid_rows >= 0 AND imported_rows >= 0),
    CONSTRAINT ck_hr_import_count_totals CHECK (valid_rows + warning_rows + invalid_rows <= total_rows AND imported_rows <= valid_rows + warning_rows),
    CONSTRAINT ck_hr_import_lifecycle CHECK (
        status = 'FAILED'
        OR (status = 'UPLOADED' AND parsed_at IS NULL AND validated_at IS NULL AND confirmed_at IS NULL AND rolled_back_at IS NULL)
        OR (status = 'PARSED' AND parsed_at IS NOT NULL AND validated_at IS NULL AND confirmed_at IS NULL AND rolled_back_at IS NULL)
        OR (status = 'VALIDATED' AND parsed_at IS NOT NULL AND validated_at IS NOT NULL AND confirmed_at IS NULL AND rolled_back_at IS NULL)
        OR (status = 'CONFIRMED' AND parsed_at IS NOT NULL AND validated_at IS NOT NULL AND confirmed_at IS NOT NULL AND confirmed_by_actor IS NOT NULL AND rolled_back_at IS NULL)
        OR (status = 'ROLLED_BACK' AND confirmed_at IS NOT NULL AND confirmed_by_actor IS NOT NULL AND rolled_back_at IS NOT NULL AND rolled_back_by_actor IS NOT NULL)
    ),
    CONSTRAINT ck_hr_import_row_version CHECK (row_version >= 0)
);

CREATE TABLE hr_departments (
    id VARCHAR(36) NOT NULL,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000) NULL,
    parent_id VARCHAR(36) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_departments PRIMARY KEY (id),
    CONSTRAINT uk_hr_department_code UNIQUE (code),
    CONSTRAINT uk_hr_department_name UNIQUE (name),
    CONSTRAINT fk_hr_department_parent FOREIGN KEY (parent_id) REFERENCES hr_departments (id) ON DELETE RESTRICT,
    CONSTRAINT ck_hr_department_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_hr_department_sort_order CHECK (sort_order >= 0),
    CONSTRAINT ck_hr_department_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id),
    CONSTRAINT ck_hr_department_row_version CHECK (row_version >= 0)
);

CREATE TABLE hr_positions (
    id VARCHAR(36) NOT NULL,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_positions PRIMARY KEY (id),
    CONSTRAINT uk_hr_position_code UNIQUE (code),
    CONSTRAINT uk_hr_position_name UNIQUE (name),
    CONSTRAINT ck_hr_position_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_hr_position_sort_order CHECK (sort_order >= 0),
    CONSTRAINT ck_hr_position_row_version CHECK (row_version >= 0)
);

CREATE TABLE hr_working_conditions (
    id VARCHAR(36) NOT NULL,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(1000) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_working_conditions PRIMARY KEY (id),
    CONSTRAINT uk_hr_working_condition_code UNIQUE (code),
    CONSTRAINT uk_hr_working_condition_name UNIQUE (name),
    CONSTRAINT ck_hr_working_condition_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_hr_working_condition_sort_order CHECK (sort_order >= 0),
    CONSTRAINT ck_hr_working_condition_version CHECK (row_version >= 0)
);

CREATE TABLE hr_employees (
    id VARCHAR(36) NOT NULL,
    employee_code VARCHAR(32) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    gender VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    date_of_birth DATE NULL,
    ethnicity VARCHAR(100) NULL,
    religion VARCHAR(100) NULL,
    birth_place_original VARCHAR(500) NULL,
    birth_place_current VARCHAR(500) NULL,
    education_level VARCHAR(255) NULL,
    major VARCHAR(255) NULL,
    employment_status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    status_effective_date DATE NULL,
    source_import_batch_id VARCHAR(36) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_employees PRIMARY KEY (id),
    CONSTRAINT uk_hr_employee_code UNIQUE (employee_code),
    CONSTRAINT fk_hr_employee_source_batch FOREIGN KEY (source_import_batch_id) REFERENCES hr_excel_import_batches (id) ON DELETE RESTRICT,
    CONSTRAINT ck_hr_employee_gender CHECK (gender IN ('MALE', 'FEMALE', 'OTHER', 'UNKNOWN')),
    CONSTRAINT ck_hr_employee_status CHECK (employment_status IN ('DRAFT', 'ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_hr_employee_row_version CHECK (row_version >= 0)
);

CREATE TABLE hr_employee_employment (
    employee_id VARCHAR(36) NOT NULL,
    department_id VARCHAR(36) NULL,
    position_id VARCHAR(36) NULL,
    working_condition_id VARCHAR(36) NULL,
    hire_date DATE NULL,
    leave_accrual_start_date DATE NULL,
    termination_date DATE NULL,
    contract_type_label VARCHAR(100) NULL,
    contract_number VARCHAR(100) NULL,
    base_salary DECIMAL(15, 2) NULL,
    allowance DECIMAL(15, 2) NULL,
    job_description VARCHAR(2000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_employee_employment PRIMARY KEY (employee_id),
    CONSTRAINT fk_hr_employment_employee FOREIGN KEY (employee_id) REFERENCES hr_employees (id) ON DELETE CASCADE,
    CONSTRAINT fk_hr_employment_department FOREIGN KEY (department_id) REFERENCES hr_departments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_hr_employment_position FOREIGN KEY (position_id) REFERENCES hr_positions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_hr_employment_condition FOREIGN KEY (working_condition_id) REFERENCES hr_working_conditions (id) ON DELETE RESTRICT,
    CONSTRAINT ck_hr_employment_dates CHECK (termination_date IS NULL OR hire_date IS NULL OR termination_date >= hire_date),
    CONSTRAINT ck_hr_employment_base_salary CHECK (base_salary IS NULL OR base_salary >= 0),
    CONSTRAINT ck_hr_employment_allowance CHECK (allowance IS NULL OR allowance >= 0),
    CONSTRAINT ck_hr_employment_row_version CHECK (row_version >= 0)
);

CREATE TABLE hr_employee_identity (
    employee_id VARCHAR(36) NOT NULL,
    legacy_identity_number VARCHAR(32) NULL,
    citizen_identity_number VARCHAR(32) NULL,
    issued_date DATE NULL,
    issued_place VARCHAR(255) NULL,
    verification_status VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_employee_identity PRIMARY KEY (employee_id),
    CONSTRAINT fk_hr_identity_employee FOREIGN KEY (employee_id) REFERENCES hr_employees (id) ON DELETE CASCADE,
    CONSTRAINT ck_hr_identity_verification CHECK (verification_status IN ('UNVERIFIED', 'VERIFIED', 'NEEDS_REVIEW')),
    CONSTRAINT ck_hr_identity_row_version CHECK (row_version >= 0)
);

CREATE TABLE hr_employee_insurance (
    employee_id VARCHAR(36) NOT NULL,
    social_insurance_number VARCHAR(32) NULL,
    health_insurance_number VARCHAR(32) NULL,
    valid_from DATE NULL,
    valid_until DATE NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_employee_insurance PRIMARY KEY (employee_id),
    CONSTRAINT fk_hr_insurance_employee FOREIGN KEY (employee_id) REFERENCES hr_employees (id) ON DELETE CASCADE,
    CONSTRAINT ck_hr_insurance_status CHECK (status IN ('UNKNOWN', 'ACTIVE', 'INACTIVE', 'NEEDS_REVIEW')),
    CONSTRAINT ck_hr_insurance_dates CHECK (valid_until IS NULL OR valid_from IS NULL OR valid_until >= valid_from),
    CONSTRAINT ck_hr_insurance_row_version CHECK (row_version >= 0)
);

CREATE TABLE hr_employee_contacts (
    employee_id VARCHAR(36) NOT NULL,
    permanent_address VARCHAR(1000) NULL,
    current_address VARCHAR(1000) NULL,
    phone VARCHAR(32) NULL,
    work_email VARCHAR(320) NULL,
    personal_email VARCHAR(320) NULL,
    emergency_contact_name VARCHAR(255) NULL,
    emergency_contact_phone VARCHAR(32) NULL,
    emergency_contact_relation VARCHAR(100) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_employee_contacts PRIMARY KEY (employee_id),
    CONSTRAINT fk_hr_contacts_employee FOREIGN KEY (employee_id) REFERENCES hr_employees (id) ON DELETE CASCADE,
    CONSTRAINT ck_hr_contacts_row_version CHECK (row_version >= 0)
);

CREATE TABLE hr_employee_movements (
    id VARCHAR(36) NOT NULL,
    employee_id VARCHAR(36) NOT NULL,
    movement_type VARCHAR(40) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    effective_date DATE NOT NULL,
    from_department_id VARCHAR(36) NULL,
    to_department_id VARCHAR(36) NULL,
    from_position_id VARCHAR(36) NULL,
    to_position_id VARCHAR(36) NULL,
    from_working_condition_id VARCHAR(36) NULL,
    to_working_condition_id VARCHAR(36) NULL,
    from_employee_status VARCHAR(16) NULL,
    to_employee_status VARCHAR(16) NULL,
    reason VARCHAR(1000) NULL,
    decision_number VARCHAR(100) NULL,
    decision_date DATE NULL,
    source_kind VARCHAR(20) NOT NULL,
    import_batch_id VARCHAR(36) NULL,
    idempotency_key VARCHAR(100) NULL,
    confirmed_at DATETIME(6) NULL,
    confirmed_by_actor VARCHAR(320) NULL,
    cancelled_at DATETIME(6) NULL,
    cancelled_by_actor VARCHAR(320) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_employee_movements PRIMARY KEY (id),
    CONSTRAINT uk_hr_movement_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_hr_movement_employee FOREIGN KEY (employee_id) REFERENCES hr_employees (id) ON DELETE RESTRICT,
    CONSTRAINT fk_hr_movement_from_department FOREIGN KEY (from_department_id) REFERENCES hr_departments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_hr_movement_to_department FOREIGN KEY (to_department_id) REFERENCES hr_departments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_hr_movement_from_position FOREIGN KEY (from_position_id) REFERENCES hr_positions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_hr_movement_to_position FOREIGN KEY (to_position_id) REFERENCES hr_positions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_hr_movement_from_condition FOREIGN KEY (from_working_condition_id) REFERENCES hr_working_conditions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_hr_movement_to_condition FOREIGN KEY (to_working_condition_id) REFERENCES hr_working_conditions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_hr_movement_import_batch FOREIGN KEY (import_batch_id) REFERENCES hr_excel_import_batches (id) ON DELETE RESTRICT,
    CONSTRAINT ck_hr_movement_type CHECK (movement_type IN ('INITIAL_LOAD', 'INCREASE', 'DECREASE', 'TRANSFER', 'POSITION_CHANGE', 'WORKING_CONDITION_CHANGE', 'ADJUSTMENT', 'REHIRE')),
    CONSTRAINT ck_hr_movement_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'CANCELLED')),
    CONSTRAINT ck_hr_movement_from_status CHECK (from_employee_status IS NULL OR from_employee_status IN ('DRAFT', 'ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_hr_movement_to_status CHECK (to_employee_status IS NULL OR to_employee_status IN ('DRAFT', 'ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_hr_movement_source CHECK (source_kind IN ('BASELINE_IMPORT', 'EXCEL_IMPORT', 'MANUAL', 'SYSTEM')),
    CONSTRAINT ck_hr_movement_lifecycle CHECK (
        (status = 'DRAFT' AND confirmed_at IS NULL AND confirmed_by_actor IS NULL AND cancelled_at IS NULL AND cancelled_by_actor IS NULL)
        OR (status = 'CONFIRMED' AND confirmed_at IS NOT NULL AND confirmed_by_actor IS NOT NULL AND cancelled_at IS NULL AND cancelled_by_actor IS NULL)
        OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND cancelled_by_actor IS NOT NULL)
    ),
    CONSTRAINT ck_hr_movement_activation CHECK (
        movement_type NOT IN ('INITIAL_LOAD', 'INCREASE', 'REHIRE') OR to_employee_status = 'ACTIVE'
    ),
    CONSTRAINT ck_hr_movement_decrease CHECK (
        movement_type <> 'DECREASE' OR (from_employee_status = 'ACTIVE' AND to_employee_status = 'INACTIVE')
    ),
    CONSTRAINT ck_hr_movement_row_version CHECK (row_version >= 0)
);

CREATE TABLE hr_monthly_rosters (
    id VARCHAR(36) NOT NULL,
    period_start DATE NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    source_roster_id VARCHAR(36) NULL,
    source_import_batch_id VARCHAR(36) NULL,
    snapshot_schema_version SMALLINT NOT NULL DEFAULT 1,
    item_count INT NOT NULL DEFAULT 0,
    roster_checksum CHAR(64) NULL,
    opened_at DATETIME(6) NULL,
    opened_by_actor VARCHAR(320) NULL,
    closed_at DATETIME(6) NULL,
    closed_by_actor VARCHAR(320) NULL,
    exported_at DATETIME(6) NULL,
    exported_by_actor VARCHAR(320) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_monthly_rosters PRIMARY KEY (id),
    CONSTRAINT uk_hr_roster_period UNIQUE (period_start),
    CONSTRAINT fk_hr_roster_source FOREIGN KEY (source_roster_id) REFERENCES hr_monthly_rosters (id) ON DELETE RESTRICT,
    CONSTRAINT fk_hr_roster_import_batch FOREIGN KEY (source_import_batch_id) REFERENCES hr_excel_import_batches (id) ON DELETE RESTRICT,
    CONSTRAINT ck_hr_roster_status CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED', 'EXPORTED')),
    CONSTRAINT ck_hr_roster_period_start CHECK (EXTRACT(DAY FROM period_start) = 1),
    CONSTRAINT ck_hr_roster_schema_version CHECK (snapshot_schema_version > 0),
    CONSTRAINT ck_hr_roster_item_count CHECK (item_count >= 0),
    CONSTRAINT ck_hr_roster_lifecycle CHECK (
        (status = 'DRAFT' AND opened_at IS NULL AND closed_at IS NULL AND exported_at IS NULL)
        OR (status = 'OPEN' AND opened_at IS NOT NULL AND opened_by_actor IS NOT NULL AND closed_at IS NULL AND exported_at IS NULL)
        OR (status = 'CLOSED' AND opened_at IS NOT NULL AND opened_by_actor IS NOT NULL AND closed_at IS NOT NULL AND closed_by_actor IS NOT NULL AND exported_at IS NULL)
        OR (status = 'EXPORTED' AND closed_at IS NOT NULL AND closed_by_actor IS NOT NULL AND exported_at IS NOT NULL AND exported_by_actor IS NOT NULL)
    ),
    CONSTRAINT ck_hr_roster_not_own_source CHECK (source_roster_id IS NULL OR source_roster_id <> id),
    CONSTRAINT ck_hr_roster_row_version CHECK (row_version >= 0)
);

CREATE TABLE hr_monthly_roster_items (
    id VARCHAR(36) NOT NULL,
    roster_id VARCHAR(36) NOT NULL,
    employee_id VARCHAR(36) NOT NULL,
    display_order INT NOT NULL,
    department_display_order INT NULL,
    employee_code VARCHAR(32) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    department_code VARCHAR(32) NULL,
    department_name VARCHAR(255) NULL,
    position_code VARCHAR(32) NULL,
    position_name VARCHAR(255) NULL,
    working_condition_code VARCHAR(32) NULL,
    working_condition_name VARCHAR(255) NULL,
    employment_status VARCHAR(16) NOT NULL,
    hire_date DATE NULL,
    termination_date DATE NULL,
    leave_days DECIMAL(6, 2) NULL,
    inclusion_reason VARCHAR(20) NOT NULL,
    source_movement_id VARCHAR(36) NULL,
    snapshot_schema_version SMALLINT NOT NULL DEFAULT 1,
    snapshot_payload JSON NOT NULL,
    payload_sha256 CHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    CONSTRAINT pk_hr_monthly_roster_items PRIMARY KEY (id),
    CONSTRAINT uk_hr_roster_item_employee UNIQUE (roster_id, employee_id),
    CONSTRAINT uk_hr_roster_item_code UNIQUE (roster_id, employee_code),
    CONSTRAINT uk_hr_roster_item_order UNIQUE (roster_id, display_order),
    CONSTRAINT fk_hr_roster_item_roster FOREIGN KEY (roster_id) REFERENCES hr_monthly_rosters (id) ON DELETE RESTRICT,
    CONSTRAINT fk_hr_roster_item_employee FOREIGN KEY (employee_id) REFERENCES hr_employees (id) ON DELETE RESTRICT,
    CONSTRAINT fk_hr_roster_item_movement FOREIGN KEY (source_movement_id) REFERENCES hr_employee_movements (id) ON DELETE RESTRICT,
    CONSTRAINT ck_hr_roster_item_display_order CHECK (display_order > 0),
    CONSTRAINT ck_hr_roster_item_department_order CHECK (department_display_order IS NULL OR department_display_order > 0),
    CONSTRAINT ck_hr_roster_item_status CHECK (employment_status IN ('DRAFT', 'ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_hr_roster_item_leave CHECK (leave_days IS NULL OR leave_days >= 0),
    CONSTRAINT ck_hr_roster_item_reason CHECK (inclusion_reason IN ('BASELINE', 'CARRIED_FORWARD', 'INCREASE', 'REHIRE', 'ADJUSTMENT')),
    CONSTRAINT ck_hr_roster_item_schema_version CHECK (snapshot_schema_version > 0)
);

CREATE TABLE hr_excel_import_rows (
    id VARCHAR(36) NOT NULL,
    batch_id VARCHAR(36) NOT NULL,
    sheet_name VARCHAR(100) NOT NULL,
    source_row_number INT NOT NULL,
    employee_code_hint VARCHAR(32) NULL,
    row_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    raw_payload JSON NOT NULL,
    normalized_payload JSON NULL,
    payload_sha256 CHAR(64) NOT NULL,
    issue_codes JSON NULL,
    employee_id VARCHAR(36) NULL,
    movement_id VARCHAR(36) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_hr_excel_import_rows PRIMARY KEY (id),
    CONSTRAINT uk_hr_import_row UNIQUE (batch_id, sheet_name, source_row_number),
    CONSTRAINT fk_hr_import_row_batch FOREIGN KEY (batch_id) REFERENCES hr_excel_import_batches (id) ON DELETE RESTRICT,
    CONSTRAINT fk_hr_import_row_employee FOREIGN KEY (employee_id) REFERENCES hr_employees (id) ON DELETE SET NULL,
    CONSTRAINT fk_hr_import_row_movement FOREIGN KEY (movement_id) REFERENCES hr_employee_movements (id) ON DELETE SET NULL,
    CONSTRAINT ck_hr_import_row_number CHECK (source_row_number > 0),
    CONSTRAINT ck_hr_import_row_status CHECK (row_status IN ('PENDING', 'VALID', 'WARNING', 'INVALID', 'IMPORTED', 'SKIPPED', 'ROLLED_BACK'))
);

CREATE TABLE hr_audit_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_subject VARCHAR(320) NOT NULL,
    actor_display_name VARCHAR(255) NULL,
    actor_role VARCHAR(32) NOT NULL,
    action VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id VARCHAR(36) NULL,
    correlation_id VARCHAR(64) NULL,
    changed_fields JSON NULL,
    sanitized_metadata JSON NULL,
    occurred_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT pk_hr_audit_events PRIMARY KEY (id)
);

CREATE INDEX idx_hr_import_status_created ON hr_excel_import_batches (status, created_at);
CREATE INDEX idx_hr_import_file_sha ON hr_excel_import_batches (file_sha256);
CREATE INDEX idx_hr_departments_parent ON hr_departments (parent_id);
CREATE INDEX idx_hr_employees_status_code ON hr_employees (employment_status, employee_code);
CREATE INDEX idx_hr_employees_full_name ON hr_employees (full_name);
CREATE INDEX idx_hr_employees_source_batch ON hr_employees (source_import_batch_id);
CREATE INDEX idx_hr_employment_department ON hr_employee_employment (department_id);
CREATE INDEX idx_hr_employment_position ON hr_employee_employment (position_id);
CREATE INDEX idx_hr_employment_condition ON hr_employee_employment (working_condition_id);
CREATE INDEX idx_hr_employment_hire_date ON hr_employee_employment (hire_date);
CREATE INDEX idx_hr_employment_contract ON hr_employee_employment (contract_number);
CREATE INDEX idx_hr_identity_legacy_number ON hr_employee_identity (legacy_identity_number);
CREATE INDEX idx_hr_identity_citizen_number ON hr_employee_identity (citizen_identity_number);
CREATE INDEX idx_hr_insurance_social_number ON hr_employee_insurance (social_insurance_number);
CREATE INDEX idx_hr_insurance_health_number ON hr_employee_insurance (health_insurance_number);
CREATE INDEX idx_hr_contacts_phone ON hr_employee_contacts (phone);
CREATE INDEX idx_hr_movement_employee_date ON hr_employee_movements (employee_id, effective_date, status);
CREATE INDEX idx_hr_movement_status_date_type ON hr_employee_movements (status, effective_date, movement_type);
CREATE INDEX idx_hr_movement_import_batch ON hr_employee_movements (import_batch_id);
CREATE INDEX idx_hr_roster_source ON hr_monthly_rosters (source_roster_id);
CREATE INDEX idx_hr_roster_import_batch ON hr_monthly_rosters (source_import_batch_id);
CREATE INDEX idx_hr_roster_item_employee ON hr_monthly_roster_items (employee_id, roster_id);
CREATE INDEX idx_hr_roster_item_movement ON hr_monthly_roster_items (source_movement_id);
CREATE INDEX idx_hr_import_row_status ON hr_excel_import_rows (batch_id, row_status, source_row_number);
CREATE INDEX idx_hr_import_row_employee ON hr_excel_import_rows (employee_id);
CREATE INDEX idx_hr_audit_entity ON hr_audit_events (entity_type, entity_id, occurred_at);
CREATE INDEX idx_hr_audit_actor ON hr_audit_events (actor_subject, occurred_at);
CREATE INDEX idx_hr_audit_correlation ON hr_audit_events (correlation_id);
