CREATE TABLE hr_attendance_shift_policies (
    id VARCHAR(36) NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(255) NOT NULL,
    policy_group VARCHAR(32) NOT NULL,
    standard_start TIME NOT NULL,
    standard_end TIME NOT NULL,
    check_in_from TIME NOT NULL,
    check_in_until TIME NOT NULL,
    check_out_from TIME NOT NULL,
    check_out_until TIME NOT NULL,
    crosses_midnight BOOLEAN NOT NULL DEFAULT FALSE,
    night_allowance_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    priority INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    valid_from DATE NOT NULL,
    valid_to DATE NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_attendance_shift_policies PRIMARY KEY (id),
    CONSTRAINT uk_hr_attendance_shift_policy_code UNIQUE (code),
    CONSTRAINT ck_hr_attendance_shift_policy_group CHECK (policy_group IN ('OFFICE', 'PRODUCTION_WORKER', 'KCS')),
    CONSTRAINT ck_hr_attendance_shift_policy_dates CHECK (valid_to IS NULL OR valid_to >= valid_from),
    CONSTRAINT ck_hr_attendance_shift_policy_allowance CHECK (night_allowance_amount >= 0),
    INDEX idx_hr_attendance_shift_policy_lookup (policy_group, active, valid_from, valid_to, priority)
);

CREATE TABLE hr_attendance_work_credit_rules (
    id VARCHAR(36) NOT NULL,
    shift_policy_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    check_out_from TIME NOT NULL,
    check_out_until TIME NOT NULL,
    work_value DECIMAL(4,2) NOT NULL,
    priority INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    valid_from DATE NOT NULL,
    valid_to DATE NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_attendance_work_credit_rules PRIMARY KEY (id),
    CONSTRAINT fk_hr_attendance_credit_shift_policy FOREIGN KEY (shift_policy_id)
        REFERENCES hr_attendance_shift_policies(id) ON DELETE RESTRICT,
    CONSTRAINT ck_hr_attendance_credit_dates CHECK (valid_to IS NULL OR valid_to >= valid_from),
    CONSTRAINT ck_hr_attendance_credit_value CHECK (work_value IN (0, 1, 1.5, 2)),
    INDEX idx_hr_attendance_credit_lookup (shift_policy_id, active, valid_from, valid_to, priority)
);

CREATE TABLE hr_employee_attendance_policies (
    id VARCHAR(36) NOT NULL,
    employee_id VARCHAR(36) NOT NULL,
    policy_group VARCHAR(32) NOT NULL,
    valid_from DATE NOT NULL,
    valid_to DATE NULL,
    source VARCHAR(32) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_employee_attendance_policies PRIMARY KEY (id),
    CONSTRAINT fk_hr_employee_attendance_policy_employee FOREIGN KEY (employee_id)
        REFERENCES hr_employees(id) ON DELETE RESTRICT,
    CONSTRAINT ck_hr_employee_attendance_policy_group CHECK (policy_group IN ('OFFICE', 'PRODUCTION_WORKER', 'KCS')),
    CONSTRAINT ck_hr_employee_attendance_policy_dates CHECK (valid_to IS NULL OR valid_to >= valid_from),
    CONSTRAINT uk_hr_employee_attendance_policy_start UNIQUE (employee_id, valid_from),
    INDEX idx_hr_employee_attendance_policy_lookup (employee_id, valid_from, valid_to)
);

CREATE TABLE hr_production_attendance_imports (
    id VARCHAR(36) NOT NULL,
    source_file_name VARCHAR(255) NOT NULL,
    file_sha256 VARCHAR(64) NOT NULL,
    file_size BIGINT NOT NULL,
    source_sheet_name VARCHAR(100) NOT NULL,
    attendance_month VARCHAR(7) NOT NULL,
    configuration_json JSON NOT NULL,
    status VARCHAR(32) NOT NULL,
    processing_version INT NOT NULL DEFAULT 1,
    total_rows INT NOT NULL DEFAULT 0,
    total_punches INT NOT NULL DEFAULT 0,
    auto_matched_shifts INT NOT NULL DEFAULT 0,
    review_shifts INT NOT NULL DEFAULT 0,
    no_punch_rows INT NOT NULL DEFAULT 0,
    excluded_rows INT NOT NULL DEFAULT 0,
    last_error VARCHAR(1000) NULL,
    confirmed_at DATETIME(6) NULL,
    confirmed_by_actor VARCHAR(320) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_production_attendance_imports PRIMARY KEY (id),
    CONSTRAINT uk_hr_production_attendance_import_sha UNIQUE (file_sha256),
    CONSTRAINT ck_hr_production_attendance_import_status CHECK (status IN ('PREVIEWED', 'CONFIRMED', 'FAILED')),
    INDEX idx_hr_production_attendance_import_month_status (attendance_month, status),
    INDEX idx_hr_production_attendance_import_created (created_at)
);

