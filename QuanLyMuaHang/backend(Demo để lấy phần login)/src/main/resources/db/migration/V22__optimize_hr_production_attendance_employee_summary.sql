CREATE INDEX idx_hr_attendance_shift_import_employee_active
    ON hr_attendance_shifts (import_id, employee_code, active, work_date);
