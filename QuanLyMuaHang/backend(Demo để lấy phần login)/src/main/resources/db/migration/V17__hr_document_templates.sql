CREATE TABLE hr_document_template_families (
    template_kind VARCHAR(32) PRIMARY KEY,
    active_version_id VARCHAR(36) NULL
);
INSERT INTO hr_document_template_families (template_kind) VALUES ('OFFICE'), ('GENERAL_LABOR'), ('PROBATION');

CREATE TABLE hr_document_template_revisions (
    id VARCHAR(36) PRIMARY KEY,
    template_kind VARCHAR(32) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_sha256 CHAR(64) NOT NULL,
    content MEDIUMBLOB NOT NULL,
    created_at DATETIME(6) NOT NULL,
    created_by_actor VARCHAR(320) NOT NULL,
    note VARCHAR(1000) NOT NULL,
    CONSTRAINT fk_hr_document_template_family FOREIGN KEY (template_kind) REFERENCES hr_document_template_families(template_kind),
    CONSTRAINT uk_hr_document_template_hash UNIQUE (template_kind, file_sha256)
);
CREATE INDEX idx_hr_document_template_revisions ON hr_document_template_revisions(template_kind, created_at);
ALTER TABLE hr_document_template_families ADD CONSTRAINT fk_hr_document_template_active
    FOREIGN KEY (active_version_id) REFERENCES hr_document_template_revisions(id);
