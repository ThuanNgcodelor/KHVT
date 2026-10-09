-- BookingBase HR Migration V11
-- Assign dedicated unused C-codes under C746 (C688, C689) to terminated employees
-- and restore 338 ACTIVE + 38 INACTIVE = 376 total employees

-- 1. Update any legacy or intermediate codes to C688 and C689
UPDATE hr_employees SET employee_code = 'C688' WHERE employee_code = 'B266';
UPDATE hr_employees SET employee_code = 'C689' WHERE employee_code = 'C722N';

-- 2. Insert/Update Quách Trung Hậu with dedicated code C688 (INACTIVE - Discharged 2026-03-10)
INSERT INTO hr_employees (id, employee_code, full_name, gender, date_of_birth, education_level, employment_status, status_effective_date, created_at, updated_at, created_by_actor, updated_by_actor, row_version)
VALUES ('fc9b36d6-f404-58e9-9134-d30906be336a', 'C688', 'Quách Trung Hậu', 'MALE', '1996-07-26', 'Phổ thông', 'INACTIVE', '2026-03-10', NOW(6), NOW(6), 'system', 'system', 0)
ON DUPLICATE KEY UPDATE employee_code = VALUES(employee_code), full_name = VALUES(full_name), gender = VALUES(gender), employment_status = VALUES(employment_status), status_effective_date = VALUES(status_effective_date), updated_at = NOW(6);

INSERT INTO hr_employee_employment (employee_id, department_id, position_id, hire_date, termination_date, contract_type_label, base_salary, allowance, created_at, updated_at, created_by_actor, updated_by_actor, row_version)
SELECT e.id, d.id, p.id, '2024-01-01', '2026-03-10', 'Kinh', 4410000.0, 0, NOW(6), NOW(6), 'system', 'system', 0
FROM hr_employees e
LEFT JOIN hr_departments d ON d.name = 'XNPBHC' OR d.name = 'XN. Phân bón Hóa chất'
LEFT JOIN hr_positions p ON p.name = 'Công nhân XNPBHC' OR p.name = '1'
WHERE e.employee_code = 'C688'
LIMIT 1
ON DUPLICATE KEY UPDATE hire_date = VALUES(hire_date), termination_date = VALUES(termination_date), contract_type_label = VALUES(contract_type_label), base_salary = VALUES(base_salary), allowance = VALUES(allowance), updated_at = NOW(6);

INSERT INTO hr_employee_identity (employee_id, citizen_identity_number, verification_status, created_at, updated_at, created_by_actor, updated_by_actor, row_version)
SELECT e.id, '092096000440', 'VERIFIED', NOW(6), NOW(6), 'system', 'system', 0
FROM hr_employees e WHERE e.employee_code = 'C688'
ON DUPLICATE KEY UPDATE citizen_identity_number = VALUES(citizen_identity_number), updated_at = NOW(6);

INSERT INTO hr_employee_insurance (employee_id, social_insurance_number, health_insurance_number, status, created_at, updated_at, created_by_actor, updated_by_actor, row_version)
SELECT e.id, '9216007053', 'DN4929216007053', 'INACTIVE', NOW(6), NOW(6), 'system', 'system', 0
FROM hr_employees e WHERE e.employee_code = 'C688'
ON DUPLICATE KEY UPDATE status = 'INACTIVE', updated_at = NOW(6);

INSERT INTO hr_employee_contacts (employee_id, permanent_address, current_address, created_at, updated_at, created_by_actor, updated_by_actor, row_version)
SELECT e.id, 'Tân Long', 'Tân Long', NOW(6), NOW(6), 'system', 'system', 0
FROM hr_employees e WHERE e.employee_code = 'C688'
ON DUPLICATE KEY UPDATE permanent_address = VALUES(permanent_address), current_address = VALUES(current_address), updated_at = NOW(6);

INSERT INTO hr_employee_movements (id, employee_id, movement_type, status, effective_date, decision_number, decision_date, from_employee_status, to_employee_status, reason, source_kind, confirmed_at, confirmed_by_actor, created_at, updated_at, created_by_actor, updated_by_actor, row_version)
SELECT '4f20d1ac-f581-5be0-9460-a1bb2d74c0f7', e.id, 'DECREASE', 'CONFIRMED', '2026-03-10', '31/QĐ-PBHC', '2026-03-10', 'ACTIVE', 'INACTIVE', 'Kỷ luật sa thải', 'MANUAL', NOW(6), 'system', NOW(6), NOW(6), 'system', 'system', 0
FROM hr_employees e WHERE e.employee_code = 'C688'
ON DUPLICATE KEY UPDATE employee_id = VALUES(employee_id), movement_type = VALUES(movement_type), status = 'CONFIRMED', effective_date = VALUES(effective_date), decision_number = VALUES(decision_number), decision_date = VALUES(decision_date), reason = VALUES(reason), confirmed_at = NOW(6), confirmed_by_actor = 'system', updated_at = NOW(6);


-- 3. Insert/Update Phan Văn Nhẫn with dedicated code C689 (INACTIVE - Resigned 2026-05-12)
INSERT INTO hr_employees (id, employee_code, full_name, gender, date_of_birth, education_level, employment_status, status_effective_date, created_at, updated_at, created_by_actor, updated_by_actor, row_version)
VALUES ('117e1b66-7224-5bdb-b498-6b960f933b52', 'C689', 'Phan Văn Nhẫn', 'MALE', '1995-08-12', 'Đại học', 'INACTIVE', '2026-05-12', NOW(6), NOW(6), 'system', 'system', 0)
ON DUPLICATE KEY UPDATE employee_code = VALUES(employee_code), full_name = VALUES(full_name), gender = VALUES(gender), employment_status = VALUES(employment_status), status_effective_date = VALUES(status_effective_date), updated_at = NOW(6);

