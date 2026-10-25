# QuantGuard 后端

QuantGuard（智衡量化交易与风控系统）后端是一个本地自用的 Spring Boot 模拟交易与风控系统。

当前阶段只做：

- 策略分析的后端基础；
- 模拟账户；
- 投资组合；
- 证券标的；
- A 股证券标的导入；
- 历史未复权日线行情同步；
- 只读持仓；
- 模拟订单、成交、资金流水和风控。

当前阶段不做：

- 前端页面；
- 真实券商连接；
- 真实资金交易；
- 自动实盘下单；
- Cookie、Token、客户端逆向或界面抓取；
- 真实行情接入；
- 实时行情；
- 分钟 K 线；
- 复权行情；
- 技术指标；
- 高频交易。

## 运行模式

后端保留两种互不替代的运行方式：

- 默认/MySQL 模式：继续使用现有 MySQL 配置和手动 V1-V8 迁移，Flyway 默认关闭；
- `desktop` Profile：使用 H2 File，不依赖 MySQL，并通过独立 Flyway 基线自动初始化。

桌面模式默认将文件写入：

```text
%APPDATA%\QuantGuard\
├─ data\
├─ logs\
├─ config\
└─ runtime\
```

可以使用 `QUANTGUARD_DATA_DIR` 覆盖根目录。路径解析优先使用该变量，其次使用 Windows `APPDATA`，最后安全降级到用户目录。数据库文件位于 `data\quantguard.mv.db`，日志滚动写入 `logs\`。不要将用户数据放入仓库或程序安装目录。

桌面模式手动启动：

```powershell
$env:SPRING_PROFILES_ACTIVE="desktop"
$env:QUANTGUARD_DATA_DIR="C:\path\to\your\data"
.\mvnw.cmd spring-boot:run
```

也可以从仓库根目录启动 Python、后端和前端：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-dev.ps1 -Desktop
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\stop-dev.ps1 -Desktop
```

当前只是浏览器开发模式的桌面运行底座，不包含 Tauri、Java Runtime、Python 可执行文件或 Windows 安装包。

## Windows 自包含后端目录

V1.2-B 使用 Java 21 `jpackage --type app-image` 将实际 Spring Boot 可执行 JAR 和 Java Runtime 组合为：

```text
dist\desktop-runtime\backend\
├─ QuantGuardBackend.exe
├─ app\
└─ runtime\
```

构建脚本会从 JAR manifest 读取真实 `Main-Class` 与 `Start-Class`，不会硬编码猜测业务主类：

```powershell
.\scripts\build-backend-runtime.ps1
```

启动器默认启用 `desktop` Profile，继续接收 Spring Boot 命令行参数，并继承 `QUANTGUARD_DATA_DIR`、`PYTHON_MARKET_DATA_BASE_URL` 等环境变量。数据库和日志仍写入用户数据目录，不写入 app-image。该目录不依赖系统 JDK，但当前不是 Tauri sidecar 或安装包。

## 本地数据库初始化

后端使用本地 MySQL 数据库：

```text
quant_guard
```

### 1. 创建数据库

先在 MySQL 中手动执行：

```sql
source src/main/resources/db/create_database.sql;
```

如果 MySQL 客户端不是从项目根目录打开，请使用该文件的完整路径。

### 2. 按顺序执行迁移脚本

默认/MySQL 模式不启用 Flyway，所以 `src/main/resources/db/migration/` 下的 SQL 是“手动执行、纳入 Git 管理”的版本化脚本。`desktop` Profile 使用独立的 `src/main/resources/db/desktop-migration/`，不会自动修改现有 MySQL 数据库。

请按顺序执行：

```sql
source src/main/resources/db/migration/V1__create_simulation_account.sql;
source src/main/resources/db/migration/V2__create_investment_portfolio.sql;
source src/main/resources/db/migration/V3__create_instrument_and_position_tables.sql;
source src/main/resources/db/migration/V4__create_risk_config_and_simulated_order_tables.sql;
source src/main/resources/db/migration/V5__create_trade_and_cash_ledger_tables.sql;
source src/main/resources/db/migration/V5_1__harden_cash_ledger.sql;
source src/main/resources/db/migration/V6__add_historical_market_data.sql;
source src/main/resources/db/migration/V7__allow_index_instruments.sql;
source src/main/resources/db/migration/V8__allow_null_first_daily_price_change.sql;
```