CREATE TABLE hr_attendance_source_days (
    id VARCHAR(36) NOT NULL,
    import_id VARCHAR(36) NOT NULL,
    employee_id VARCHAR(36) NULL,
    employee_code VARCHAR(32) NOT NULL,
    employee_name VARCHAR(255) NULL,
    work_date DATE NOT NULL,
    source_row_number INT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_attendance_source_days PRIMARY KEY (id),
    CONSTRAINT fk_hr_attendance_source_day_import FOREIGN KEY (import_id)
        REFERENCES hr_production_attendance_imports(id) ON DELETE CASCADE,
    CONSTRAINT fk_hr_attendance_source_day_employee FOREIGN KEY (employee_id)
        REFERENCES hr_employees(id) ON DELETE RESTRICT,
    CONSTRAINT uk_hr_attendance_source_day_row UNIQUE (import_id, source_row_number),
    CONSTRAINT uk_hr_attendance_source_day_employee_date UNIQUE (import_id, employee_code, work_date),
    INDEX idx_hr_attendance_source_day_timeline (import_id, employee_code, work_date)
);

CREATE TABLE hr_attendance_punches (
    id VARCHAR(36) NOT NULL,
    import_id VARCHAR(36) NOT NULL,
    source_day_id VARCHAR(36) NOT NULL,
    employee_id VARCHAR(36) NULL,
    employee_code VARCHAR(32) NOT NULL,
    employee_name VARCHAR(255) NULL,
    work_date DATE NOT NULL,
    punched_at DATETIME(6) NOT NULL,
    source_row_number INT NOT NULL,
    source_column VARCHAR(8) NOT NULL,
    raw_value VARCHAR(100) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_attendance_punches PRIMARY KEY (id),
    CONSTRAINT fk_hr_attendance_punch_import FOREIGN KEY (import_id)
        REFERENCES hr_production_attendance_imports(id) ON DELETE CASCADE,
    CONSTRAINT fk_hr_attendance_punch_source_day FOREIGN KEY (source_day_id)
        REFERENCES hr_attendance_source_days(id) ON DELETE CASCADE,
    CONSTRAINT fk_hr_attendance_punch_employee FOREIGN KEY (employee_id)
        REFERENCES hr_employees(id) ON DELETE RESTRICT,
    CONSTRAINT uk_hr_attendance_punch_source UNIQUE (import_id, source_row_number, source_column),
    INDEX idx_hr_attendance_punch_timeline (import_id, employee_code, punched_at),
    INDEX idx_hr_attendance_punch_employee_date (employee_code, work_date)
);

CREATE TABLE hr_attendance_incidents (
    id VARCHAR(36) NOT NULL,
    incident_type VARCHAR(32) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    ended_at DATETIME(6) NOT NULL,
    scope_type VARCHAR(32) NOT NULL,
    scope_values_json JSON NOT NULL,
    description VARCHAR(1000) NOT NULL,
    status VARCHAR(16) NOT NULL,
    confirmed_at DATETIME(6) NULL,
    confirmed_by_actor VARCHAR(320) NULL,
    cancelled_at DATETIME(6) NULL,
    cancelled_by_actor VARCHAR(320) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_attendance_incidents PRIMARY KEY (id),
    CONSTRAINT ck_hr_attendance_incident_type CHECK (incident_type = 'DEVICE_OUTAGE'),
    CONSTRAINT ck_hr_attendance_incident_scope CHECK (scope_type IN ('ALL', 'EMPLOYEE_CODES', 'POLICY_GROUP', 'DEPARTMENT')),
    CONSTRAINT ck_hr_attendance_incident_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'CANCELLED')),
    CONSTRAINT ck_hr_attendance_incident_period CHECK (ended_at > started_at),
    INDEX idx_hr_attendance_incident_period (status, started_at, ended_at)
);

