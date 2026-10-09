-- Store the exact text approved for each delivery. Historical records remain nullable.
ALTER TABLE hr_payroll_deliveries ADD COLUMN message_snapshot TEXT NULL;
