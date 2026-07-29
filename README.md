# QuantGuard

## 中文

QuantGuard 是一个前后端分离的量化交易模拟与投资组合风控平台。

当前项目定位是本地模拟系统。它不会连接真实券商，不会提交真实订单，不会自动操作真实账户，也不会执行真实交易。

### 项目结构

```text
QuantGuard/
├── .github/workflows/       # monorepo CI 工作流
├── scripts/                 # Windows 开发启动与停止脚本
├── quant-guard-backend/     # Spring Boot 后端
├── quant-guard-frontend/    # Vue 3 前端
└── quant-guard-market-data-python/ # BaoStock / AKShare 行情适配服务
```

### 当前实现

后端当前包括：

- 健康检查和数据库健康检查；
- 模拟账户；
- 投资组合；
- 证券标的；
- A 股证券标的批量导入预览和确认导入；
- 可按代码或名称搜索的无 Token 证券目录，以及 BaoStock 股票、AKShare 新浪 ETF/指数行情适配；
- 历史未复权日线行情同步、查询和同步日志；
- 可配置的工作日自动增量行情同步，默认关闭；
- 只读持仓查询；
- 投资组合风险配置；
- 模拟订单；
- 模拟成交；
- 资金流水；
- 面向订单执行、撤单、资金、持仓和事务回滚行为的测试。

后端启动方式、数据库迁移顺序、接口说明和验证命令见 [quant-guard-backend/README.md](quant-guard-backend/README.md)。

前端基于 Vue 3、TypeScript、Vite、Vue Router、Pinia、Axios 和 Element Plus 初始化。

当前前端页面包括：

- 模拟账户及其投资组合管理；
- 行情中心，支持按代码或名称搜索并添加 A 股股票、ETF 和指数，查看未复权日 K、持仓和收益；
- 模拟订单与成交管理，支持创建、执行和取消模拟限价订单；
- 回本计算器；
- 设置中心，可向当前运行的后端进程配置 Tushare Token；行情 Provider 通过配置文件或环境变量显式切换。

行情数据统一使用十进制字符串传输。BaoStock 和 AKShare 的成交量按 `volume / 100` 转换为 `volumeLots`，成交额以人民币元保存到 `amountCny`；新浪指数接口没有成交额时暂存为 `0`。首条可用日线没有昨收时，昨收、涨跌额和涨跌幅保持为空，不伪造为零。

### 技术栈

后端：

- Java 21
- Spring Boot
- Maven Wrapper
- MyBatis-Plus
- MySQL / H2 File
- Flyway（仅 desktop Profile）
- JUnit
- Testcontainers

前端：

- Vue 3
- TypeScript
- Vite
- Vue Router
- Pinia
- Axios
- Element Plus

### 快速开始

V1.1 的默认开发/服务器模式继续使用 MySQL。V1.2-A 新增 `desktop` Profile，可将数据库、日志和运行状态保存在 Windows 用户数据目录中，不需要安装 MySQL。

准备好 Python 虚拟环境和前端依赖后，可在仓库根目录一键启动桌面开发模式：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-dev.ps1 -Desktop
```

停止桌面开发模式：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\stop-dev.ps1 -Desktop
```

默认用户数据目录为 `%APPDATA%\QuantGuard\`，包含 `data`、`logs`、`config` 和 `runtime`。测试或自定义路径时可设置 `QUANTGUARD_DATA_DIR`，或向脚本传入 `-DataDirectory`。用户数据库不应放在仓库或未来的程序安装目录中。

桌面模式使用 H2 File，并只在 `desktop` Profile 中启用 Flyway 自动迁移。手动启动后端的示例：

```powershell
cd quant-guard-backend
$env:SPRING_PROFILES_ACTIVE="desktop"
$env:QUANTGUARD_DATA_DIR="C:\path\to\your\data"
.\mvnw.cmd spring-boot:run
```

现有 MySQL 开发模式保持不变：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-dev.ps1
```