这些迁移脚本都包含：

```sql
USE quant_guard;
```

所以在数据库已创建后，也可以直接用 MySQL Workbench 打开完整脚本并执行。

`V5_1__harden_cash_ledger.sql` 是增量加固脚本：它会调整 `cash_ledger` 的合法流水类型，并新增 `(order_id, entry_type)` 唯一约束。该脚本应在 V5 后手动执行一次，不是可无限重复执行的脚本。

`V7__allow_index_instruments.sql` 会把证券类型约束扩展为 `STOCK`、`ETF` 和 `INDEX`。它不会修改或删除已有证券及交易数据，可以在已有数据库上重复执行。

`V8__allow_null_first_daily_price_change.sql` 允许第一条可用日线的昨收价、涨跌额和涨跌幅为空，避免用 `0` 伪造未知昨收。它不会删除或重算已有日线，可以重复执行。

执行 V5.1 前，先检查是否存在重复流水：

```sql
SELECT order_id, entry_type, COUNT(*) AS duplicate_count
FROM cash_ledger
GROUP BY order_id, entry_type
HAVING COUNT(*) > 1;
```

只有查询结果为空时，才执行 `V5_1__harden_cash_ledger.sql`。如果存在重复数据，不要自动删除或合并，先人工确认哪些流水应该保留。

## 本地配置

可提交的配置模板：

```text
src/main/resources/application-local.example.yaml
```

真实本地配置：

```text
src/main/resources/application-local.yaml
```

第一次配置时，把 example 文件复制为 `application-local.yaml`，然后填写你本机的 MySQL 用户名和密码。

注意：

- `application-local.yaml` 已被 `.gitignore` 忽略；
- 不要提交真实数据库密码；
- 不要提交 API Key、Token、私钥、Cookie；
- 不要提交真实交易数据或个人隐私数据。

## 行情数据配置

V1.3 仅支持 Python Provider，旧 Token 配置接口已移除；不需要任何行情 Token。自动同步只由显式启用配置控制。

通过本机 Python 服务获取无 Token 历史行情：

- 证券基础信息和类型：BaoStock；
- 证券目录搜索：BaoStock 股票与 ETF 目录，加上 AKShare 新浪指数目录；
- 普通 A 股历史日线：BaoStock；
- ETF 历史日线：AKShare `fund_etf_hist_sina`；
- 指数历史日线：AKShare `stock_zh_index_daily`，只读且禁止模拟下单。

BaoStock 与 AKShare 的成交量统一按 `volume / 100` 转换为 `volumeLots`，成交额以人民币元保存为 `amountCny`。新浪指数接口不提供成交额，因此指数日线当前使用 `0`。第一条可用日线没有前序收盘价时，昨收、涨跌额和涨跌幅保持为空。

证券搜索使用 BaoStock 服务端筛选，指数补充目录与按需备用目录使用六小时缓存；完整六位代码或名称可查询，不完整数字代码不会触发全量下载。上游调用超时会回收工作进程并返回明确错误。

先在 monorepo 的 `quant-guard-market-data-python` 目录启动单 worker 服务，再启动后端。Python 服务不访问 MySQL。

PowerShell 示例：

```powershell
$env:PYTHON_MARKET_DATA_BASE_URL="http://127.0.0.1:8001"
$env:MARKET_DATA_AUTO_SYNC_ENABLED="false" # Explicitly set true only to opt in
```

默认配置：

- `PYTHON_MARKET_DATA_BASE_URL` 默认 `http://127.0.0.1:8001`；
- Python 连接超时默认 3 秒，读取超时默认 60 秒；
- `MARKET_DATA_DEFAULT_START_DATE` 默认 `1990-01-01`；
- `MARKET_DATA_AUTO_SYNC_ENABLED` 默认 `false`；
- `MARKET_DATA_AUTO_SYNC_CRON` 默认工作日 18:00；
- 自动同步默认关闭。

