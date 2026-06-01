-- Flyway Migration: V5__add_data_source.sql
-- Description: Add data_source column to stock_monitor_config for switching between Xueqiu and Tushare
-- Author: auto-generated
-- Date: 2026-05-31

ALTER TABLE stock_monitor_config
ADD COLUMN data_source VARCHAR(20) NOT NULL DEFAULT 'XUEQIU'
AFTER wechat_configured;
