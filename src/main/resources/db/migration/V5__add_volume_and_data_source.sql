-- Flyway Migration: V5__add_volume_and_data_source.sql
-- Description: Add volume column to stock_indicator_snapshot and data_source to stock_monitor_config
-- Date: 2026-06-01

-- Add volume column to stock_indicator_snapshot
ALTER TABLE stock_indicator_snapshot
ADD COLUMN IF NOT EXISTS volume DECIMAL(18,2) AFTER kdjj;

-- Add data_source column to stock_monitor_config
ALTER TABLE stock_monitor_config
ADD COLUMN IF NOT EXISTS data_source VARCHAR(20) NOT NULL DEFAULT 'XUEQIU' AFTER wechat_open_id;
