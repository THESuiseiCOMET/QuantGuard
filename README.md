# QuantGuard

QuantGuard is a frontend and backend separated quantitative trading simulation and portfolio risk-control platform.

The project is currently a local simulation system. It does not connect to real brokers, place real orders, automate real accounts, or execute live trading.

## Project Structure

```text
QuantGuard/
├── .github/workflows/       # CI workflows for the monorepo
├── quant-guard-backend/     # Spring Boot backend
└── quant-guard-frontend/    # Vue 3 frontend
```

## Current Implementation

### Backend

The backend currently includes:

- health and database health checks;
- simulation accounts;
- investment portfolios;
- security instruments;
- read-only portfolio positions;
- portfolio risk configuration;
- simulated orders;
- simulated trades;
- cash ledger records;
- transaction-focused tests for order execution, cancellation, cash, holdings, and rollback behavior.

Detailed backend setup, database migration order, API notes, and validation commands are documented in [quant-guard-backend/README.md](quant-guard-backend/README.md).

### Frontend

The frontend is initialized with Vue 3, TypeScript, Vite, Vue Router, Pinia, Axios, and Element Plus.

Current frontend pages include:

- dashboard summary based on existing backend list APIs;
- simulation account list and creation;
- investment portfolio list, account filter, creation, and current selection;
- portfolio position list for the current selected portfolio;
- placeholder routes for orders, trades, cash ledger, risk management, and later modules.

## Tech Stack

Backend:

- Java 21
- Spring Boot
- Maven Wrapper
- MyBatis-Plus
- MySQL
- JUnit
- Testcontainers

Frontend:

- Vue 3
- TypeScript
- Vite
- Vue Router
- Pinia
- Axios
- Element Plus

## Quick Start

Run backend tests from the monorepo root:

```powershell
cd quant-guard-backend
.\mvnw.cmd test
```

Run backend integration tests when Docker is available:

```powershell
cd quant-guard-backend
.\mvnw.cmd -P integration verify
```

Start the backend after configuring the local MySQL profile described in the backend README:

```powershell
cd quant-guard-backend
.\mvnw.cmd spring-boot:run
```

Default local health endpoints:

```text
GET http://localhost:8080/api/health
GET http://localhost:8080/api/database/health
```

Start the frontend:

```powershell
cd quant-guard-frontend
npm install
npm run dev
```

Default frontend URL:

```text
http://localhost:5173
```

## Project Status

QuantGuard is in active development.

The backend core simulation loop is implemented through simulated accounts, portfolios, instruments, orders, trades, cash ledger records, and risk checks.

The frontend V1 foundation is in progress. The current implemented frontend scope covers dashboard, simulation accounts, investment portfolios, and positions. Simulated orders, trade records, cash ledger, and risk-management screens are planned for later frontend milestones.

## Safety Notice

QuantGuard is not a real trading system.

It does not support real brokerage access, real account automation, live market execution, credential harvesting, cookie or token scraping, or UI automation against brokerage clients.

Keep real passwords, API keys, tokens, private keys, broker credentials, account identifiers, logs, local configuration files, and private trading data out of Git.
