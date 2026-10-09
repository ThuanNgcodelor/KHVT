-- Immutable generated DOCX evidence for formal employment contracts.

CREATE TABLE hr_employment_contract_documents (
    id VARCHAR(36) NOT NULL,
    employment_contract_id VARCHAR(36) NOT NULL,
    workforce_group VARCHAR(32) NOT NULL,
    template_file_name VARCHAR(255) NOT NULL,
    template_sha256 CHAR(64) NOT NULL,
    generated_file_name VARCHAR(255) NOT NULL,
    generated_file_sha256 CHAR(64) NOT NULL,
    generated_docx MEDIUMBLOB NOT NULL,
    snapshot_payload JSON NOT NULL,
    generated_at DATETIME(6) NOT NULL,
    generated_by_actor VARCHAR(320) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_employment_contract_documents PRIMARY KEY (id),
    CONSTRAINT fk_hr_employment_contract_document_contract
        FOREIGN KEY (employment_contract_id) REFERENCES hr_employment_contracts (id) ON DELETE CASCADE,
    CONSTRAINT ck_hr_employment_contract_document_group
        CHECK (workforce_group IN ('OFFICE', 'GENERAL_LABOR')),
    CONSTRAINT ck_hr_employment_contract_document_row_version CHECK (row_version >= 0)
);

CREATE INDEX idx_hr_employment_contract_document_contract
    ON hr_employment_contract_documents (employment_contract_id, generated_at);
