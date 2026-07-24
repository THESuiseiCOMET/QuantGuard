-- QuantGuard migration V5.1: harden cash_ledger audit constraints.
-- Purpose: extends cash ledger entry types and prevents duplicate ledger entries for the same order action.
-- Objects: modifies cash_ledger CHECK constraint and adds uk_cash_ledger_order_entry_type.
-- Execution order: run once after V5__create_trade_and_cash_ledger_tables.sql.
-- Repeatable: not repeatable; MySQL cannot add the same named constraint repeatedly.
-- Data safety: this script does not drop tables, delete data, or overwrite existing rows.
-- Secrets: this script contains no database passwords, API keys, tokens, or private trading data.
-- Execution: run manually in MySQL; Flyway is not enabled in this project yet.

USE quant_guard;

ALTER TABLE cash_ledger
    DROP CHECK chk_cash_ledger_entry_type;

ALTER TABLE cash_ledger
    ADD CONSTRAINT chk_cash_ledger_entry_type
        CHECK (entry_type IN ('BUY_ORDER_FREEZE', 'BUY_SETTLEMENT', 'SELL_PROCEEDS', 'BUY_CANCEL_RELEASE'));

ALTER TABLE cash_ledger
    ADD CONSTRAINT uk_cash_ledger_order_entry_type UNIQUE (order_id, entry_type);
