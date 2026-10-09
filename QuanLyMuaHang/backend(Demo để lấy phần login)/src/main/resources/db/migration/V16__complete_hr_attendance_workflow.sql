ALTER TABLE hr_attendance_imports ADD COLUMN auto_filled_rows INT NOT NULL DEFAULT 0;
ALTER TABLE hr_attendance_imports ADD COLUMN no_punch_rows INT NOT NULL DEFAULT 0;
ALTER TABLE hr_attendance_imports ADD COLUMN excluded_rows INT NOT NULL DEFAULT 0;
ALTER TABLE hr_attendance_imports ADD COLUMN confirmed_at DATETIME(6) NULL;
ALTER TABLE hr_attendance_imports ADD COLUMN confirmed_by_actor VARCHAR(320) NULL;
CREATE INDEX idx_hr_attendance_import_month_status ON hr_attendance_imports (attendance_month, status);

UPDATE hr_attendance_imports attendance_import
SET auto_filled_rows = (
        SELECT COUNT(*) FROM hr_attendance_records attendance_record
        WHERE attendance_record.import_id = attendance_import.id
          AND attendance_record.status = 'AUTO_FILLED'
    ),
    no_punch_rows = (
        SELECT COUNT(*) FROM hr_attendance_records attendance_record
        WHERE attendance_record.import_id = attendance_import.id
          AND attendance_record.status = 'NO_PUNCH'
    ),
    excluded_rows = (
        SELECT COUNT(*) FROM hr_attendance_records attendance_record
        WHERE attendance_record.import_id = attendance_import.id
          AND attendance_record.status = 'EXCLUDED'
    );

INSERT INTO hr_system_settings (
    setting_key, setting_value, category, description, created_by_actor, updated_by_actor
) VALUES (
    'attendance.autoFillMissingPunches', 'true', 'ATTENDANCE',
    'Tự điền lượt còn thiếu khi ngày có đúng một phía chấm công', 'system', 'system'
)
ON DUPLICATE KEY UPDATE
    category = VALUES(category),
    description = VALUES(description);
