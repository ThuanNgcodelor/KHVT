CREATE TABLE departments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(50) NULL,
    name VARCHAR(255) NOT NULL,
    parent_id BIGINT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_departments_code (code),
    CONSTRAINT fk_departments_parent FOREIGN KEY (parent_id) REFERENCES departments(id),
    KEY idx_departments_name (name)
);

CREATE TABLE positions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(50) NULL,
    name VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_positions_code (code),
    KEY idx_positions_name (name)
);

CREATE TABLE employees (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    employee_code VARCHAR(80) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(320) NULL,
    phone VARCHAR(50) NULL,
    department_id BIGINT NULL,
    position_id BIGINT NULL,
    manager_id BIGINT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    joined_at DATE NULL,
    left_at DATE NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_employees_code (employee_code),
    UNIQUE KEY uk_employees_email (email),
    CONSTRAINT fk_employees_department FOREIGN KEY (department_id) REFERENCES departments(id),
    CONSTRAINT fk_employees_position FOREIGN KEY (position_id) REFERENCES positions(id),
    CONSTRAINT fk_employees_manager FOREIGN KEY (manager_id) REFERENCES employees(id),
    KEY idx_employees_name (full_name),
    KEY idx_employees_status (status)
);

CREATE TABLE permissions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(80) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500) NULL,
    system_permission BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE KEY uk_permissions_code (code)
);

CREATE TABLE roles (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500) NULL,
    system_role BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_roles_code (code)
);

CREATE TABLE role_permissions (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles(id),
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions(id)
);

CREATE TABLE user_accounts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    email VARCHAR(320) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    employee_id BIGINT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    must_change_password BOOLEAN NOT NULL DEFAULT FALSE,
    failed_login_count INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMP(6) NULL,
    last_login_at TIMESTAMP(6) NULL,
    password_changed_at TIMESTAMP(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_user_accounts_email (email),
    UNIQUE KEY uk_user_accounts_employee (employee_id),
    CONSTRAINT fk_user_accounts_employee FOREIGN KEY (employee_id) REFERENCES employees(id),
    KEY idx_user_accounts_status (status)
);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES user_accounts(id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id)
);

ALTER TABLE suppliers
    ADD COLUMN tax_code VARCHAR(50) NULL,
    ADD COLUMN phone VARCHAR(50) NULL,
    ADD COLUMN email VARCHAR(320) NULL;

CREATE TABLE supplier_aliases (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    supplier_id BIGINT NOT NULL,
    alias VARCHAR(500) NOT NULL,
    normalized_alias VARCHAR(500) NOT NULL,
    UNIQUE KEY uk_supplier_alias (supplier_id, normalized_alias),
    CONSTRAINT fk_supplier_alias_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id),
    KEY idx_supplier_alias_lookup (normalized_alias)
);

CREATE TABLE material_aliases (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    material_id BIGINT NOT NULL,
    alias VARCHAR(500) NOT NULL,
    normalized_alias VARCHAR(500) NOT NULL,
    UNIQUE KEY uk_material_alias (material_id, normalized_alias),
    CONSTRAINT fk_material_alias_material FOREIGN KEY (material_id) REFERENCES materials(id),
    KEY idx_material_alias_lookup (normalized_alias)
);

ALTER TABLE historical_purchases
    MODIFY COLUMN purchase_date DATE NULL,
    ADD COLUMN supplier_code_snapshot VARCHAR(50) NULL,
    ADD COLUMN material_name_normalized_snapshot VARCHAR(500) NULL,
    ADD COLUMN currency_basis VARCHAR(30) NOT NULL DEFAULT 'SOURCE',
    ADD COLUMN source_sheet VARCHAR(80) NULL,
    ADD COLUMN import_batch_id BIGINT NULL,
    ADD COLUMN data_quality_flags JSON NULL,
    ADD CONSTRAINT fk_history_import_batch FOREIGN KEY (import_batch_id) REFERENCES import_batches(id),
    ADD KEY idx_history_currency_date (currency, purchase_date),
    ADD KEY idx_history_name_date (material_name_normalized_snapshot, purchase_date);

ALTER TABLE purchase_orders
    MODIFY COLUMN vat_percent DECIMAL(5,2) NULL,
    ADD COLUMN created_by BIGINT NULL,
    ADD COLUMN updated_by BIGINT NULL,
    ADD COLUMN cancelled_at TIMESTAMP(6) NULL,
    ADD COLUMN cancel_reason VARCHAR(500) NULL,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT fk_purchase_orders_created_by FOREIGN KEY (created_by) REFERENCES user_accounts(id),
    ADD CONSTRAINT fk_purchase_orders_updated_by FOREIGN KEY (updated_by) REFERENCES user_accounts(id);

ALTER TABLE import_batches
    ADD COLUMN created_by BIGINT NULL,
    ADD CONSTRAINT fk_import_batches_created_by FOREIGN KEY (created_by) REFERENCES user_accounts(id),
    ADD UNIQUE KEY uk_import_batch_checksum_mode (sha256, mode);