Python 服务不可用不会阻止 Spring Boot 启动，只在实际行情调用时返回明确错误。当前范围支持按代码或名称搜索上交所、深交所普通 A 股、ETF 和指数；股票与 ETF 可参与模拟交易，指数仅支持只读未复权日线。北交所、港股、美股、分钟线、复权行情、技术指标、回测、策略和真实券商接入不在本阶段范围。

## 常用验证

运行测试：

```powershell
.\mvnw.cmd test
```

运行完整普通测试：

```powershell
.\mvnw.cmd clean test
```

运行 Testcontainers MySQL 集成测试：

```powershell
.\mvnw.cmd -P integration verify
```

集成测试使用临时 MySQL 容器和测试数据，不连接你的本地 `quant_guard`，也不读取 `application-local.yaml` 中的真实数据库密码。

启动后可访问：

```text
GET http://localhost:8080/api/health
GET http://localhost:8080/api/database/health
```

## 当前主要接口

健康检查：

```text
GET /api/health
GET /api/database/health
```

模拟账户：

```text
POST /api/accounts
GET  /api/accounts
GET  /api/accounts/{id}
```

投资组合：

```text
POST /api/portfolios
GET  /api/portfolios
GET  /api/portfolios/{id}
GET  /api/accounts/{accountId}/portfolios
```

证券标的：

```text
POST /api/instruments
GET  /api/instruments
GET  /api/instruments/{id}
POST /api/instruments/import/preview
POST /api/instruments/import
POST /api/instruments/{id}/daily-prices/sync
GET  /api/instruments/{id}/daily-prices
GET  /api/instruments/{id}/daily-prices/summary
POST /api/market-data/daily-prices/sync
```

股票代码导入支持：

```text
600519
000001
600519.SH
000001.SZ
SSE:600519
SZSE:000001
```

批量输入可使用换行、英文逗号、中文逗号、分号或空格分隔。单批最多 50 个代码。裸代码仅按本阶段规则推断：`60/68/90` 开头为 SSE，`00/20/30` 开头为 SZSE。

历史日线保存规则：

- 价格、涨跌额、涨跌幅、成交量和成交额均使用 DECIMAL；
- `volume_lots` 保存手数，由 Python 将上游成交量除以 100；
- `amount_cny` 保存上游人民币元成交额；新浪指数接口没有成交额时保存 `0`；
- `adjustment_type` 当前固定为 `NONE`；
- `data_source` 当前为 `PYTHON`（Python 内部路由到 BaoStock 或 AKShare）；既有历史数据不重写；
- 停牌日和无数据日期不插入空 K 线。

持仓查询：

```text
GET /api/portfolios/{portfolioId}/positions
GET /api/positions/{id}
```

持仓目前没有公开写接口。后续只能由模拟成交服务在事务中更新，避免绕过订单和风控。

风控配置：

```text
POST /api/risk-configs
PUT  /api/risk-configs/{id}
GET  /api/risk-configs/{id}
GET  /api/portfolios/{portfolioId}/risk-config
```

模拟订单：

```text
POST /api/orders
GET  /api/orders
GET  /api/orders/{id}
GET  /api/portfolios/{portfolioId}/orders
```

模拟成交、取消和查询：

```text
POST /api/orders/{id}/execute
POST /api/orders/{id}/cancel
GET  /api/trades
GET  /api/trades/{id}
GET  /api/orders/{orderId}/trades
GET  /api/portfolios/{portfolioId}/trades
GET  /api/accounts/{accountId}/cash-ledger
```

V4 订单规则：

- 当前只支持 `LIMIT` 模拟订单；
- 系统订单金额精度为 4 位小数；
- 订单金额低于 `0.0001` 时直接拒绝，避免冻结金额变成 `0.0000`；
- BUY 冻结金额来自订单提交时统一计算出的 `frozenAmount`，后续成交模块不应重新计算另一套订单金额；
- BUY 订单通过风控后冻结账户可用现金；
- BUY 订单创建成功后写入 `BUY_ORDER_FREEZE` 资金流水；
- SELL 订单通过风控后冻结对应组合、对应标的的可用持仓；
- SELL 下单不改变现金，因此不创建现金流水；
- 风控失败时不创建 `simulated_order`；
- 风控失败时不修改账户资金；
- 风控失败时不修改持仓数量；
- `OrderStatus.REJECTED` 仅为未来审计拒绝订单预留，V4 正常接口流程暂不持久化 REJECTED 订单；
- V4 尚未实现订单成交；
- V4 尚未实现订单取消；
- V4 不接入真实行情、真实券商或实盘交易。

