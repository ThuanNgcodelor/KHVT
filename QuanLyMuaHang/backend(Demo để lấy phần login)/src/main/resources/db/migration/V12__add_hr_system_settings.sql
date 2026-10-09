-- Migration V12: System settings table for OCR provider configuration (Gemini / Groq)

CREATE TABLE hr_system_settings (
    setting_key VARCHAR(100) NOT NULL,
    setting_value TEXT NULL,
    category VARCHAR(50) NOT NULL DEFAULT 'GENERAL',
    description VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL DEFAULT 'system',
    updated_by_actor VARCHAR(320) NOT NULL DEFAULT 'system',
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_hr_system_settings PRIMARY KEY (setting_key)
);

INSERT INTO hr_system_settings (setting_key, setting_value, category, description, created_by_actor, updated_by_actor)
VALUES
('ocr.provider', 'GEMINI', 'OCR', 'Nhà cung cấp OCR: GEMINI hoặc GROQ', 'system', 'system'),
('ocr.gemini.model', 'gemini-1.5-flash', 'OCR', 'Model Google Gemini Vision', 'system', 'system'),
('ocr.groq.model', 'llama-3.2-11b-vision-preview', 'OCR', 'Model Groq Vision', 'system', 'system');
