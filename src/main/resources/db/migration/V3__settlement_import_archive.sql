-- Flyway Migration: V3__settlement_import_archive.sql
-- Description: Archive imported broker settlement files and map stock trades into actual_trade.

ALTER TABLE actual_trade MODIFY COLUMN price DECIMAL(18,4) NOT NULL;
ALTER TABLE actual_trade MODIFY COLUMN quantity DECIMAL(18,4) NOT NULL;

ALTER TABLE actual_trade ADD COLUMN IF NOT EXISTS turnover_amount DECIMAL(18,4);
ALTER TABLE actual_trade ADD COLUMN IF NOT EXISTS settlement_amount DECIMAL(18,4);
ALTER TABLE actual_trade ADD COLUMN IF NOT EXISTS stamp_tax DECIMAL(18,4);
ALTER TABLE actual_trade ADD COLUMN IF NOT EXISTS transfer_fee DECIMAL(18,4);
ALTER TABLE actual_trade ADD COLUMN IF NOT EXISTS commission DECIMAL(18,4);
ALTER TABLE actual_trade ADD COLUMN IF NOT EXISTS other_fee DECIMAL(18,4);
ALTER TABLE actual_trade ADD COLUMN IF NOT EXISTS total_fee DECIMAL(18,4);
ALTER TABLE actual_trade ADD COLUMN IF NOT EXISTS settlement_account_number VARCHAR(40);
ALTER TABLE actual_trade ADD COLUMN IF NOT EXISTS settlement_trade_type VARCHAR(40);
ALTER TABLE actual_trade ADD COLUMN IF NOT EXISTS settlement_unique_key VARCHAR(255);
ALTER TABLE actual_trade ADD COLUMN IF NOT EXISTS settlement_record_id BIGINT;

CREATE UNIQUE INDEX IF NOT EXISTS uk_actual_trade_settlement_key
    ON actual_trade(settlement_unique_key);

CREATE TABLE IF NOT EXISTS settlement_import_batch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    original_filename VARCHAR(255),
    file_hash VARCHAR(64) NOT NULL,
    raw_content LONGTEXT,
    status VARCHAR(20) NOT NULL,
    archived_rows INT NOT NULL DEFAULT 0,
    imported_trades INT NOT NULL DEFAULT 0,
    skipped_rows INT NOT NULL DEFAULT 0,
    duplicate_rows INT NOT NULL DEFAULT 0,
    failed_rows INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sib_file_hash ON settlement_import_batch(file_hash);
CREATE INDEX IF NOT EXISTS idx_sib_created_at ON settlement_import_batch(created_at);

CREATE TABLE IF NOT EXISTS settlement_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    row_number INT NOT NULL,
    raw_line TEXT,
    stock_code VARCHAR(20),
    stock_name VARCHAR(50),
    turnover_amount DECIMAL(18,4),
    settlement_amount DECIMAL(18,4),
    stamp_tax DECIMAL(18,4),
    transfer_fee DECIMAL(18,4),
    trade_date DATE,
    trade_type VARCHAR(40),
    currency VARCHAR(20),
    quantity DECIMAL(18,4),
    price DECIMAL(18,4),
    account_number VARCHAR(40),
    commission DECIMAL(18,4),
    other_fee DECIMAL(18,4),
    remark VARCHAR(255),
    settlement_unique_key VARCHAR(255),
    import_status VARCHAR(20) NOT NULL,
    skip_reason VARCHAR(255),
    actual_trade_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_settlement_record_batch
        FOREIGN KEY (batch_id) REFERENCES settlement_import_batch(id)
);

CREATE INDEX IF NOT EXISTS idx_sr_batch ON settlement_record(batch_id);
CREATE INDEX IF NOT EXISTS idx_sr_settlement_key ON settlement_record(settlement_unique_key);
CREATE INDEX IF NOT EXISTS idx_sr_trade_date ON settlement_record(trade_date);
CREATE INDEX IF NOT EXISTS idx_sr_stock ON settlement_record(stock_code);
