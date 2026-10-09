CREATE TABLE hr_word_editor_sessions (
    id VARCHAR(36) PRIMARY KEY,
    target_type VARCHAR(16) NOT NULL,
    template_kind VARCHAR(32) NULL,
    source_id VARCHAR(36) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content MEDIUMBLOB NOT NULL,
    owner_subject VARCHAR(320) NOT NULL,
    owner_name VARCHAR(255) NULL,
    owner_role VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    result_id VARCHAR(36) NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL
);
CREATE INDEX idx_hr_word_editor_owner ON hr_word_editor_sessions(owner_subject, created_at);
