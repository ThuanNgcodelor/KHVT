ALTER TABLE historical_purchases
    ADD COLUMN source_reference VARCHAR(120) NULL,
    ADD UNIQUE KEY uk_history_source_reference (source_reference);
