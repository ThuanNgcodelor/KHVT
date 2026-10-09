ALTER TABLE purchase_orders
    MODIFY COLUMN order_date DATE NULL,
    ADD COLUMN source_po_number VARCHAR(40) NULL,
    ADD COLUMN source_import_batch_id BIGINT NULL,
    ADD CONSTRAINT fk_purchase_orders_source_batch FOREIGN KEY (source_import_batch_id) REFERENCES import_batches(id),
    ADD KEY idx_purchase_orders_source_po (source_po_number);
