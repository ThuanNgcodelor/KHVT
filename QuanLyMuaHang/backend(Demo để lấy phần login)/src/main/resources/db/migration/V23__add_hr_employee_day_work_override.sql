ALTER TABLE hr_employee_attendance_policies
    ADD COLUMN day_work_value_override DECIMAL(4,2) NULL;

ALTER TABLE hr_employee_attendance_policies
    ADD CONSTRAINT ck_hr_employee_attendance_day_work_override
        CHECK (day_work_value_override IS NULL OR day_work_value_override IN (1, 1.5, 2));
