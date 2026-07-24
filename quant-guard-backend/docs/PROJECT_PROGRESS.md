# QuantGuard Backend Project Progress

This document records durable backend milestones for QuantGuard so the project can be reviewed, restored, and continued across sessions.

## Current Scope

QuantGuard V1 is a local simulation trading and risk-control backend. It does not connect to real brokers, automate real-money trading, scrape trading clients, or place live orders.

Frontend development is intentionally deferred until the user resumes and confirms the next learning step.

## Database Migration Order

1. `src/main/resources/db/create_database.sql`
   - Creates the `quant_guard` database.
   - Must be executed manually before table migrations.

2. `src/main/resources/db/migration/V1__create_simulation_account.sql`
   - Creates the `simulation_account` table.
   - Uses `USE quant_guard;`.

3. `src/main/resources/db/migration/V2__create_investment_portfolio.sql`
   - Creates the `investment_portfolio` table.
   - Uses `USE quant_guard;`.

4. `src/main/resources/db/migration/V3__create_instrument_and_position_tables.sql`
   - Creates the `security_instrument` and `portfolio_position` tables.
   - Uses `USE quant_guard;`.

5. `src/main/resources/db/migration/V4__create_risk_config_and_simulated_order_tables.sql`
   - Creates the `portfolio_risk_config` and `simulated_order` tables.
   - Uses `USE quant_guard;`.

6. `src/main/resources/db/migration/V5__create_trade_and_cash_ledger_tables.sql`
   - Creates the `simulated_trade` and `cash_ledger` tables.
   - Uses `USE quant_guard;`.

7. `src/main/resources/db/migration/V5_1__harden_cash_ledger.sql`
   - Extends `cash_ledger.entry_type` with `BUY_ORDER_FREEZE`.
   - Adds `uk_cash_ledger_order_entry_type`.
   - Uses `USE quant_guard;`.
   - Must be executed once after V5; this ALTER TABLE script is not repeatable.

## Completed Milestones

### Initial Backend And Simulation Accounts

- Commit: `497a167 feat: initialize QuantGuard backend with simulation accounts`
- Result: Spring Boot backend, unified response wrapper, health checks, database health check, simulation account module.

### Investment Portfolio Module

- Commit: `5941f3e feat: add investment portfolio module`
- Result: investment portfolios belong to simulation accounts, enforce LONG_TERM and SHORT_TERM isolation, and expose basic create/list/detail APIs.

### API Validation Hardening And Baseline Tests

- Commit: `2393ee9 fix: harden API validation and add baseline tests`
- Result:
  - Invalid JSON and invalid enum values return HTTP 400.
  - Non-numeric path parameters return HTTP 400.
  - Account and portfolio names are normalized with `trim()` before duplicate checks and persistence.
  - Portfolio description is normalized with `trim()` when present.
  - Baseline tests no longer depend on a real local MySQL password.
- Validation:
  - `.\mvnw.cmd test`
  - Tests run: 19
  - Failures: 0
  - Errors: 0

### Security Instruments And Read-Only Positions

- Commit: `7239dce feat: add security instruments and read-only positions`
- Result:
  - Added `security_instrument` for normalized tradable instruments.
  - Added `portfolio_position` for read-only holdings under portfolios.
  - Instrument symbols and currencies are normalized to uppercase.
  - Position write APIs are intentionally not exposed; future simulated trade execution must update positions transactionally.
- Validation:
  - `.\mvnw.cmd test`
  - Tests run: 30
  - Failures: 0
  - Errors: 0

### Portfolio Risk Checks And Simulated Orders

- Commit: `af5ebdb feat: add portfolio risk checks and simulated orders`
- Hardening commit: `17c24f6 fix: harden simulated order amount and rollback checks`
- Result:
  - Added portfolio risk config for max single order amount, buy/sell switches, and reserved position ratio.
  - Added simulated LIMIT order submission.
  - System order amount precision is 4 decimal places.
  - Orders with raw amount below `0.0001` are rejected before cash or position freeze.
  - BUY orders freeze account cash after risk checks.
  - SELL orders freeze available position quantity after risk checks.
  - Cash and position freeze updates use MyBatis-Plus optimistic locking through `@Version`.
  - Simulated order insert result is checked after freeze; insert failure throws an exception so the transaction can roll back database updates.
  - Failed risk checks do not create `simulated_order`.
  - Failed risk checks do not mutate cash or position quantities.
  - `maxPositionRatio` is reserved for future market-value risk checks and is not enforced in V4 because there is no real market data or reliable market value.
  - V4 active risk checks are `allowBuy`, `allowSell`, `maxSingleOrderAmount`, available cash, and available position quantity.
  - `OrderStatus.REJECTED` is reserved for future audit features and is not persisted by the V4 API flow.
- Validation:
  - `.\mvnw.cmd clean test`
  - Tests run: 61
  - Failures: 0
  - Errors: 0

### Transactional Simulated Trade Execution

