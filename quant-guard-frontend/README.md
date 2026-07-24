# QuantGuard Frontend

QuantGuard Frontend is the Vue 3 user interface for the QuantGuard local simulation and portfolio risk-control system.

This frontend is for local simulated trading workflows only. It does not connect to real brokers, real trading accounts, or live market execution.

## Tech Stack

- Vue 3
- TypeScript
- Vite
- Vue Router
- Pinia
- Axios
- Element Plus
- ESLint
- Prettier

## Requirements

- Node.js 24 or newer is currently used in this workspace.
- The backend runs from `../quant-guard-backend`.
- The backend default port is `8080`.

## Install

```powershell
npm install
```

## Start Development

Start the backend first:

```powershell
cd ..\quant-guard-backend
.\mvnw.cmd spring-boot:run
```

Start the frontend:

```powershell
cd ..\quant-guard-frontend
npm run dev
```

Default frontend URL:

```text
http://localhost:5173
```

## API Configuration

The frontend uses:

```text
VITE_API_BASE_URL=/api
```

During local development, Vite proxies `/api` to:

```text
http://localhost:8080
```

Commit `.env.example`. Do not commit real secrets or machine-specific credentials.

## Scripts

```powershell
npm run dev
npm run type-check
npm run lint
npm run build
npm run preview
```

## Current Scope

Implemented:

- main app bootstrap;
- Element Plus integration;
- Vue Router routes;
- Pinia stores;
- Axios response unwrapping for backend `{ success, message, data }`;
- main layout with sidebar and top bar;
- placeholder pages for current and future modules;
- dashboard summary using existing list APIs;
- simulation account list and creation;
- investment portfolio list, account filter, creation, and current selection;
- portfolio position list for the selected current portfolio;
- typed API shells for accounts, portfolios, and positions.

Not implemented yet:

- simulated order form;
- order record page;
- trade record page;
- cash ledger page;
- risk management page;
- real-time quotes;
- floating P/L calculations.
