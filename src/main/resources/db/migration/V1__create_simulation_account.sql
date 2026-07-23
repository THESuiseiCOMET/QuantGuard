-- QuantGuard migration V1: create simulation_account table.
-- Purpose: stores local simulated trading accounts for long-term investing and short-term trading.
-- Objects: creates the simulation_account table and its account name unique constraint.
-- Execution order: run after src/main/resources/db/create_database.sql.
-- Repeatable: safe to run multiple times because CREATE TABLE IF NOT EXISTS is used.
-- Data safety: this script does not drop tables, delete data, or overwrite existing rows.
-- Secrets: this script contains no database passwords, API keys, tokens, or private trading data.
-- Execution: run manually in MySQL; Flyway is not enabled in this project yet.

USE quant_guard;

CREATE TABLE IF NOT EXISTS simulation_account (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    account_name VARCHAR(64) NOT NULL COMMENT 'Unique simulated account name',
    account_type VARCHAR(20) NOT NULL COMMENT 'LONG_TERM or SHORT_TERM',
    initial_cash DECIMAL(19, 4) NOT NULL COMMENT 'Initial account cash',
    available_cash DECIMAL(19, 4) NOT NULL COMMENT 'Cash available for simulated trading',
    frozen_cash DECIMAL(19, 4) NOT NULL DEFAULT 0 COMMENT 'Cash frozen by pending simulated orders',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE or DISABLED',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last update time',
    version INT NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version reserved for future updates',
    PRIMARY KEY (id),
    CONSTRAINT uk_simulation_account_name UNIQUE (account_name),
    CONSTRAINT chk_simulation_account_type CHECK (account_type IN ('LONG_TERM', 'SHORT_TERM')),
    CONSTRAINT chk_simulation_account_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT chk_simulation_account_initial_cash CHECK (initial_cash >= 0),
    CONSTRAINT chk_simulation_account_available_cash CHECK (available_cash >= 0),
    CONSTRAINT chk_simulation_account_frozen_cash CHECK (frozen_cash >= 0)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Simulated trading account';