停止由脚本启动的服务：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\stop-dev.ps1
```

脚本只用于开发和演示。它不会安装依赖或写入 Token；MySQL 模式的日志和 PID 保存在 `.runtime/`，桌面模式保存在用户数据目录。这仍不是 Tauri 客户端或安装包，前端需要通过浏览器访问。Python、Java Runtime 和 Tauri 的独立打包属于后续阶段。

在 monorepo 根目录运行后端测试：

```powershell
cd quant-guard-backend
.\mvnw.cmd test
```

首次启动无 Token 行情服务：

```powershell
cd quant-guard-market-data-python
py -3.11 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8001 --workers 1
```

本机有 Docker 时运行后端集成测试：

```powershell
cd quant-guard-backend
.\mvnw.cmd -P integration verify
```

按后端 README 配置本地 MySQL profile 后启动后端：

```powershell
cd quant-guard-backend
.\mvnw.cmd spring-boot:run
```

默认本地健康检查接口：

```text
GET http://localhost:8080/api/health
GET http://localhost:8080/api/database/health
```

启动前端：

```powershell
cd quant-guard-frontend
npm install
npm run dev
```

默认前端地址：

```text
http://localhost:5173
```

### CI 验证

仓库当前包含：

- 后端 CI：push 和 pull request 时运行后端 Maven 测试；
- 桌面 H2 CI：作为普通后端测试执行，明确校验 6 条桌面路径与持久化测试没有被跳过；
- 后端集成 CI：后端变更的 push、pull request 和手动触发时，使用 Docker/Testcontainers 运行事务与数据库结构集成测试；
- Python 行情 CI：Python 行情服务变更时安装锁定版本依赖，运行离线测试和模块编译；
- 前端 CI：push 和 pull request 时运行 `npm ci`、类型检查、lint、浏览器 E2E 和构建。

### 项目状态

QuantGuard V1.1 提供本地模拟交易和无 Token 历史行情底座。

V1.2-A 开发分支新增 Windows 桌面运行底座：后端可通过 H2 File 在没有 MySQL 的环境中持久运行，但尚未生成桌面客户端或安装包。

后端核心模拟链路已经覆盖模拟账户、投资组合、证券标的、订单、成交、资金流水和风控检查。

V1.1 支持上交所和深交所普通 A 股、ETF 与指数的目录搜索和历史未复权日线。股票与 ETF 可以参与模拟交易，指数仅用于行情展示。证券目录使用六小时进程内缓存：首次加载依赖网络，Python 服务重启后会重新建立缓存，上游不可用时首次搜索可能失败。实时行情、分钟行情、复权行情、自动撮合、回测、策略执行、登录权限、桌面客户端、生产部署和真实券商接入不在当前版本范围。

设置中心提交的 Tushare Token 只保存在当前后端进程内存中，不写入仓库或本地配置文件；后端重启后需要重新配置。

### 安全说明

QuantGuard 不是实盘交易系统。

它不支持真实券商接入、真实账户自动化、实时市场交易、凭证采集、Cookie 或 Token 抓取，也不会自动操作券商客户端。

请不要把真实密码、API Key、Token、私钥、券商凭证、账户标识、本地日志、本地配置文件或私人交易数据提交到 Git。

---

## English

QuantGuard is a frontend and backend separated quantitative trading simulation and portfolio risk-control platform.

The project is currently a local simulation system. It does not connect to real brokers, place real orders, automate real accounts, or execute live trading.

### Project Structure

```text
QuantGuard/
├── .github/workflows/       # CI workflows for the monorepo
├── scripts/                 # Windows development start/stop scripts
├── quant-guard-backend/     # Spring Boot backend
├── quant-guard-frontend/    # Vue 3 frontend
└── quant-guard-market-data-python/ # BaoStock / AKShare adapter
```

### Current Implementation

The backend currently includes:

- health and database health checks;
- simulation accounts;
- investment portfolios;
- security instruments;
- A-share security instrument import preview and confirmation APIs;
- token-free code/name instrument search plus BaoStock stock and AKShare Sina ETF/index routing;
- unadjusted historical daily price synchronization, queries, and sync logs;
- configurable weekday automatic incremental market-data synchronization, disabled by default;
- read-only portfolio positions;
- portfolio risk configuration;
- simulated orders;
- simulated trades;
- cash ledger records;
- transaction-focused tests for order execution, cancellation, cash, holdings, and rollback behavior.

Detailed backend setup, database migration order, API notes, and validation commands are documented in [quant-guard-backend/README.md](quant-guard-backend/README.md).

The frontend is initialized with Vue 3, TypeScript, Vite, Vue Router, Pinia, Axios, and Element Plus.

Current frontend pages include:

- simulation accounts with nested investment portfolio management;
- a market center with code/name search for A-share stocks, ETFs, and indexes, unadjusted daily candlesticks, positions, and returns;
- simulated order and trade management, including creation, execution, and cancellation of simulated limit orders;
- a recovery calculator;
- a settings center that configures a Tushare token in the currently running backend; Provider selection remains an explicit configuration or environment setting.

Market-data decimals are transferred as strings. BaoStock and AKShare volume is converted with `volume / 100` into `volumeLots`, while amount in CNY yuan is stored as `amountCny`; Sina index data currently uses `0` when the upstream response has no amount field. The first available bar keeps previous close, change, and percentage change null when no earlier close exists.

### Tech Stack

Backend:

- Java 21
- Spring Boot
- Maven Wrapper
- MyBatis-Plus
- MySQL / H2 File
- Flyway (desktop Profile only)
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

### Quick Start

V1.1's default development/server mode continues to use MySQL. V1.2-A adds a `desktop` Profile that stores the database, logs, and runtime state under the Windows user data directory without requiring MySQL.

After preparing the Python environment and frontend dependencies, start the desktop development mode from the repository root:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-dev.ps1 -Desktop
```

