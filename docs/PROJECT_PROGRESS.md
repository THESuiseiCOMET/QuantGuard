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

## Current Limits

- No frontend yet.
- No public position write API.
- No simulated order or trade execution yet.
- No real market data.
- No real broker integration.
- No backtesting.
- No profit, market value, or real-time total asset calculation.

## Next Recommended Backend Milestones

1. Security instruments and read-only portfolio positions.
2. Portfolio risk configuration and simulated orders.
3. Transactional simulated trade execution, cash ledger, and trade records.
4. Summary and query APIs.
