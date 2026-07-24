-- QuantGuard migration V4: create portfolio_risk_config and simulated_order tables.
-- Purpose: stores basic portfolio risk limits and simulated orders before trade execution.
-- Objects: creates portfolio_risk_config, simulated_order, foreign keys, unique constraints, and indexes.
-- Execution order: run after V3__create_instrument_and_position_tables.sql.
-- Repeatable: safe to run multiple times because CREATE TABLE IF NOT EXISTS is used.
-- Data safety: this script does not drop tables, delete data, or overwrite existing rows.
-- Secrets: this script contains no database passwords, API keys, tokens, or private trading data.
-- Execution: run manually in MySQL; Flyway is not enabled in this project yet.
-- Business rule: order submission must pass risk checks before cash or position quantity is frozen.

USE quant_guard;

CREATE TABLE IF NOT EXISTS portfolio_risk_config (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    portfolio_id BIGINT NOT NULL COMMENT 'Owning investment_portfolio id',
    max_single_order_amount DECIMAL(19,4) NOT NULL COMMENT 'Maximum amount allowed for one order',
    max_position_ratio DECIMAL(10,6) NOT NULL COMMENT 'Reserved max position ratio for future market-value risk checks',
    allow_buy BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Whether buy orders are allowed',
    allow_sell BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Whether sell orders are allowed',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE or DISABLED',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last update time',
    version INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
    PRIMARY KEY (id),
    CONSTRAINT uk_portfolio_risk_config_portfolio UNIQUE (portfolio_id),
    CONSTRAINT fk_portfolio_risk_config_portfolio
        FOREIGN KEY (portfolio_id) REFERENCES investment_portfolio (id),
    CONSTRAINT chk_portfolio_risk_config_max_order CHECK (max_single_order_amount > 0),
    CONSTRAINT chk_portfolio_risk_config_position_ratio CHECK (max_position_ratio >= 0 AND max_position_ratio <= 1),
    CONSTRAINT chk_portfolio_risk_config_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Basic risk configuration for one investment portfolio';

CREATE TABLE IF NOT EXISTS simulated_order (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    order_no VARCHAR(40) NOT NULL COMMENT 'Unique simulated order number',
    account_id BIGINT NOT NULL COMMENT 'Owning simulation_account id',
    portfolio_id BIGINT NOT NULL COMMENT 'Owning investment_portfolio id',
    instrument_id BIGINT NOT NULL COMMENT 'Security instrument id',
    side VARCHAR(10) NOT NULL COMMENT 'BUY or SELL',
    order_type VARCHAR(20) NOT NULL COMMENT 'LIMIT',
    requested_quantity DECIMAL(19,6) NOT NULL COMMENT 'Requested order quantity',
    limit_price DECIMAL(19,4) NOT NULL COMMENT 'Limit price entered by the user',
    frozen_amount DECIMAL(19,4) NOT NULL DEFAULT 0 COMMENT 'Cash frozen for BUY orders',
    frozen_quantity DECIMAL(19,6) NOT NULL DEFAULT 0 COMMENT 'Position quantity frozen for SELL orders',
    status VARCHAR(20) NOT NULL COMMENT 'OPEN, REJECTED, FILLED, or CANCELED',
    rejection_reason VARCHAR(255) NULL COMMENT 'Reason for rejected order when saved',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last update time',
    version INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version',
    PRIMARY KEY (id),
    CONSTRAINT uk_simulated_order_order_no UNIQUE (order_no),
    KEY idx_simulated_order_account_id (account_id),
    KEY idx_simulated_order_portfolio_id (portfolio_id),
    KEY idx_simulated_order_instrument_id (instrument_id),
    CONSTRAINT fk_simulated_order_account
        FOREIGN KEY (account_id) REFERENCES simulation_account (id),
    CONSTRAINT fk_simulated_order_portfolio
        FOREIGN KEY (portfolio_id) REFERENCES investment_portfolio (id),
    CONSTRAINT fk_simulated_order_instrument
        FOREIGN KEY (instrument_id) REFERENCES security_instrument (id),
    CONSTRAINT chk_simulated_order_side CHECK (side IN ('BUY', 'SELL')),
    CONSTRAINT chk_simulated_order_type CHECK (order_type IN ('LIMIT')),
    CONSTRAINT chk_simulated_order_status CHECK (status IN ('OPEN', 'REJECTED', 'FILLED', 'CANCELED')),
    CONSTRAINT chk_simulated_order_quantity CHECK (requested_quantity > 0),
    CONSTRAINT chk_simulated_order_limit_price CHECK (limit_price > 0),
    CONSTRAINT chk_simulated_order_frozen_amount CHECK (frozen_amount >= 0),
    CONSTRAINT chk_simulated_order_frozen_quantity CHECK (frozen_quantity >= 0)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Simulated order accepted after basic risk checks';