V4 实际生效的风控规则：

- `allowBuy`；
- `allowSell`；
- `maxSingleOrderAmount`；
- BUY 可用现金检查；
- SELL 可用持仓检查。

`maxPositionRatio` 是未来市值风控的预留配置。当前没有真实行情和可靠市值，因此 V4 不执行最大仓位比例检查，也不会用成本价或订单价格冒充实时市值。

## V5 模拟成交规则

V5 只支持本地模拟的全量成交：

- 只处理 `OPEN` 订单；
- 一次性全量成交，不支持部分成交；
- 成交价格使用订单保存的 `limitPrice`；
- BUY 成交金额直接使用订单已有 `frozenAmount`，不重新计算另一套金额；
- SELL 成交金额按 V4 同一规则计算为 4 位小数；
- 这不是交易所撮合，不代表真实市场成交价；
- 不接入真实行情；
- 不接入真实券商；
- 不做实盘交易。

资金流水覆盖范围：

- BUY 下单冻结现金：`BUY_ORDER_FREEZE`；
- BUY 成交扣除冻结现金：`BUY_SETTLEMENT`；
- SELL 成交增加可用现金：`SELL_PROCEEDS`；
- BUY 取消释放冻结现金：`BUY_CANCEL_RELEASE`；
- SELL 下单和 SELL 取消不产生现金流水，因为它们只冻结或释放持仓数量；
- V5.1 不会自动为历史 BUY 订单补写冻结流水，历史脏数据后续可通过单独的数据修复脚本处理。

BUY 成交：

- 订单状态从 `OPEN` 变为 `FILLED`；
- 账户 `frozenCash` 减少；
- `availableCash` 不再次减少；
- 新建或更新对应组合、对应标的的持仓；
- 创建 `simulated_trade`；
- 创建 `BUY_SETTLEMENT` 资金流水。

SELL 成交：

- 订单状态从 `OPEN` 变为 `FILLED`；
- 对应组合、对应标的的 `totalQuantity` 减少；
- `frozenQuantity` 减少；
- `availableQuantity` 不再次减少；
- 账户 `availableCash` 增加；
- 创建 `simulated_trade`；
- 创建 `SELL_PROCEEDS` 资金流水。

订单取消：

- 只有 `OPEN` 订单可以取消；
- BUY 取消释放冻结现金，并创建 `BUY_CANCEL_RELEASE` 资金流水；
- SELL 取消释放冻结持仓，不创建虚假现金流水；
- 已成交和已取消订单不能再次执行或取消。

并发控制：

- `SimulatedOrder.version` 使用 MyBatis-Plus `@Version`；
- 订单执行和取消先通过乐观锁领取订单；
- 资金和持仓更新继续使用 `@Version`；
- 所有 `updateById` 和关键 `insert` 都检查返回值；
- 失败时抛出运行时业务异常，由 Spring 事务回滚。

## MySQL 事务集成测试

普通命令：

```powershell
.\mvnw.cmd clean test
```

该命令运行单元测试、Web 层测试，以及不依赖 Docker 的 H2 desktop 首次启动、重启持久化和核心业务兼容测试。

Testcontainers MySQL 集成测试命令：

```powershell
.\mvnw.cmd -P integration verify
```

该集成测试应验证：

- BUY 执行中途失败时，订单仍为 `OPEN`，资金和持仓回滚；
- SELL 执行中途失败时，订单、持仓和现金回滚；
- 两个并发请求执行同一订单时，最多一个成功，且只产生一条成交记录；
- 执行和取消并发操作同一订单时，只能一个操作成功；
- V7 能升级旧证券类型约束、保留数据并重复执行；
- V8 能放宽首条日线派生字段、保留关键非空约束和数据并重复执行；
- 当前全新结构允许 `STOCK`、`ETF`、`INDEX`，并继续拒绝不支持的类型。

