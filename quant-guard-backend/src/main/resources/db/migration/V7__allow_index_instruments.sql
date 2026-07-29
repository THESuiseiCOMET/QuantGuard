-- QuantGuard migration V7: allow index instruments in the security catalog.
-- Purpose: expands security_instrument.asset_type from STOCK/ETF to STOCK/ETF/INDEX.
-- Object changed: replaces chk_security_instrument_asset_type on security_instrument.
-- Execution order: run after V6__add_historical_market_data.sql.
-- Prerequisite: existing asset_type values must be STOCK, ETF, or INDEX.
-- Repeatable: safe to run multiple times; the named check constraint is recreated each time.
-- Data safety: this script preserves all tables and rows and does not rewrite asset_type values.
-- Secrets: this script contains no passwords, tokens, API keys, or private trading data.
-- Execution: run manually in MySQL; Flyway is not enabled in this project yet.

USE quant_guard;

SET @asset_type_constraint_exists = (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'security_instrument'
      AND constraint_name = 'chk_security_instrument_asset_type'
      AND constraint_type = 'CHECK'
);

SET @drop_asset_type_constraint_sql = IF(
    @asset_type_constraint_exists > 0,
    'ALTER TABLE security_instrument DROP CHECK chk_security_instrument_asset_type',
    'SELECT 1'
);

PREPARE drop_asset_type_constraint_statement FROM @drop_asset_type_constraint_sql;
EXECUTE drop_asset_type_constraint_statement;
DEALLOCATE PREPARE drop_asset_type_constraint_statement;

ALTER TABLE security_instrument
    ADD CONSTRAINT chk_security_instrument_asset_type
    CHECK (asset_type IN ('STOCK', 'ETF', 'INDEX'));
