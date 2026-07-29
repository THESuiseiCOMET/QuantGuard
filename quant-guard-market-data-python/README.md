# QuantGuard Market Data Python

Token-free historical market-data adapter for QuantGuard V1.1.

## Data routing

- Stock and ETF metadata/catalog: BaoStock
- Index catalog: AKShare `stock_zh_index_spot_sina`
- A-share stock daily bars: BaoStock
- ETF daily bars: AKShare `fund_etf_hist_sina`
- Index daily bars: AKShare `stock_zh_index_daily`

The service does not access the QuantGuard database.

BaoStock and AKShare volume is normalized with `volume / 100` into `volume_lots`. Upstream amount is kept in CNY yuan as `amount_cny`; the Sina index endpoint has no amount field, so index bars currently use `0`. The first available bar keeps `previous_close`, `change`, and `percentage_change` null when no earlier close exists.

The stock/ETF/index catalog uses a six-hour in-process cache. The first catalog load requires upstream network access, and restarting the service rebuilds the cache. If the upstream source is unavailable before the first successful load, catalog search can fail with a structured availability error.

## Run

```powershell
py -3.11 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8001 --workers 1
```

BaoStock uses process-wide session state, so the first version must run with one worker.

From the monorepo root, the Windows development helper starts this service, Spring Boot, and Vue together:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-dev.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\stop-dev.ps1
```

The helper is for development and demos. It does not install dependencies, modify MySQL, store tokens, or create a desktop application.

## API

```text
GET  /health
POST /v1/instruments/lookup
POST /v1/instruments/search
POST /v1/daily-bars
```

Canonical symbols use `XXXXXX.SH` or `XXXXXX.SZ`.

## Tests

Default tests mock all upstream calls:

```powershell
.\.venv\Scripts\python.exe -m pytest
.\.venv\Scripts\python.exe -m compileall app
```

Manual network smoke tests are opt-in:

```powershell
$env:RUN_MARKET_DATA_SMOKE="1"
.\.venv\Scripts\python.exe -m pytest -m live -s
```
