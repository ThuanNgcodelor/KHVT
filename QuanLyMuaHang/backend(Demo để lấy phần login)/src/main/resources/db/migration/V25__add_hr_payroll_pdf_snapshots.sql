-- Preserve the exact PDF that was prepared for each delivery. This keeps resend
-- and audit deterministic even if the payroll import source changes later.
ALTER TABLE hr_payroll_deliveries
    ADD COLUMN document_snapshot LONGBLOB NULL;
ALTER TABLE hr_payroll_deliveries
    ADD COLUMN document_file_name VARCHAR(255) NULL;

ALTER TABLE hr_payroll_test_deliveries
    ADD COLUMN document_snapshot LONGBLOB NULL;
ALTER TABLE hr_payroll_test_deliveries
    ADD COLUMN document_file_name VARCHAR(255) NULL;
