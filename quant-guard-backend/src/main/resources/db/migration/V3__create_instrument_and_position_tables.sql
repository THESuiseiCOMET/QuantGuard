-- QuantGuard migration V3: create security_instrument and portfolio_position tables.
-- Purpose: stores tradable instruments and read-only portfolio positions for the simulation flow.
-- Objects: creates security_instrument, portfolio_position, foreign keys, unique constraints, and indexes.
-- Execution order: run after V2__create_investment_portfolio.sql.
-- Repeatable: safe to run multiple times because CREATE TABLE IF NOT EXISTS is used.
-- Data safety: this script does not drop tables, delete data, or overwrite existing rows.
-- Secrets: this script contains no database passwords, API keys, tokens, or private trading data.
-- Execution: run manually in MySQL; Flyway is not enabled in this project yet.
-- Business rule: positions are updated only by future simulated trade execution services, not by public write APIs.

USE quant_guard;

CREATE TABLE IF NOT EXISTS security_instrument (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    symbol VARCHAR(32) NOT NULL COMMENT 'Normalized instrument symbol, such as 510300 or AAPL',
    exchange VARCHAR(20) NOT NULL COMMENT 'SSE, SZSE, BSE, HKEX, NASDAQ, or NYSE',
    instrument_name VARCHAR(128) NOT NULL COMMENT 'Instrument display name',
    asset_type VARCHAR(20) NOT NULL COMMENT 'STOCK or ETF',
    currency VARCHAR(3) NOT NULL COMMENT 'ISO-like currency code such as CNY, HKD, or USD',
    lot_size DECIMAL(19,6) NOT NULL COMMENT 'Minimum tradable quantity unit',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE or DISABLED',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last update time',
    PRIMARY KEY (id),
    CONSTRAINT uk_security_instrument_exchange_symbol UNIQUE (exchange, symbol),
    KEY idx_security_instrument_symbol (symbol),
    CONSTRAINT chk_security_instrument_exchange CHECK (exchange IN ('SSE', 'SZSE', 'BSE', 'HKEX', 'NASDAQ', 'NYSE')),
    CONSTRAINT chk_security_instrument_asset_type CHECK (asset_type IN ('STOCK', 'ETF')),
    CONSTRAINT chk_security_instrument_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT chk_security_instrument_lot_size CHECK (lot_size > 0)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Tradable security instrument catalog';

CREATE TABLE IF NOT EXISTS portfolio_position (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    portfolio_id BIGINT NOT NULL COMMENT 'Owning investment_portfolio id',
    instrument_id BIGINT NOT NULL COMMENT 'Held security_instrument id',
    total_quantity DECIMAL(19,6) NOT NULL DEFAULT 0 COMMENT 'Total held quantity',
    available_quantity DECIMAL(19,6) NOT NULL DEFAULT 0 COMMENT 'Quantity available to sell',
    frozen_quantity DECIMAL(19,6) NOT NULL DEFAULT 0 COMMENT 'Quantity frozen by pending sell orders',
    average_cost DECIMAL(19,4) NOT NULL DEFAULT 0 COMMENT 'Weighted average holding cost',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last update time',
    version INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version for future trade execution',
    PRIMARY KEY (id),
    CONSTRAINT uk_portfolio_position_portfolio_instrument UNIQUE (portfolio_id, instrument_id),
    KEY idx_portfolio_position_portfolio_id (portfolio_id),
    KEY idx_portfolio_position_instrument_id (instrument_id),
    CONSTRAINT fk_portfolio_position_portfolio
        FOREIGN KEY (portfolio_id) REFERENCES investment_portfolio (id),
    CONSTRAINT fk_portfolio_position_instrument
        FOREIGN KEY (instrument_id) REFERENCES security_instrument (id),
    CONSTRAINT chk_portfolio_position_total_quantity CHECK (total_quantity >= 0),
    CONSTRAINT chk_portfolio_position_available_quantity CHECK (available_quantity >= 0),
    CONSTRAINT chk_portfolio_position_frozen_quantity CHECK (frozen_quantity >= 0),
    CONSTRAINT chk_portfolio_position_average_cost CHECK (average_cost >= 0),
    CONSTRAINT chk_portfolio_position_quantity_balance CHECK (available_quantity + frozen_quantity <= total_quantity)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Read-only portfolio position maintained by simulated trade execution';
