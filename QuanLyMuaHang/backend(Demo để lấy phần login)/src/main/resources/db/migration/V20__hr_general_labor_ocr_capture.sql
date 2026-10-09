-- Temporary, owner-scoped capture sessions; never creates an employee or movement.
CREATE TABLE hr_ocr_capture_sessions (
    id VARCHAR(36) PRIMARY KEY,
    owner_subject VARCHAR(320) NOT NULL,
    status VARCHAR(16) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    paired BOOLEAN NOT NULL DEFAULT FALSE,
    result_json MEDIUMTEXT NULL,
    error_message VARCHAR(500) NULL,
    run_id VARCHAR(36) NULL,
    run_started_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL
);
CREATE INDEX idx_hr_ocr_capture_owner ON hr_ocr_capture_sessions(owner_subject, expires_at);
CREATE INDEX idx_hr_ocr_capture_expiry ON hr_ocr_capture_sessions(expires_at);
CREATE INDEX idx_hr_ocr_capture_run ON hr_ocr_capture_sessions(status, run_started_at);

CREATE TABLE hr_ocr_capture_images (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    client_id VARCHAR(36) NOT NULL,
    kind VARCHAR(16) NOT NULL,
    content_type VARCHAR(32) NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    content MEDIUMBLOB NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_hr_ocr_capture_image_session FOREIGN KEY (session_id)
        REFERENCES hr_ocr_capture_sessions(id) ON DELETE CASCADE,
    CONSTRAINT uq_hr_ocr_capture_upload UNIQUE (session_id, client_id)
);