- Commit: `be1b49a feat: add transactional simulated trade execution`
- Result:
  - Added simulated full-fill trade execution for OPEN LIMIT orders.
  - Added order cancellation for OPEN orders.
  - Added `simulated_trade` and `cash_ledger` records.
  - BUY execution uses the existing order `frozenAmount` as trade amount.
  - SELL execution reduces frozen position quantity and increases available cash.
  - BUY cancellation releases frozen cash and creates cash ledger.
  - SELL cancellation restores available position quantity without fake cash ledger.
  - `SimulatedOrder.version` now uses MyBatis-Plus optimistic locking.
  - Execution and cancellation are transactional service methods.
  - This is local simulation only, not real exchange matching or broker trading.
- Validation:
  - `.\mvnw.cmd clean test`
  - Tests run: 77
  - Failures: 0
  - Errors: 0
  - MySQL/Testcontainers transaction integration tests: not executed in this milestone.

### V5 Cash Ledger Audit And Transaction Hardening

- Commit: `609a6c2 fix: complete cash ledger audit and verify transactions`
- Final hardening commit: `258256d test: fix integration CI container setup`
- Result:
  - BUY order creation now records `BUY_ORDER_FREEZE` cash ledger entries in the same transaction as risk checks, account cash freeze, and `simulated_order` creation.
  - SELL order creation still does not create cash ledger entries because SELL submission only freezes position quantity.
  - Cash ledger insert failures and duplicate ledger entries throw business exceptions and rely on the surrounding transaction to roll back account, order, trade, or position changes.
  - Added V5.1 manual migration for `BUY_ORDER_FREEZE` and `(order_id, entry_type)` uniqueness.
  - Added Testcontainers MySQL integration tests for rollback and concurrency behavior without connecting to local MySQL or `application-local.yaml`.
  - Testcontainers MySQL 8 temporary database starts successfully in GitHub Actions.
  - V1, V2, V3, V4, V5, and V5.1 database scripts execute successfully in the temporary MySQL database.
  - BUY order cash ledger failure rolls back account cash and simulated order creation.
  - BUY execution failure rolls back order status, cash, position, trade, and cash ledger changes.
  - SELL execution failure rolls back order status, cash, position, trade, and cash ledger changes.
  - Concurrent execution of the same order allows at most one successful operation.
  - Concurrent execute and cancel operations on the same order allow at most one successful operation.
  - V5 transaction closure has been verified remotely.
- Validation:
  - `.\mvnw.cmd clean test`
  - Tests run: 89
  - Failures: 0
  - Errors: 0
  - Skipped: 0
  - `.\mvnw.cmd -P integration verify`
  - Unit/Web tests run before failsafe: 89
  - Testcontainers integration tests discovered: 6
  - Local Docker availability: unavailable on this machine, so integration tests were skipped locally.
  - Local integration command result: build success with 6 skipped integration tests.
  - Remote workflow: `QuantGuard Backend Integration CI #2`
  - Remote commit: `258256d`
  - Remote branch: `main`
  - Remote job: `integration-test`
  - Remote status: Success
  - Remote Testcontainers MySQL integration tests run: 6
  - Remote Failures: 0
  - Remote Errors: 0
  - Remote Skipped: 0
  - Remote integration tests were not skipped.
- Remaining boundaries:
  - QuantGuard is still a local simulated trading system.
  - V5 is not real exchange matching.
  - V5 does not connect to real market data or real brokers.
  - `V5_1__harden_cash_ledger.sql` still needs to be executed once manually in the user's actual MySQL database after duplicate ledger checks pass.
  - Historical BUY orders are not automatically backfilled with `BUY_ORDER_FREEZE` cash ledger records.

## Main APIs

- `GET /api/health`
- `GET /api/database/health`
- `POST /api/accounts`
- `GET /api/accounts`
- `GET /api/accounts/{id}`
- `POST /api/portfolios`
- `GET /api/portfolios`
- `GET /api/portfolios/{id}`
- `GET /api/accounts/{accountId}/portfolios`
- `POST /api/instruments`
- `GET /api/instruments`
- `GET /api/instruments/{id}`
- `GET /api/portfolios/{portfolioId}/positions`
- `GET /api/positions/{id}`
- `POST /api/risk-configs`
- `GET /api/risk-configs/{id}`
- `GET /api/portfolios/{portfolioId}/risk-config`
- `POST /api/orders`
- `GET /api/orders`
- `GET /api/orders/{id}`
- `GET /api/portfolios/{portfolioId}/orders`
- `POST /api/orders/{id}/execute`
- `POST /api/orders/{id}/cancel`
- `GET /api/trades`
- `GET /api/trades/{id}`
- `GET /api/orders/{orderId}/trades`
- `GET /api/portfolios/{portfolioId}/trades`
- `GET /api/accounts/{accountId}/cash-ledger`

## Current Limits

- No frontend yet.
- No public position write API.
- No partial fills.
- No market orders.
- No fees or taxes.
- No real market data.
- No real broker integration.
- No backtesting.
- No profit, market value, or real-time total asset calculation.

## Next Recommended Backend Milestones

1. Transactional simulated trade execution, cash ledger, and trade records.
2. Order cancellation and frozen asset release.
3. Summary and query APIs.