CREATE TABLE import_rows (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    batch_id BIGINT NOT NULL,
    sheet_name VARCHAR(80) NOT NULL,
    row_number INT NOT NULL,
    raw_json JSON NOT NULL,
    mapped_json JSON NULL,
    issues_json JSON NULL,
    status VARCHAR(20) NOT NULL,
    committed_entity_type VARCHAR(60) NULL,
    committed_entity_id BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_import_rows_batch_sheet_row (batch_id, sheet_name, row_number),
    CONSTRAINT fk_import_rows_batch FOREIGN KEY (batch_id) REFERENCES import_batches(id),
    KEY idx_import_rows_status (batch_id, status)
);

CREATE TABLE purchase_order_revisions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    purchase_order_id BIGINT NOT NULL,
    revision INT NOT NULL,
    snapshot_json JSON NOT NULL,
    changed_by BIGINT NULL,
    change_reason VARCHAR(500) NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_po_revisions_order_revision (purchase_order_id, revision),
    CONSTRAINT fk_po_revisions_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id),
    CONSTRAINT fk_po_revisions_user FOREIGN KEY (changed_by) REFERENCES user_accounts(id)
);

CREATE TABLE generated_documents (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    purchase_order_id BIGINT NOT NULL,
    revision INT NOT NULL,
    document_type VARCHAR(30) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    storage_key VARCHAR(1000) NOT NULL,
    content_type VARCHAR(120) NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    generated_by BIGINT NULL,
    generated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_generated_document_revision (purchase_order_id, revision, document_type),
    CONSTRAINT fk_generated_documents_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id),
    CONSTRAINT fk_generated_documents_user FOREIGN KEY (generated_by) REFERENCES user_accounts(id)
);

ALTER TABLE audit_logs
    ADD COLUMN request_id VARCHAR(64) NULL,
    ADD CONSTRAINT fk_audit_logs_actor FOREIGN KEY (actor_user_id) REFERENCES user_accounts(id) ON DELETE SET NULL,
    ADD KEY idx_audit_logs_entity (entity_type, entity_id, created_at),
    ADD KEY idx_audit_logs_actor (actor_user_id, created_at);

INSERT INTO permissions (code, name, description) VALUES
    ('*', 'Toàn quyền', 'Quyền wildcard chỉ dành cho ADMIN'),
    ('USER_READ', 'Xem tài khoản', 'Xem danh sách và chi tiết tài khoản'),
    ('USER_MANAGE', 'Quản lý tài khoản', 'Tạo, sửa, khóa tài khoản và gán vai trò'),
    ('ROLE_MANAGE', 'Quản lý vai trò', 'Quản lý vai trò và quyền'),
    ('PERSONNEL_READ', 'Xem nhân sự', 'Xem danh bạ nhân sự'),
    ('PERSONNEL_MANAGE', 'Quản lý nhân sự', 'Thêm, sửa, ngừng nhân sự/phòng ban/chức vụ'),
    ('CATALOG_READ', 'Xem danh mục', 'Tra cứu vật tư và nhà cung cấp'),
    ('CATALOG_MANAGE', 'Quản lý danh mục', 'Thêm, sửa, ngừng vật tư và nhà cung cấp'),
    ('PRICE_READ', 'Tra cứu giá', 'Tra cứu lịch sử và giá gần nhất'),
    ('PO_READ', 'Xem đơn mua', 'Xem đơn đặt hàng'),
    ('PO_CREATE', 'Tạo đơn mua', 'Tạo PO'),
    ('PO_EDIT', 'Sửa đơn mua', 'Sửa PO và tạo revision'),
    ('PO_CANCEL', 'Hủy đơn mua', 'Hủy PO'),
    ('IMPORT_LEGACY', 'Import dữ liệu cũ', 'Preview và commit workbook legacy'),
    ('IMPORT_OPERATIONAL', 'Import yêu cầu mua', 'Đọc yêu cầu mua thành danh sách nháp'),
    ('AUDIT_READ', 'Xem nhật ký', 'Tra cứu audit log');

INSERT INTO roles (code, name, description) VALUES
    ('ADMIN', 'Quản trị viên', 'Toàn quyền trên hệ thống'),
    ('HR_MANAGER', 'Quản lý nhân sự', 'Quản lý danh bạ nhân sự'),
    ('PLANNER', 'Nhân viên kế hoạch', 'Thực hiện nghiệp vụ mua hàng'),
    ('VIEWER', 'Chỉ xem', 'Tra cứu, không thay đổi dữ liệu');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = '*'
WHERE r.code = 'ADMIN';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
  ON p.code IN ('PERSONNEL_READ', 'PERSONNEL_MANAGE')
WHERE r.code = 'HR_MANAGER';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
  ON p.code IN ('CATALOG_READ', 'CATALOG_MANAGE', 'PRICE_READ', 'PO_READ', 'PO_CREATE', 'PO_EDIT', 'PO_CANCEL', 'IMPORT_OPERATIONAL')
WHERE r.code = 'PLANNER';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
  ON p.code IN ('PRICE_READ', 'PO_READ')
WHERE r.code = 'VIEWER';