## 项目资产规则

- 重要 SQL 保存在 `src/main/resources/db/`；
- 版本化表结构脚本保存在 `src/main/resources/db/migration/`；
- 长期项目进度保存在 `docs/PROJECT_PROGRESS.md`；
- 可提交的本地配置只放 example 模板；
- 真实密码、Token、私钥、日志、构建产物和本地配置不得提交。

---

# QuantGuard Backend

QuantGuard Backend is a local Spring Boot simulation trading and risk-control system.

The current scope includes:

- backend foundations for strategy analysis;
- simulation accounts;
- investment portfolios;
- security instruments;
- A-share security instrument import;
- unadjusted historical daily market data synchronization;
- read-only portfolio positions;
- simulated orders, trades, cash ledger records, and risk control.

The current scope excludes:

- frontend page implementation;
- real broker connections;
- real-money trading;
- automated live order placement;
- cookie, token, client reverse engineering, or UI scraping;
- real-time market data integration;
- minute-level bars;
- adjusted price data;
- technical indicators;
- high-frequency trading.

## Runtime Modes

The backend keeps two independent runtime modes:

- default/MySQL mode: keeps the existing MySQL configuration and manual V1-V8 migrations, with Flyway disabled;
- `desktop` Profile: uses H2 File without MySQL and initializes through a separate Flyway baseline.

Desktop files default to `%APPDATA%\QuantGuard\`, under `data`, `logs`, `config`, and `runtime`. Override the root with `QUANTGUARD_DATA_DIR`; when `APPDATA` is unavailable, the resolver safely falls back to the user home directory. The database is stored as `data\quantguard.mv.db`, and rolling logs live under `logs`. User data should not be stored in the repository or installation directory.

Manual desktop startup:

```powershell
$env:SPRING_PROFILES_ACTIVE="desktop"
$env:QUANTGUARD_DATA_DIR="C:\path\to\your\data"
.\mvnw.cmd spring-boot:run
```

Start and stop the full browser-based development stack from the repository root:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-dev.ps1 -Desktop
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\stop-dev.ps1 -Desktop
```

This is a desktop runtime foundation, not yet a Tauri client, bundled Java/Python runtime, or Windows installer.

## Self-contained Windows backend directory

V1.2-B uses Java 21 `jpackage --type app-image` to combine the executable Spring Boot JAR and Java Runtime:

```text
dist\desktop-runtime\backend\
├─ QuantGuardBackend.exe
├─ app\
└─ runtime\
```

The build script reads the actual `Main-Class` and `Start-Class` from the JAR manifest instead of guessing the business entry point:

```powershell
.\scripts\build-backend-runtime.ps1
```

The launcher enables the `desktop` Profile by default, accepts additional Spring Boot arguments, and inherits `QUANTGUARD_DATA_DIR`, `PYTHON_MARKET_DATA_BASE_URL`, and related environment variables. Database and logs remain under user data rather than the app-image directory. This directory does not require a system JDK, but is not yet a Tauri sidecar or installer.

## Local Database Initialization

The backend uses a local MySQL database:

```text
quant_guard
```

### 1. Create The Database

Run the following manually in MySQL:

```sql
source src/main/resources/db/create_database.sql;
```

If the MySQL client is not opened from the project root, use the full path to the file.

### 2. Run Migration Scripts In Order

Flyway remains disabled in default/MySQL mode. SQL files under `src/main/resources/db/migration/` are versioned, Git-managed scripts that must be executed manually. The `desktop` Profile uses the separate `src/main/resources/db/desktop-migration/` location and does not automatically alter existing MySQL databases.

Run them in order:

```sql
source src/main/resources/db/migration/V1__create_simulation_account.sql;
source src/main/resources/db/migration/V2__create_investment_portfolio.sql;
source src/main/resources/db/migration/V3__create_instrument_and_position_tables.sql;
source src/main/resources/db/migration/V4__create_risk_config_and_simulated_order_tables.sql;
source src/main/resources/db/migration/V5__create_trade_and_cash_ledger_tables.sql;
source src/main/resources/db/migration/V5_1__harden_cash_ledger.sql;
source src/main/resources/db/migration/V6__add_historical_market_data.sql;
source src/main/resources/db/migration/V7__allow_index_instruments.sql;
source src/main/resources/db/migration/V8__allow_null_first_daily_price_change.sql;
```

