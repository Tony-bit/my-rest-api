-- Flyway Migration: V4__stock_indicator_monitor.sql
-- Description: Add stock indicator monitor tables for KDJ/MACD signal monitoring
-- Author: auto-generated
-- Date: 2026-05-31

-- ============================================================================
-- TABLE 1: stock_monitor_config
-- Singleton row for Xueqiu cookie and WeChat test account configuration
-- ============================================================================

CREATE TABLE IF NOT EXISTS stock_monitor_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    -- Xueqiu configuration
    xueqiu_cookie TEXT,
    xueqiu_configured BOOLEAN NOT NULL DEFAULT FALSE,
    -- WeChat test account configuration
    wechat_app_id VARCHAR(50),
    wechat_app_secret VARCHAR(100),
    wechat_template_id VARCHAR(100),
    wechat_open_id VARCHAR(100),
    wechat_configured BOOLEAN NOT NULL DEFAULT FALSE,
    -- Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- ============================================================================
-- TABLE 2: stock_watch
-- Monitored stocks with observation state
-- ============================================================================

CREATE TABLE IF NOT EXISTS stock_watch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stock_code VARCHAR(10) NOT NULL,
    stock_name VARCHAR(100),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    signal_state VARCHAR(20) NOT NULL DEFAULT 'NONE',
    watch_start_date DATE,
    last_signal_type VARCHAR(20),
    last_signal_date DATE,
    last_checked_at TIMESTAMP NULL,
    last_check_status VARCHAR(30),
    last_check_message VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_stock_watch_code UNIQUE (stock_code)
);

CREATE INDEX IF NOT EXISTS idx_sw_enabled ON stock_watch(enabled);
CREATE INDEX IF NOT EXISTS idx_sw_signal_state ON stock_watch(signal_state);

-- ============================================================================
-- TABLE 3: stock_indicator_snapshot
-- Daily K-line indicator values (KDJ, MACD) per stock and trade date
-- ============================================================================

CREATE TABLE IF NOT EXISTS stock_indicator_snapshot (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stock_code VARCHAR(10) NOT NULL,
    trade_date DATE NOT NULL,
    open_price DECIMAL(18,4),
    high_price DECIMAL(18,4),
    low_price DECIMAL(18,4),
    close_price DECIMAL(18,4) NOT NULL,
    dif DECIMAL(18,6),
    dea DECIMAL(18,6),
    macd DECIMAL(18,6),
    kdjk DECIMAL(18,6),
    kdjd DECIMAL(18,6),
    kdjj DECIMAL(18,6),
    source_timestamp BIGINT,
    last_checked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_stock_snapshot_stock_date UNIQUE (stock_code, trade_date)
);

CREATE INDEX IF NOT EXISTS idx_sis_trade_date ON stock_indicator_snapshot(trade_date);
CREATE INDEX IF NOT EXISTS idx_sis_stock_code ON stock_indicator_snapshot(stock_code);

-- ============================================================================
-- TABLE 4: stock_signal_log
-- Signal detection audit log (allows duplicates for repeated checks)
-- ============================================================================

CREATE TABLE IF NOT EXISTS stock_signal_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    stock_code VARCHAR(10) NOT NULL,
    trade_date DATE NOT NULL,
    signal_type VARCHAR(20) NOT NULL,
    trigger_source VARCHAR(20) NOT NULL,
    close_price DECIMAL(18,4) NOT NULL,
    j_today DECIMAL(18,6),
    j_yesterday DECIMAL(18,6),
    macd_today DECIMAL(18,6),
    macd_yesterday DECIMAL(18,6),
    evaluation_summary VARCHAR(500),
    notification_status VARCHAR(30) NOT NULL DEFAULT 'NOT_SENT',
    notification_attempts INT NOT NULL DEFAULT 0,
    notification_sent_at TIMESTAMP NULL,
    notification_failure_reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ssl_stock_date_type ON stock_signal_log(stock_code, trade_date, signal_type);
CREATE INDEX IF NOT EXISTS idx_ssl_created_at ON stock_signal_log(created_at);
CREATE INDEX IF NOT EXISTS idx_ssl_notification_status ON stock_signal_log(notification_status);

-- ============================================================================
-- SEED DATA: Initial WeChat test account configuration
-- ============================================================================

INSERT INTO stock_monitor_config (xueqiu_cookie, xueqiu_configured, wechat_app_id, wechat_app_secret, wechat_template_id, wechat_open_id, wechat_configured, created_at, updated_at)
VALUES (NULL, FALSE, 'wx06680d7f6fdcd0da', '0fa34fb71e4d1c198b784f230583b468', '239zHlPaAORIosFh8sOOqVsaRGF3JPQgvgW5WEjSQpI', 'oIvsX3XhNf21wWPWtF2SyOxmydek', TRUE, NOW(), NOW());
