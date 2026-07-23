-- QuantGuard migration V2: create investment_portfolio table.
-- Purpose: stores strategy and trading containers under a simulation account.
-- Objects: creates the investment_portfolio table, account foreign key, account/name unique constraint, and account index.
-- Execution order: run after V1__create_simulation_account.sql.
-- Repeatable: safe to run multiple times because CREATE TABLE IF NOT EXISTS is used.
-- Data safety: this script does not drop tables, delete data, or overwrite existing rows.
-- Secrets: this script contains no database passwords, API keys, tokens, or private trading data.
-- Execution: run manually in MySQL; Flyway is not enabled in this project yet.
-- Business rule: portfolio_type must match simulation_account.account_type; this cross-table rule is enforced in Service code.

USE quant_guard;

CREATE TABLE IF NOT EXISTS investment_portfolio (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key',
    account_id BIGINT NOT NULL COMMENT 'Owning simulation_account id',
    portfolio_name VARCHAR(64) NOT NULL COMMENT 'Portfolio name unique within one account',
    portfolio_type VARCHAR(20) NOT NULL COMMENT 'LONG_TERM or SHORT_TERM',
    description VARCHAR(255) NULL COMMENT 'Optional portfolio description',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE or DISABLED',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last update time',
    PRIMARY KEY (id),
    CONSTRAINT uk_investment_portfolio_account_name UNIQUE (account_id, portfolio_name),
    KEY idx_investment_portfolio_account_id (account_id),
    CONSTRAINT fk_investment_portfolio_account
        FOREIGN KEY (account_id) REFERENCES simulation_account (id),
    CONSTRAINT chk_investment_portfolio_type CHECK (portfolio_type IN ('LONG_TERM', 'SHORT_TERM')),
    CONSTRAINT chk_investment_portfolio_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Investment portfolio under a simulated account';
