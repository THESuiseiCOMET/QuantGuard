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
