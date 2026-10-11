CREATE TABLE source_document_imports (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    kind VARCHAR(20) NOT NULL,
    title VARCHAR(255) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    sheet_key VARCHAR(80) NOT NULL DEFAULT '',
    storage_key VARCHAR(200) NOT NULL,
    parsed_json LONGTEXT NOT NULL,
    confirmed_json LONGTEXT,
    inquiries_json LONGTEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_by BIGINT,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_source_document UNIQUE (checksum, kind, sheet_key),
    INDEX ix_source_document_kind_status (kind, status, id)
);
