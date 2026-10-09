ALTER TABLE hr_employee_employment
    ADD COLUMN salary_review_status VARCHAR(24) NULL AFTER next_salary_review_date;

ALTER TABLE hr_employee_employment
    ADD COLUMN salary_review_follow_up_date DATE NULL AFTER salary_review_status;

ALTER TABLE hr_employee_employment
    ADD COLUMN salary_review_note VARCHAR(1000) NULL AFTER salary_review_follow_up_date;

ALTER TABLE hr_employee_employment
    ADD COLUMN salary_review_status_updated_at DATETIME(6) NULL AFTER salary_review_note;

ALTER TABLE hr_employee_employment
    ADD COLUMN salary_review_status_updated_by_actor VARCHAR(320) NULL AFTER salary_review_status_updated_at;

ALTER TABLE hr_employee_employment
    ADD CONSTRAINT ck_hr_employment_salary_review_status
        CHECK (salary_review_status IS NULL OR salary_review_status IN (
            'PENDING', 'IN_REVIEW', 'APPROVED', 'DEFERRED', 'NOT_ELIGIBLE', 'COMPLETED'
        ));

ALTER TABLE hr_employee_employment
    ADD CONSTRAINT ck_hr_employment_salary_review_follow_up
        CHECK (
            (salary_review_status = 'DEFERRED' AND salary_review_follow_up_date IS NOT NULL)
            OR (salary_review_status <> 'DEFERRED' AND salary_review_follow_up_date IS NULL)
            OR (salary_review_status IS NULL AND salary_review_follow_up_date IS NULL)
        );

CREATE INDEX idx_hr_employment_salary_review_due
    ON hr_employee_employment (next_salary_review_date, salary_review_status);

CREATE INDEX idx_hr_employment_salary_review_follow_up
    ON hr_employee_employment (salary_review_follow_up_date, salary_review_status);

ALTER TABLE hr_employee_salary_changes
    MODIFY COLUMN review_cycle_months INT NULL;

ALTER TABLE hr_employee_salary_changes
    MODIFY COLUMN next_review_date DATE NULL;

ALTER TABLE hr_employee_salary_changes
    DROP CONSTRAINT ck_hr_salary_change_cycle;

ALTER TABLE hr_employee_salary_changes
    ADD CONSTRAINT ck_hr_salary_change_cycle
        CHECK (review_cycle_months IS NULL OR review_cycle_months > 0);
