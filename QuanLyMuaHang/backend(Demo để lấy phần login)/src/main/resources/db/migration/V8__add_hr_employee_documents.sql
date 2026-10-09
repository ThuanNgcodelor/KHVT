-- Employee document repository table for managing attached documents and inline PDF preview.

CREATE TABLE hr_employee_documents (
    id VARCHAR(36) NOT NULL,
    employee_id VARCHAR(36) NOT NULL,
    document_category VARCHAR(50) NOT NULL,
    document_name VARCHAR(255) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_type VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    file_sha256 CHAR(64) NOT NULL,
    file_data LONGBLOB NOT NULL,
    document_number VARCHAR(100),
    issue_date DATE,
    expiry_date DATE,
    issuing_authority VARCHAR(255),
    note VARCHAR(1000),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_employee_documents PRIMARY KEY (id),
    CONSTRAINT fk_hr_employee_document_employee
        FOREIGN KEY (employee_id) REFERENCES hr_employees (id) ON DELETE CASCADE,
    CONSTRAINT ck_hr_employee_document_category
        CHECK (document_category IN ('CITIZEN_ID', 'CURRICULUM_VITAE', 'DEGREE_CERTIFICATE', 'HEALTH_CERTIFICATE', 'LABOR_CONTRACT', 'DECISION', 'OTHER')),
    CONSTRAINT ck_hr_employee_document_row_version CHECK (row_version >= 0)
);

CREATE INDEX idx_hr_employee_documents_emp_cat
    ON hr_employee_documents (employee_id, document_category);

CREATE INDEX idx_hr_employee_documents_emp_date
    ON hr_employee_documents (employee_id, created_at);