CREATE TABLE hr_attendance_shifts (
    id VARCHAR(36) NOT NULL,
    import_id VARCHAR(36) NOT NULL,
    employee_id VARCHAR(36) NULL,
    employee_code VARCHAR(32) NOT NULL,
    employee_name VARCHAR(255) NULL,
    work_date DATE NOT NULL,
    calculation_version INT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    policy_group VARCHAR(32) NOT NULL,
    shift_policy_id VARCHAR(36) NULL,
    shift_code_snapshot VARCHAR(40) NULL,
    work_credit_rule_id VARCHAR(36) NULL,
    check_in_punch_id VARCHAR(36) NULL,
    check_out_punch_id VARCHAR(36) NULL,
    check_in_at DATETIME(6) NULL,
    check_out_at DATETIME(6) NULL,
    work_value DECIMAL(4,2) NOT NULL DEFAULT 0,
    night_allowance_amount DECIMAL(15,2) NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    resolution_type VARCHAR(32) NOT NULL,
    explanation VARCHAR(2000) NULL,
    incident_id VARCHAR(36) NULL,
    confirmed_at DATETIME(6) NULL,
    confirmed_by_actor VARCHAR(320) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_attendance_shifts PRIMARY KEY (id),
    CONSTRAINT fk_hr_attendance_shift_import FOREIGN KEY (import_id)
        REFERENCES hr_production_attendance_imports(id) ON DELETE CASCADE,
    CONSTRAINT fk_hr_attendance_shift_employee FOREIGN KEY (employee_id)
        REFERENCES hr_employees(id) ON DELETE RESTRICT,
    CONSTRAINT fk_hr_attendance_shift_policy FOREIGN KEY (shift_policy_id)
        REFERENCES hr_attendance_shift_policies(id) ON DELETE RESTRICT,
    CONSTRAINT fk_hr_attendance_shift_credit_rule FOREIGN KEY (work_credit_rule_id)
        REFERENCES hr_attendance_work_credit_rules(id) ON DELETE RESTRICT,
    CONSTRAINT fk_hr_attendance_shift_check_in FOREIGN KEY (check_in_punch_id)
        REFERENCES hr_attendance_punches(id) ON DELETE RESTRICT,
    CONSTRAINT fk_hr_attendance_shift_check_out FOREIGN KEY (check_out_punch_id)
        REFERENCES hr_attendance_punches(id) ON DELETE RESTRICT,
    CONSTRAINT fk_hr_attendance_shift_incident FOREIGN KEY (incident_id)
        REFERENCES hr_attendance_incidents(id) ON DELETE RESTRICT,
    CONSTRAINT uk_hr_attendance_shift_employee_date_version UNIQUE (import_id, employee_code, work_date, calculation_version),
    CONSTRAINT ck_hr_attendance_shift_group CHECK (policy_group IN ('OFFICE', 'PRODUCTION_WORKER', 'KCS')),
    CONSTRAINT ck_hr_attendance_shift_status CHECK (status IN ('AUTO_MATCHED', 'NEEDS_REVIEW', 'NO_PUNCH', 'EXCLUDED', 'CONFIRMED', 'REJECTED')),
    CONSTRAINT ck_hr_attendance_shift_resolution CHECK (resolution_type IN ('NORMAL', 'MISSING_PUNCH', 'DEVICE_OUTAGE', 'MONTH_BOUNDARY', 'MANUAL_OVERRIDE', 'EXEMPTION')),
    CONSTRAINT ck_hr_attendance_shift_work_value CHECK (work_value IN (0, 1, 1.5, 2)),
    CONSTRAINT ck_hr_attendance_shift_allowance CHECK (night_allowance_amount >= 0),
    INDEX idx_hr_attendance_shift_review (import_id, active, status, work_date),
    INDEX idx_hr_attendance_shift_summary (employee_code, work_date, status)
);

CREATE TABLE hr_attendance_shift_adjustments (
    id VARCHAR(36) NOT NULL,
    shift_id VARCHAR(36) NOT NULL,
    before_json JSON NOT NULL,
    after_json JSON NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_attendance_shift_adjustments PRIMARY KEY (id),
    CONSTRAINT fk_hr_attendance_adjustment_shift FOREIGN KEY (shift_id)
        REFERENCES hr_attendance_shifts(id) ON DELETE CASCADE,
    INDEX idx_hr_attendance_adjustment_shift (shift_id, created_at)
);

