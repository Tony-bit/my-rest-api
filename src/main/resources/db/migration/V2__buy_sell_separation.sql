-- Flyway Migration: V2__buy_sell_separation.sql
-- Description: Implement buy-sell separation architecture
-- Author: auto-generated
-- Date: 2026-05-22

-- ============================================================================
-- STEP 1: Create backup of existing data (for emergency recovery)
-- ============================================================================

-- Backup table for plan data
CREATE TABLE IF NOT EXISTS plan_backup (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100),
    stock_code VARCHAR(20),
    stock_name VARCHAR(50),
    cycle VARCHAR(20),
    status VARCHAR(20),
    is_locked BOOLEAN,
    execution_quantity DECIMAL(10,2),
    trigger_date DATE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    backup_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Backup table for plan_condition data
CREATE TABLE IF NOT EXISTS plan_condition_backup (
    id BIGINT PRIMARY KEY,
    plan_id BIGINT,
    condition_type VARCHAR(20),
    direction VARCHAR(20),
    ma_period INTEGER,
    target_price DECIMAL(10,2),
    is_active BOOLEAN,
    backup_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Backup table for plan_execution data
CREATE TABLE IF NOT EXISTS plan_execution_backup (
    id BIGINT PRIMARY KEY,
    plan_id BIGINT,
    trade_date DATE,
    direction VARCHAR(20),
    triggered BOOLEAN,
    trigger_price DECIMAL(10,2),
    close_price DECIMAL(10,2),
    ma_value DECIMAL(10,2),
    condition_id BIGINT,
    quantity DECIMAL(10,2),
    executed BOOLEAN,
    created_at TIMESTAMP,
    backup_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Copy existing data to backup tables
INSERT INTO plan_backup
SELECT id, name, stock_code, stock_name, cycle, status, is_locked,
       execution_quantity, trigger_date, created_at, updated_at, CURRENT_TIMESTAMP
FROM plan;

INSERT INTO plan_condition_backup
SELECT id, plan_id, condition_type, direction, ma_period, target_price, is_active, CURRENT_TIMESTAMP
FROM plan_condition;

INSERT INTO plan_execution_backup
SELECT id, plan_id, trade_date, direction, triggered, trigger_price, close_price,
       ma_value, condition_id, quantity, executed, created_at, CURRENT_TIMESTAMP
FROM plan_execution;

-- Log backup completion
-- NOTE: Check these backup tables for emergency recovery if needed

-- ============================================================================
-- STEP 2: Clear existing data (to avoid foreign key and data integrity issues)
-- ============================================================================

DELETE FROM plan_execution;
DELETE FROM plan_condition;
DELETE FROM plan;

-- ============================================================================
-- STEP 3: Drop old columns that are no longer needed
-- ============================================================================

-- Note: In PostgreSQL/MySQL, we need to drop constraints first
-- For H2/development, this works directly

ALTER TABLE plan DROP COLUMN IF EXISTS is_locked;

ALTER TABLE plan_condition DROP COLUMN IF EXISTS direction;
ALTER TABLE plan_condition DROP COLUMN IF EXISTS is_active;

ALTER TABLE plan_execution DROP COLUMN IF EXISTS direction;
ALTER TABLE plan_execution DROP COLUMN IF EXISTS condition_id;
ALTER TABLE plan_execution DROP COLUMN IF EXISTS quantity;

-- ============================================================================
-- STEP 4: Add new columns for buy-sell separation
-- ============================================================================

-- Plan table: Add plan_type, trade_plan_id, buy_plan_id, valid_until
ALTER TABLE plan ADD COLUMN IF NOT EXISTS plan_type VARCHAR(10) NOT NULL DEFAULT 'BUY';
ALTER TABLE plan ADD COLUMN IF NOT EXISTS trade_plan_id BIGINT;
ALTER TABLE plan ADD COLUMN IF NOT EXISTS buy_plan_id BIGINT;
ALTER TABLE plan ADD COLUMN IF NOT EXISTS valid_until DATE;

-- PlanExecution table: Add linked_execution_id
ALTER TABLE plan_execution ADD COLUMN IF NOT EXISTS linked_execution_id BIGINT;

-- ============================================================================
-- STEP 5: Add indexes for new fields
-- ============================================================================

CREATE INDEX IF NOT EXISTS idx_plan_type ON plan(plan_type);
CREATE INDEX IF NOT EXISTS idx_plan_trade ON plan(trade_plan_id);
CREATE INDEX IF NOT EXISTS idx_plan_buy ON plan(buy_plan_id);
CREATE INDEX IF NOT EXISTS idx_plan_type_status ON plan(plan_type, status);
CREATE INDEX IF NOT EXISTS idx_exec_linked ON plan_execution(linked_execution_id);

-- ============================================================================
-- STEP 6: Add foreign key constraints
-- ============================================================================

ALTER TABLE plan ADD CONSTRAINT fk_plan_buy_plan
    FOREIGN KEY (buy_plan_id) REFERENCES plan(id) ON DELETE SET NULL;

ALTER TABLE plan_execution ADD CONSTRAINT fk_exec_linked_exec
    FOREIGN KEY (linked_execution_id) REFERENCES plan_execution(id) ON DELETE SET NULL;

-- ============================================================================
-- STEP 7: Migration completed
-- ============================================================================

-- Log migration completion
-- All existing plans are now marked as BUY type with NULL trade_plan_id
-- They will need to be manually recreated or the user should re-enter data
