CREATE TABLE suppliers (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(50) NULL,
    name VARCHAR(500) NOT NULL,
    normalized_name VARCHAR(500) NOT NULL,
    address VARCHAR(1000) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    UNIQUE KEY uk_suppliers_code (code),
    KEY idx_suppliers_normalized_name (normalized_name)
);

CREATE TABLE materials (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(80) NULL,
    name VARCHAR(500) NOT NULL,
    normalized_name VARCHAR(500) NOT NULL,
    category VARCHAR(20) NOT NULL,
    default_unit VARCHAR(100) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    UNIQUE KEY uk_materials_code (code),
    KEY idx_materials_normalized_name (normalized_name)
);

CREATE TABLE historical_purchases (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    purchase_date DATE NOT NULL,
    supplier_id BIGINT NULL,
    supplier_snapshot VARCHAR(500) NULL,
    material_id BIGINT NULL,
    material_code_snapshot VARCHAR(80) NULL,
    material_name_snapshot VARCHAR(500) NOT NULL,
    unit VARCHAR(100) NULL,
    quantity DECIMAL(20,6) NULL,
    quantity_text VARCHAR(255) NULL,
    unit_price DECIMAL(20,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    source VARCHAR(30) NOT NULL,
    source_row_number INT NULL,
    category VARCHAR(20) NOT NULL,
    CONSTRAINT fk_history_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id),
    CONSTRAINT fk_history_material FOREIGN KEY (material_id) REFERENCES materials(id),
    KEY idx_history_material_date (material_id, purchase_date DESC)
);

CREATE TABLE purchase_orders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    po_number VARCHAR(40) NOT NULL,
    order_date DATE NOT NULL,
    supplier_id BIGINT NULL,
    supplier_name_snapshot VARCHAR(500) NOT NULL,
    supplier_address_snapshot VARCHAR(1000) NULL,
    currency VARCHAR(3) NOT NULL,
    vat_percent DECIMAL(5,2) NOT NULL,
    note VARCHAR(1000) NULL,
    prepared_by VARCHAR(255) NULL,
    status VARCHAR(20) NOT NULL,
    revision INT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    UNIQUE KEY uk_purchase_orders_po_number (po_number),
    CONSTRAINT fk_purchase_orders_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id),
    KEY idx_purchase_orders_order_date (order_date),
    KEY idx_purchase_orders_supplier_date (supplier_id, order_date)
);

CREATE TABLE purchase_order_items (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    purchase_order_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    material_id BIGINT NULL,
    material_code_snapshot VARCHAR(80) NULL,
    material_name VARCHAR(500) NOT NULL,
    specification VARCHAR(1000) NULL,
    unit VARCHAR(100) NULL,
    quantity DECIMAL(20,6) NULL,
    quantity_text VARCHAR(255) NULL,
    unit_price DECIMAL(20,4) NOT NULL,
    CONSTRAINT fk_po_items_order FOREIGN KEY (purchase_order_id) REFERENCES purchase_orders(id),
    CONSTRAINT fk_po_items_material FOREIGN KEY (material_id) REFERENCES materials(id),
    UNIQUE KEY uk_po_items_line (purchase_order_id, line_no),
    KEY idx_po_items_material (material_id)
);

CREATE TABLE po_daily_sequences (
    sequence_date DATE PRIMARY KEY,
    next_number INT NOT NULL
);

CREATE TABLE import_batches (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    file_name VARCHAR(255) NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    file_type VARCHAR(20) NOT NULL,
    mode VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    total_rows INT NOT NULL DEFAULT 0,
    success_rows INT NOT NULL DEFAULT 0,
    error_rows INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    KEY idx_import_batches_created_at (created_at),
    KEY idx_import_batches_sha256 (sha256)
);

CREATE TABLE audit_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    actor_user_id BIGINT NULL,
    action VARCHAR(50) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id BIGINT NULL,
    before_json JSON NULL,
    after_json JSON NULL,
    ip_address VARCHAR(64) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    KEY idx_audit_logs_created_at (created_at)
);
