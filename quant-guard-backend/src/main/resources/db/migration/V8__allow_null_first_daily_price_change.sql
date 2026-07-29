-- QuantGuard migration V8: allow null first-day daily price change fields.
-- Purpose: preserves an unknown previous close instead of fabricating zero for the first available bar.
-- Object changed: instrument_daily_price previous_close, price_change, and percentage_change columns.
-- Execution order: run after V7__allow_index_instruments.sql.
-- Repeatable: safe to run multiple times because MODIFY COLUMN reapplies the same nullable definitions.
-- Data safety: this script does not delete rows or change existing daily price values.
-- Secrets: this script contains no passwords, tokens, API keys, or private trading data.
-- Execution: run manually in MySQL; Flyway is not enabled in this project yet.

USE quant_guard;

ALTER TABLE instrument_daily_price
    MODIFY COLUMN previous_close DECIMAL(19,4) NULL
        COMMENT 'Previous close price; null for the first available bar',
    MODIFY COLUMN price_change DECIMAL(19,4) NULL
        COMMENT 'Price change; null when previous close is unavailable',
    MODIFY COLUMN percentage_change DECIMAL(19,6) NULL
        COMMENT 'Percentage change; null when previous close is unavailable';
