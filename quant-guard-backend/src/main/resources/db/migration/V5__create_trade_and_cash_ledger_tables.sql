-- QuantGuard migration V5: create simulated_trade and cash_ledger tables.
-- Purpose: stores full-fill simulated trades and cash movement ledger entries.
-- Objects: creates simulated_trade, cash_ledger, foreign keys, unique constraints, and indexes.
-- Execution order: run after V4__create_risk_config_and_simulated_order_tables.sql.
-- Repeatable: safe to run multiple times because CREATE TABLE IF NOT EXISTS is used.
-- Data safety: this script does not drop tables, delete data, or overwrite existing rows.
-- Secrets: this script contains no database passwords, API keys, tokens, or private trading data.
-- Execution: run manually in MySQL; Flyway is not enabled in this project yet.
-- Business rule: this is local simulated execution, not real exchange matching or broker trading.

USE quant_guard;

CREATE TABLE IF NOT EXISTS simulated_trade (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    trade_no VARCHAR(40) NOT NULL COMMENT 'Unique simulated trade number',
    order_id BIGINT NOT NULL COMMENT 'Owning simulated_order id; one trade per order in V5',
    account_id BIGINT NOT NULL COMMENT 'Owning simulation_account id',
    portfolio_id BIGINT NOT NULL COMMENT 'Owning investment_portfolio id',
    instrument_id BIGINT NOT NULL COMMENT 'Security instrument id',
    side VARCHAR(10) NOT NULL COMMENT 'BUY or SELL',
    quantity DECIMAL(19,6) NOT NULL COMMENT 'Executed quantity',
    execution_price DECIMAL(19,4) NOT NULL COMMENT 'Local simulated execution price from order limit_price',
    trade_amount DECIMAL(19,4) NOT NULL COMMENT 'Executed trade amount',
    executed_at DATETIME(6) NOT NULL COMMENT 'Execution time',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time',
    PRIMARY KEY (id),
    CONSTRAINT uk_simulated_trade_trade_no UNIQUE (trade_no),
    CONSTRAINT uk_simulated_trade_order_id UNIQUE (order_id),
    KEY idx_simulated_trade_account_id (account_id),
    KEY idx_simulated_trade_portfolio_id (portfolio_id),
    KEY idx_simulated_trade_instrument_id (instrument_id),
    CONSTRAINT fk_simulated_trade_order
        FOREIGN KEY (order_id) REFERENCES simulated_order (id),
    CONSTRAINT fk_simulated_trade_account
        FOREIGN KEY (account_id) REFERENCES simulation_account (id),
    CONSTRAINT fk_simulated_trade_portfolio
        FOREIGN KEY (portfolio_id) REFERENCES investment_portfolio (id),
    CONSTRAINT fk_simulated_trade_instrument
        FOREIGN KEY (instrument_id) REFERENCES security_instrument (id),
    CONSTRAINT chk_simulated_trade_side CHECK (side IN ('BUY', 'SELL')),
    CONSTRAINT chk_simulated_trade_quantity CHECK (quantity > 0),
    CONSTRAINT chk_simulated_trade_execution_price CHECK (execution_price > 0),
    CONSTRAINT chk_simulated_trade_amount CHECK (trade_amount > 0)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Full-fill simulated trade record';

CREATE TABLE IF NOT EXISTS cash_ledger (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    account_id BIGINT NOT NULL COMMENT 'Owning simulation_account id',
    portfolio_id BIGINT NOT NULL COMMENT 'Owning investment_portfolio id',
    order_id BIGINT NOT NULL COMMENT 'Related simulated_order id',
    trade_id BIGINT NULL COMMENT 'Related simulated_trade id; null for cash-only order cancellation',
    entry_type VARCHAR(30) NOT NULL COMMENT 'BUY_SETTLEMENT, SELL_PROCEEDS, or BUY_CANCEL_RELEASE',
    available_cash_delta DECIMAL(19,4) NOT NULL COMMENT 'Change in available cash',
    frozen_cash_delta DECIMAL(19,4) NOT NULL COMMENT 'Change in frozen cash',
    available_cash_after DECIMAL(19,4) NOT NULL COMMENT 'Available cash after this ledger entry',
    frozen_cash_after DECIMAL(19,4) NOT NULL COMMENT 'Frozen cash after this ledger entry',
    description VARCHAR(255) NULL COMMENT 'Ledger description',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time',
    PRIMARY KEY (id),
    KEY idx_cash_ledger_account_id (account_id),
    KEY idx_cash_ledger_portfolio_id (portfolio_id),
    KEY idx_cash_ledger_order_id (order_id),
    KEY idx_cash_ledger_trade_id (trade_id),
    CONSTRAINT fk_cash_ledger_account
        FOREIGN KEY (account_id) REFERENCES simulation_account (id),
    CONSTRAINT fk_cash_ledger_portfolio
        FOREIGN KEY (portfolio_id) REFERENCES investment_portfolio (id),
    CONSTRAINT fk_cash_ledger_order
        FOREIGN KEY (order_id) REFERENCES simulated_order (id),
    CONSTRAINT fk_cash_ledger_trade
        FOREIGN KEY (trade_id) REFERENCES simulated_trade (id),
    CONSTRAINT chk_cash_ledger_entry_type CHECK (entry_type IN ('BUY_SETTLEMENT', 'SELL_PROCEEDS', 'BUY_CANCEL_RELEASE')),
    CONSTRAINT chk_cash_ledger_available_after CHECK (available_cash_after >= 0),
    CONSTRAINT chk_cash_ledger_frozen_after CHECK (frozen_cash_after >= 0)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Cash movement ledger for simulated trading';
