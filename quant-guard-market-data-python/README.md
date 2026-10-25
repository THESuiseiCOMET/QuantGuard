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

Code/name search is performed on demand; supplemental source catalogs use a six-hour in-process cache. Restarting the service rebuilds those caches. Upstream calls are bounded and failures return structured errors. See V1.3 reliability changes below.

## Run

```powershell
py -3.11 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8001 --workers 1
```

BaoStock uses process-wide session state, so the first version must run with one worker.

The stable CLI entry point supports loopback-only hosting, an explicit port, rotating JSON logs, and version output:

```powershell
.\.venv\Scripts\python.exe -m app.cli --host 127.0.0.1 --port 8001 --log-dir .\.runtime\logs
.\.venv\Scripts\python.exe -m app.cli --version
```

Non-loopback addresses are rejected. The packaged runtime keeps one worker because BaoStock uses process-wide session state.

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

Runtime dependencies are in `requirements.txt`; pytest and the TestClient HTTP dependency are isolated in `requirements-test.txt`. Default tests mock all upstream calls:

```powershell
.\.venv\Scripts\python.exe -m pip install -r requirements-test.txt
.\.venv\Scripts\python.exe -m pytest
.\.venv\Scripts\python.exe -m compileall app
```

Manual network smoke tests are opt-in:

```powershell
$env:RUN_MARKET_DATA_SMOKE="1"
.\.venv\Scripts\python.exe -m pytest -m live -s
```

## Windows onedir runtime

Install the locked build-only dependency into the existing virtual environment:

```powershell
.\.venv\Scripts\python.exe -m pip install -r requirements-test.txt -r requirements-build.txt
```

Build from the monorepo root:

```powershell
.\scripts\build-python-runtime.ps1
```

The maintained `quantguard-market-data.spec` produces:

```text
dist\desktop-runtime\market-data\
├─ quantguard-market-data.exe
└─ PyInstaller dependencies
```

The runtime does not read `.venv`, source files, or desktop PoC paths. It remains a console-enabled `onedir` build for diagnosability and is not a Windows installer.

## V1.3 reliability changes

Search now uses BaoStock server-side code/name filters instead of downloading its entire catalog. Enter a full six-digit code (optionally `.SH`/`.SZ`) or a name. Incomplete numeric input returns no results without starting a market-wide download. Sina index metadata supplements ambiguous codes and names. Slow AKShare stock/ETF catalogs are fallbacks only; primary listing dates, types and status are retained. Supplemental source catalogs are cached for six hours, and recent search metadata is bounded to 512 entries.

Each blocking vendor SDK operation runs in a short-lived spawned worker, with a hard deadline (BaoStock 8 seconds, AKShare 12 seconds). At most two workers run concurrently; timed-out workers are terminated and joined instead of accumulating hung threads. This is needed because the pinned SDK functions do not consistently expose request timeouts. Packaged CLI startup calls `multiprocessing.freeze_support()` before importing the app. Health checks do not start these workers or call the internet.

Stock daily bars fall back to AKShare on timeout, unavailable/malformed responses or empty primary data. ETF/index amount is optional: valid provided amounts are retained, missing/empty values use the existing `0` sentinel because H2's amount field is non-null. This means unknown amount, not an estimated traded value. A-share stock amount remains required. All rows are filtered to the requested dates; missing previous close remains null.

Java's default Python read timeout is 60 seconds, and the frontend catalog timeout is 65 seconds. Individual upstream operations time out first. Initial implicit sync/import now requests one year (`MARKET_DATA_INITIAL_LOOKBACK_YEARS`, valid 1–30), clamped to the historical floor/listing date; explicit start dates and full refresh remain available. Automatic sync is still disabled by default.

Python errors preserve TIMEOUT/availability codes and log the source failure. Real-network tests remain opt-in and now include an index plus cold-start search. The retained BaoStock version is 0.9.3: inspecting 0.9.4 showed API-key/VIP changes, not a timeout fix, so no blind dependency upgrade was made.


V1.3 源码精简、依赖分离、日志限额与验证结果见 [精简记录](../docs/v1.3/SIMPLIFICATION.md)。安装包和自包含运行时构建当前按用户要求暂停；源码验证与预览可继续使用。