Each migration contains:

```sql
USE quant_guard;
```

After the database exists, you can also open and run each full script in MySQL Workbench.

`V5_1__harden_cash_ledger.sql` is an incremental hardening script. It adjusts valid `cash_ledger` entry types and adds the `(order_id, entry_type)` unique constraint. Run it once after V5. It is not intended to be executed repeatedly.

`V7__allow_index_instruments.sql` expands the instrument type constraint to `STOCK`, `ETF`, and `INDEX`. It preserves existing instruments and trading data and can be run repeatedly on an existing database.

`V8__allow_null_first_daily_price_change.sql` allows the first available daily bar to keep previous close, price change, and percentage change null instead of fabricating zero. It preserves existing daily prices and can be run repeatedly.

Before running V5.1, check for duplicate ledger records:

```sql
SELECT order_id, entry_type, COUNT(*) AS duplicate_count
FROM cash_ledger
GROUP BY order_id, entry_type
HAVING COUNT(*) > 1;
```

Only run `V5_1__harden_cash_ledger.sql` when the query returns no rows. If duplicates exist, do not delete or merge records automatically; review which entries should remain first.

## Local Configuration

Commit-safe configuration template:

```text
src/main/resources/application-local.example.yaml
```

Real local configuration:

```text
src/main/resources/application-local.yaml
```

For first-time setup, copy the example file to `application-local.yaml`, then fill in your local MySQL username and password.

Notes:

- `application-local.yaml` is ignored by `.gitignore`;
- do not commit real database passwords;
- do not commit API keys, tokens, private keys, or cookies;
- do not commit real trading data or personal private data.

## Market Data Configuration

V1.3 supports only the local token-free Python adapter. Legacy token configuration endpoints have been removed; scheduled sync requires explicit opt-in.

The adapter provides:

- instrument metadata and types: BaoStock;
- A-share stock daily bars: BaoStock;
- Search catalog: BaoStock stocks/ETFs plus AKShare Sina indexes.
- ETF daily bars: AKShare `fund_etf_hist_sina`.
- Index daily bars: AKShare `stock_zh_index_daily`; indexes are read-only and cannot be traded.

BaoStock and AKShare volume is normalized with `volume / 100` into `volumeLots`, and amount in CNY yuan is stored as `amountCny`. The Sina index endpoint has no amount field, so index daily bars currently use `0`. The first available bar keeps previous close, change, and percentage change null when no earlier close exists.

The instrument catalog uses a six-hour in-process cache. Its first load requires upstream network access, and restarting the Python service rebuilds the cache. If the upstream is unavailable before the first successful load, catalog search returns an explicit unavailable or timeout error.

Start the single-worker service from `quant-guard-market-data-python` before the backend. The Python service never accesses MySQL.

PowerShell example:

```powershell
$env:PYTHON_MARKET_DATA_BASE_URL="http://127.0.0.1:8001"
$env:MARKET_DATA_AUTO_SYNC_ENABLED="false" # Explicitly set true only to opt in
```

Defaults:

- `PYTHON_MARKET_DATA_BASE_URL` defaults to `http://127.0.0.1:8001`.
- Python connect timeout defaults to 3 seconds and read timeout to 60 seconds.
- `MARKET_DATA_DEFAULT_START_DATE` defaults to `1990-01-01`.
- `MARKET_DATA_AUTO_SYNC_ENABLED` defaults to `false`.
- `MARKET_DATA_AUTO_SYNC_CRON` defaults to weekdays at 18:00.
- automatic synchronization is disabled by default.

An unavailable Python service does not prevent Spring Boot startup; failures are reported only when market-data operations are called. The current scope supports code/name catalog search for SSE/SZSE A-share stocks, ETFs, and indexes. Stocks and ETFs are tradable in the simulator, while indexes are read-only daily-bar benchmarks. Other exchanges, minute bars, adjusted prices, backtesting, strategies, and real broker access remain out of scope.