Stop it with:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\stop-dev.ps1 -Desktop
```

The default directory is `%APPDATA%\QuantGuard\`, with `data`, `logs`, `config`, and `runtime` children. Override it with `QUANTGUARD_DATA_DIR` or the script's `-DataDirectory` option. User data should not live in the repository or a future installation directory.

Desktop mode uses H2 File and enables Flyway only under the `desktop` Profile. Manual backend example:

```powershell
cd quant-guard-backend
$env:SPRING_PROFILES_ACTIVE="desktop"
$env:QUANTGUARD_DATA_DIR="C:\path\to\your\data"
.\mvnw.cmd spring-boot:run
```

The existing MySQL development mode remains available:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-dev.ps1
```

Stop only the processes started by that script:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\stop-dev.ps1
```

The scripts are for development and demos only. They do not install dependencies or write tokens. MySQL-mode logs and PID state use `.runtime/`; desktop mode uses the user data directory. This is not yet a Tauri client or installer, and the frontend still opens in a browser. Packaging Python, Java Runtime, and Tauri belongs to a later phase.

Prepare and start the token-free single-worker service:

```powershell
cd quant-guard-market-data-python
py -3.11 -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe -m uvicorn app.main:app --host 127.0.0.1 --port 8001 --workers 1
```

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

### CI Validation

The repository currently includes:

- backend CI: runs Maven backend tests on push and pull request;
- desktop H2 CI: runs with regular backend tests and explicitly verifies that all six desktop path and persistence tests executed without skips;
- backend integration CI: runs Docker/Testcontainers transaction and schema tests for backend changes on push, pull request, or manual dispatch;
- Python market-data CI: installs pinned dependencies and runs offline tests plus module compilation for Python service changes;
- frontend CI: runs `npm ci`, type check, lint, browser E2E tests, and build on push and pull request.

### Project Status

QuantGuard V1.1 provides a local trading simulator with a token-free historical market-data foundation.

The V1.2-A development branch adds a Windows desktop runtime foundation: the backend can persist with H2 File without MySQL, but no desktop client or installer is produced yet.

The backend core simulation loop is implemented through simulated accounts, portfolios, instruments, orders, trades, cash ledger records, and risk checks.

V1.1 supports catalog search and historical unadjusted daily bars for SSE/SZSE A-share stocks, ETFs, and indexes. Stocks and ETFs can participate in simulation, while indexes are read-only market references. The catalog uses a six-hour in-process cache: its first load requires network access, a Python service restart rebuilds it, and an upstream outage can prevent the initial search. Real-time quotes, minute bars, adjusted prices, automatic matching, backtesting, strategies, authentication, the desktop client, production deployment, and real brokerage access remain outside this release.

The settings page keeps a submitted Tushare token only in the current backend process. It does not write the token to the repository or a local configuration file, so the token must be configured again after a backend restart.

### Safety Notice

QuantGuard is not a real trading system.

It does not support real brokerage access, real account automation, live market execution, credential harvesting, cookie or token scraping, or UI automation against brokerage clients.

Keep real passwords, API keys, tokens, private keys, broker credentials, account identifiers, logs, local configuration files, and private trading data out of Git.
