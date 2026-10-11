-- Thêm composite indexes để tối ưu hoá Server-side sorting, phân trang và truy vấn chống lag

-- 1. Bảng purchase_orders
CREATE INDEX idx_po_order_date_id ON purchase_orders(order_date DESC, id DESC);
CREATE INDEX idx_po_status_order_date ON purchase_orders(status, order_date DESC);
CREATE INDEX idx_po_supplier_name ON purchase_orders(supplier_name_snapshot(100), order_date DESC);

-- 2. Bảng historical_purchases
CREATE INDEX idx_hist_purchase_date_id ON historical_purchases(purchase_date DESC, id DESC);
CREATE INDEX idx_hist_material_code ON historical_purchases(material_code_snapshot, purchase_date DESC);
CREATE INDEX idx_hist_norm_name ON historical_purchases(material_name_normalized_snapshot(100));
CREATE INDEX idx_hist_price ON historical_purchases(unit_price, purchase_date DESC);

-- 3. Bảng materials & suppliers
CREATE INDEX idx_materials_name ON materials(name(100), id ASC);
CREATE INDEX idx_materials_active_name ON materials(active, name(100));

CREATE INDEX idx_suppliers_name ON suppliers(name(100), id ASC);
CREATE INDEX idx_suppliers_active_name ON suppliers(active, name(100));
