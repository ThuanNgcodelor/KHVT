ALTER TABLE hr_excel_import_batches
    ADD COLUMN payload_retention_until DATETIME(6) NULL;

ALTER TABLE hr_excel_import_batches
    ADD COLUMN payload_purged_at DATETIME(6) NULL;

ALTER TABLE hr_excel_import_batches
    ADD COLUMN payload_purged_by_actor VARCHAR(320) NULL;

ALTER TABLE hr_excel_import_batches
    ADD CONSTRAINT ck_hr_import_payload_purge_audit CHECK (
        (payload_purged_at IS NULL AND payload_purged_by_actor IS NULL)
        OR (payload_purged_at IS NOT NULL AND payload_purged_by_actor IS NOT NULL)
    );

CREATE INDEX idx_hr_import_payload_retention
    ON hr_excel_import_batches (payload_purged_at, payload_retention_until, status);

ALTER TABLE hr_excel_import_rows
    MODIFY COLUMN raw_payload JSON NULL;
