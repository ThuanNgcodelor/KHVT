-- Phase 9: preserve confirmed HR history and link a later correction to its source movement.
-- No existing HR row is rewritten by this migration.

ALTER TABLE hr_employee_movements
    ADD COLUMN correction_of_movement_id VARCHAR(36) NULL;

ALTER TABLE hr_employee_movements
    ADD CONSTRAINT fk_hr_movement_correction_target
    FOREIGN KEY (correction_of_movement_id)
    REFERENCES hr_employee_movements (id)
    ON DELETE RESTRICT;

CREATE INDEX idx_hr_movement_correction_target
    ON hr_employee_movements (correction_of_movement_id);