CREATE TABLE hr_attendance_exemptions (
    id VARCHAR(36) NOT NULL,
    employee_id VARCHAR(36) NOT NULL,
    employee_code VARCHAR(32) NOT NULL,
    valid_from DATE NOT NULL,
    valid_to DATE NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(16) NOT NULL,
    confirmed_at DATETIME(6) NULL,
    confirmed_by_actor VARCHAR(320) NULL,
    cancelled_at DATETIME(6) NULL,
    cancelled_by_actor VARCHAR(320) NULL,
    cancellation_reason VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_attendance_exemptions PRIMARY KEY (id),
    CONSTRAINT fk_hr_attendance_exemption_employee FOREIGN KEY (employee_id)
        REFERENCES hr_employees(id) ON DELETE RESTRICT,
    CONSTRAINT ck_hr_attendance_exemption_dates CHECK (valid_to >= valid_from),
    CONSTRAINT ck_hr_attendance_exemption_status CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    INDEX idx_hr_attendance_exemption_lookup (employee_code, status, valid_from, valid_to)
);

INSERT INTO hr_attendance_shift_policies (
    id, code, name, policy_group, standard_start, standard_end,
    check_in_from, check_in_until, check_out_from, check_out_until,
    crosses_midnight, night_allowance_amount, priority, active, valid_from,
    created_by_actor, updated_by_actor
) VALUES
('att-shift-hc-office', 'HC', 'Giờ hành chính', 'OFFICE', '07:30', '16:30', '04:00', '09:30', '14:30', '20:30', FALSE, 0, 100, TRUE, '2026-01-01', 'system', 'system'),
('att-shift-hc-kcs', 'KCS_HC', 'KCS giờ hành chính', 'KCS', '07:30', '16:30', '04:00', '09:30', '14:30', '20:30', FALSE, 0, 100, TRUE, '2026-01-01', 'system', 'system'),
('att-shift-kcs-ca2', 'KCS_CA2', 'KCS ca 2', 'KCS', '13:00', '22:00', '11:30', '14:30', '20:30', '23:59', FALSE, 0, 200, TRUE, '2026-01-01', 'system', 'system'),
('att-shift-cn-day', 'CN_DAY', 'Công nhân ca ngày', 'PRODUCTION_WORKER', '06:00', '18:00', '04:00', '09:30', '12:00', '23:59', FALSE, 0, 100, TRUE, '2026-01-01', 'system', 'system'),
('att-shift-cn-night', 'CN_18_5', 'Công nhân ca đêm', 'PRODUCTION_WORKER', '18:00', '05:00', '16:45', '18:59', '04:00', '05:59', TRUE, 50000, 200, TRUE, '2026-01-01', 'system', 'system')
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    updated_by_actor = VALUES(updated_by_actor);

INSERT INTO hr_attendance_work_credit_rules (
    id, shift_policy_id, name, check_out_from, check_out_until,
    work_value, priority, active, valid_from, created_by_actor, updated_by_actor
) VALUES
('att-credit-hc-1', 'att-shift-hc-office', 'Hành chính đủ ca', '14:30', '20:30', 1, 100, TRUE, '2026-01-01', 'system', 'system'),
('att-credit-kcs-hc-1', 'att-shift-hc-kcs', 'KCS hành chính đủ ca', '14:30', '20:30', 1, 100, TRUE, '2026-01-01', 'system', 'system'),
('att-credit-kcs-ca2-1', 'att-shift-kcs-ca2', 'KCS ca 2 đủ ca', '20:30', '23:59', 1, 100, TRUE, '2026-01-01', 'system', 'system'),
('att-credit-cn-day-1', 'att-shift-cn-day', 'Công nhân ca ngày 1 công', '12:00', '14:59', 1, 300, TRUE, '2026-01-01', 'system', 'system'),
('att-credit-cn-day-15', 'att-shift-cn-day', 'Công nhân ca ngày 1,5 công', '15:00', '17:44', 1.5, 200, TRUE, '2026-01-01', 'system', 'system'),
('att-credit-cn-day-2', 'att-shift-cn-day', 'Công nhân ca ngày 2 công', '17:45', '23:59', 2, 100, TRUE, '2026-01-01', 'system', 'system'),
('att-credit-cn-night-15', 'att-shift-cn-night', 'Công nhân ca đêm 1,5 công', '04:00', '05:59', 1.5, 100, TRUE, '2026-01-01', 'system', 'system')
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    updated_by_actor = VALUES(updated_by_actor);
