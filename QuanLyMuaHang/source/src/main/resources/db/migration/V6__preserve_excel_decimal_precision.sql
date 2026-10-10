-- Preserve source values rather than rounding quantities/prices during import.
-- Existing values remain unchanged; precision already lost is not invented here.
ALTER TABLE historical_purchases
    MODIFY COLUMN quantity DECIMAL(38,18) NULL,
    MODIFY COLUMN unit_price DECIMAL(38,18) NOT NULL;

ALTER TABLE purchase_order_items
    MODIFY COLUMN quantity DECIMAL(38,18) NULL,
    MODIFY COLUMN unit_price DECIMAL(38,18) NOT NULL;