## Common Validation

Run tests:

```powershell
.\mvnw.cmd test
```

Run the full regular test suite:

```powershell
.\mvnw.cmd clean test
```

Run Testcontainers MySQL integration tests:

```powershell
.\mvnw.cmd -P integration verify
```

The integration tests use a temporary MySQL container and test data. They do not connect to your local `quant_guard` database or read real database passwords from `application-local.yaml`.

After startup, the following endpoints are available:

```text
GET http://localhost:8080/api/health
GET http://localhost:8080/api/database/health
```

## Main APIs

Health:

```text
GET /api/health
GET /api/database/health
```

Simulation accounts:

```text
POST /api/accounts
GET  /api/accounts
GET  /api/accounts/{id}
```

Investment portfolios:

```text
POST /api/portfolios
GET  /api/portfolios
GET  /api/portfolios/{id}
GET  /api/accounts/{accountId}/portfolios
```

Security instruments:

```text
POST /api/instruments
GET  /api/instruments
GET  /api/instruments/{id}
POST /api/instruments/import/preview
POST /api/instruments/import
POST /api/instruments/{id}/daily-prices/sync
GET  /api/instruments/{id}/daily-prices
GET  /api/instruments/{id}/daily-prices/summary
POST /api/market-data/daily-prices/sync
```

Instrument import accepts:

```text
600519
000001
600519.SH
000001.SZ
SSE:600519
SZSE:000001
```

Batch input may be separated by newlines, English commas, Chinese commas, semicolons, or spaces. Each batch supports at most 50 codes. Bare codes are inferred only within this phase: `60/68/90` map to SSE, and `00/20/30` map to SZSE.

Historical daily price storage:

- prices, changes, percentage changes, volumes, and amounts use DECIMAL;
- `volume_lots` stores lots, normalized by Python from upstream volume divided by 100;
- `amount_cny` stores upstream CNY yuan; Sina index bars use `0` when no amount is provided;
- `adjustment_type` is currently fixed to `NONE`;
- `data_source` is currently `PYTHON` (internally routed to BaoStock or AKShare); historical records are not rewritten;
- suspended dates and no-data dates do not create empty bars.

Portfolio positions:

```text
GET /api/portfolios/{portfolioId}/positions
GET /api/positions/{id}
```

Positions currently have no public write API. Future updates should only be performed by simulated trade execution services inside transactions, so orders and risk control cannot be bypassed.

Risk configuration:

```text
POST /api/risk-configs
PUT  /api/risk-configs/{id}
GET  /api/risk-configs/{id}
GET  /api/portfolios/{portfolioId}/risk-config
```

Simulated orders:

```text
POST /api/orders
GET  /api/orders
GET  /api/orders/{id}
GET  /api/portfolios/{portfolioId}/orders
```

Simulated execution, cancellation, and queries:

```text
POST /api/orders/{id}/execute
POST /api/orders/{id}/cancel
GET  /api/trades
GET  /api/trades/{id}
GET  /api/orders/{orderId}/trades
GET  /api/portfolios/{portfolioId}/trades
GET  /api/accounts/{accountId}/cash-ledger
```

## V4 Order Rules

- Only `LIMIT` simulated orders are currently supported.
- Order amount precision is 4 decimal places.
- Orders below `0.0001` are rejected to avoid freezing `0.0000`.
- BUY frozen amount comes from the unified `frozenAmount` calculated when the order is submitted; later execution code should not calculate a second amount.
- BUY orders freeze available account cash after risk checks pass.
- BUY order creation writes a `BUY_ORDER_FREEZE` cash ledger record.
- SELL orders freeze available position quantity for the matching portfolio and instrument after risk checks pass.
- SELL order creation does not change cash, so it does not create a cash ledger record.
- Risk-control failures do not create `simulated_order` records.
- Risk-control failures do not change account cash.
- Risk-control failures do not change position quantity.
- `OrderStatus.REJECTED` is reserved for future audit records. The V4 normal API flow does not persist rejected orders.
- V4 does not implement order execution.
- V4 does not implement order cancellation.
- V4 does not connect to real quotes, real brokers, or live trading.

V4 active risk rules:

- `allowBuy`;
- `allowSell`;
- `maxSingleOrderAmount`;
- BUY available cash check;
- SELL available position check.

`maxPositionRatio` is reserved for future market-value risk control. Because the project currently has no real quotes or reliable market value, V4 does not enforce max position ratio checks and does not use cost or order price as fake real-time market value.

## V5 Simulated Execution Rules

V5 supports local simulated full-fill execution only:

- only `OPEN` orders are executable;
- execution is all-or-nothing, with no partial fills;
- execution price uses the order's stored `limitPrice`;
- BUY execution amount uses the order's existing `frozenAmount`, without recalculating another amount;
- SELL execution amount uses the same V4 rule and 4-decimal precision;
- this is not exchange matching and does not represent real market execution price;
- no real quotes are integrated;
- no real brokers are integrated;
- no live trading is performed.

Cash ledger coverage:

- BUY order cash freeze: `BUY_ORDER_FREEZE`;
- BUY execution frozen cash deduction: `BUY_SETTLEMENT`;
- SELL execution cash increase: `SELL_PROCEEDS`;
- BUY cancellation frozen cash release: `BUY_CANCEL_RELEASE`;
- SELL order creation and SELL cancellation do not create cash ledger records because they only freeze or release position quantity;
- V5.1 does not backfill freeze ledger records for historical BUY orders. Historical dirty data can be handled later by a dedicated data repair script.

BUY execution:

- order status changes from `OPEN` to `FILLED`;
- account `frozenCash` decreases;
- `availableCash` is not decreased again;
- the matching portfolio and instrument position is created or updated;
- a `simulated_trade` record is created;
- a `BUY_SETTLEMENT` cash ledger record is created.

SELL execution:

- order status changes from `OPEN` to `FILLED`;
- matching position `totalQuantity` decreases;
- `frozenQuantity` decreases;
- `availableQuantity` is not decreased again;
- account `availableCash` increases;
- a `simulated_trade` record is created;
- a `SELL_PROCEEDS` cash ledger record is created.

Order cancellation:

- only `OPEN` orders can be cancelled;
- BUY cancellation releases frozen cash and creates a `BUY_CANCEL_RELEASE` cash ledger record;
- SELL cancellation releases frozen position quantity and does not create fake cash ledger records;
- filled and cancelled orders cannot be executed or cancelled again.

Concurrency control:

- `SimulatedOrder.version` uses MyBatis-Plus `@Version`;
- order execution and cancellation first claim the order with optimistic locking;
- cash and position updates continue to use `@Version`;
- all `updateById` calls and key inserts check their affected row counts;
- failures throw runtime business exceptions and are rolled back by Spring transactions.

## MySQL Transaction Integration Tests

Regular command:

```powershell
.\mvnw.cmd clean test
```

This command runs unit tests, web-layer tests, and non-Docker H2 desktop tests for first startup, restart persistence, and core business compatibility.

Testcontainers MySQL integration test command:

```powershell
.\mvnw.cmd -P integration verify
```

The integration tests verify:

- if BUY execution fails halfway, the order remains `OPEN`, and cash and positions roll back;
- if SELL execution fails halfway, the order, positions, and cash roll back;
- when two concurrent requests execute the same order, at most one succeeds and only one trade record is created;
- when execution and cancellation race on the same order, only one operation can succeed.
- V7 upgrades the legacy instrument-type constraint, preserves data, and is repeatable;
- V8 makes first-bar derived fields nullable, preserves required columns and data, and is repeatable;
- the current fresh schema accepts `STOCK`, `ETF`, and `INDEX` while rejecting unsupported types.

## Project Asset Rules

- Important SQL files live in `src/main/resources/db/`.
- Versioned schema scripts live in `src/main/resources/db/migration/`.
- Long-term project progress is tracked in `docs/PROJECT_PROGRESS.md`.
- Commit-safe local configuration belongs in example templates only.
- Real passwords, tokens, private keys, logs, build artifacts, and local configuration files must not be committed.


V1.3 行情可靠性、超时边界、首次同步范围与实际网络证据见 [阶段 B 记录](../docs/v1.3/MARKET_DATA_FIX.md)。