INSERT INTO hr_employee_employment (employee_id, department_id, position_id, hire_date, termination_date, contract_type_label, base_salary, allowance, created_at, updated_at, created_by_actor, updated_by_actor, row_version)
SELECT e.id, d.id, p.id, '2024-01-01', '2026-05-12', 'Không xác định', 4410000, 0, NOW(6), NOW(6), 'system', 'system', 0
FROM hr_employees e
LEFT JOIN hr_departments d ON d.name = 'XNPBHC' OR d.name = 'XN. Phân bón Hóa chất'
LEFT JOIN hr_positions p ON p.name = 'Công nhân XNPBHC' OR p.name = '1'
WHERE e.employee_code = 'C689'
LIMIT 1
ON DUPLICATE KEY UPDATE hire_date = VALUES(hire_date), termination_date = VALUES(termination_date), contract_type_label = VALUES(contract_type_label), base_salary = VALUES(base_salary), allowance = VALUES(allowance), updated_at = NOW(6);

INSERT INTO hr_employee_identity (employee_id, citizen_identity_number, verification_status, created_at, updated_at, created_by_actor, updated_by_actor, row_version)
SELECT e.id, '092095007890', 'VERIFIED', NOW(6), NOW(6), 'system', 'system', 0
FROM hr_employees e WHERE e.employee_code = 'C689'
ON DUPLICATE KEY UPDATE citizen_identity_number = VALUES(citizen_identity_number), updated_at = NOW(6);

INSERT INTO hr_employee_insurance (employee_id, status, created_at, updated_at, created_by_actor, updated_by_actor, row_version)
SELECT e.id, 'INACTIVE', NOW(6), NOW(6), 'system', 'system', 0
FROM hr_employees e WHERE e.employee_code = 'C689'
ON DUPLICATE KEY UPDATE status = 'INACTIVE', updated_at = NOW(6);

INSERT INTO hr_employee_contacts (employee_id, permanent_address, current_address, created_at, updated_at, created_by_actor, updated_by_actor, row_version)
SELECT e.id, 'Ô Môn, Cần Thơ', 'Ô Môn, Cần Thơ', NOW(6), NOW(6), 'system', 'system', 0
FROM hr_employees e WHERE e.employee_code = 'C689'
ON DUPLICATE KEY UPDATE permanent_address = VALUES(permanent_address), current_address = VALUES(current_address), updated_at = NOW(6);

INSERT INTO hr_employee_movements (id, employee_id, movement_type, status, effective_date, decision_number, decision_date, from_employee_status, to_employee_status, reason, source_kind, confirmed_at, confirmed_by_actor, created_at, updated_at, created_by_actor, updated_by_actor, row_version)
SELECT '8adc6e76-8bf7-579b-b5ed-1fca1cf8a4cf', e.id, 'DECREASE', 'CONFIRMED', '2026-05-12', '48/QĐ-PBHC', '2026-05-12', 'ACTIVE', 'INACTIVE', 'Thôi việc theo nguyện vọng', 'MANUAL', NOW(6), 'system', NOW(6), NOW(6), 'system', 'system', 0
FROM hr_employees e WHERE e.employee_code = 'C689'
ON DUPLICATE KEY UPDATE employee_id = VALUES(employee_id), movement_type = VALUES(movement_type), status = 'CONFIRMED', effective_date = VALUES(effective_date), decision_number = VALUES(decision_number), decision_date = VALUES(decision_date), reason = VALUES(reason), confirmed_at = NOW(6), confirmed_by_actor = 'system', updated_at = NOW(6);


-- 4. Restore C720 (Châu Văn Hậu) to ACTIVE (working in T8-26)
UPDATE hr_employees
SET full_name = 'Châu Văn Hậu', employment_status = 'ACTIVE', status_effective_date = '2026-05-01', updated_at = NOW(6)
WHERE employee_code = 'C720';

UPDATE hr_employee_employment
SET termination_date = NULL, base_salary = 4730000.0, allowance = 0.0, updated_at = NOW(6)
WHERE employee_id IN (SELECT id FROM hr_employees WHERE employee_code = 'C720');

UPDATE hr_employee_insurance
SET social_insurance_number = '9221418083', health_insurance_number = 'DN4929221418083', status = 'ACTIVE', updated_at = NOW(6)
WHERE employee_id IN (SELECT id FROM hr_employees WHERE employee_code = 'C720');

DELETE FROM hr_employee_movements
WHERE movement_type = 'DECREASE' AND employee_id IN (SELECT id FROM hr_employees WHERE employee_code = 'C720');


-- 5. Restore C722 (Lê Hoàng Tâm) to ACTIVE (working in T8-26)
UPDATE hr_employees
SET full_name = 'Lê Hoàng Tâm', employment_status = 'ACTIVE', status_effective_date = '2026-05-01', updated_at = NOW(6)
WHERE employee_code = 'C722';

UPDATE hr_employee_employment
SET termination_date = NULL, base_salary = 4730000.0, allowance = 0.0, updated_at = NOW(6)
WHERE employee_id IN (SELECT id FROM hr_employees WHERE employee_code = 'C722');

UPDATE hr_employee_insurance
SET social_insurance_number = '9222440655', health_insurance_number = 'DN4929222440655', status = 'ACTIVE', updated_at = NOW(6)
WHERE employee_id IN (SELECT id FROM hr_employees WHERE employee_code = 'C722');

DELETE FROM hr_employee_movements
WHERE movement_type = 'DECREASE' AND employee_id IN (SELECT id FROM hr_employees WHERE employee_code = 'C722');
