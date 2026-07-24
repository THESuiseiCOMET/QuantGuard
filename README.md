# QuantGuard

## 中文

QuantGuard 是一个前后端分离的量化交易模拟与投资组合风控平台。

当前项目定位是本地模拟系统。它不会连接真实券商，不会提交真实订单，不会自动操作真实账户，也不会执行真实交易。

### 项目结构

```text
QuantGuard/
├── .github/workflows/       # monorepo CI 工作流
├── quant-guard-backend/     # Spring Boot 后端
└── quant-guard-frontend/    # Vue 3 前端
```

### 当前实现

后端当前包括：

- 健康检查和数据库健康检查；
- 模拟账户；
- 投资组合；
- 证券标的；
- 只读持仓查询；
- 投资组合风险配置；
- 模拟订单；
- 模拟成交；
- 资金流水；
- 面向订单执行、撤单、资金、持仓和事务回滚行为的测试。

后端启动方式、数据库迁移顺序、接口说明和验证命令见 [quant-guard-backend/README.md](quant-guard-backend/README.md)。

前端基于 Vue 3、TypeScript、Vite、Vue Router、Pinia、Axios 和 Element Plus 初始化。

当前前端页面包括：

- 基于后端真实列表接口的总览页；
- 模拟账户列表和创建；
- 投资组合列表、账户筛选、创建和当前组合选择；
- 当前组合的持仓列表；
- 风险配置读取、创建和更新；
- 订单、成交、资金流水和后续模块的占位路由。

### 技术栈

后端：

- Java 21
- Spring Boot
- Maven Wrapper
- MyBatis-Plus
- MySQL
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

在 monorepo 根目录运行后端测试：

```powershell
cd quant-guard-backend
.\mvnw.cmd test
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
- 后端集成 CI：手动触发，使用 Docker/Testcontainers 运行集成测试；
- 前端 CI：push 和 pull request 时运行 `npm ci`、类型检查、lint 和构建。

### 项目状态

QuantGuard 正在开发中。

后端核心模拟链路已经覆盖模拟账户、投资组合、证券标的、订单、成交、资金流水和风控检查。

前端 V1 基础能力已完成总览、模拟账户、投资组合、持仓和风险配置。风险配置支持读取、创建和更新；`maxPositionRatio` 当前仅保存，尚未参与订单风控。模拟订单、成交记录和资金流水页面将在后续前端里程碑继续开发。

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
├── quant-guard-backend/     # Spring Boot backend
└── quant-guard-frontend/    # Vue 3 frontend
```

### Current Implementation

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

The frontend is initialized with Vue 3, TypeScript, Vite, Vue Router, Pinia, Axios, and Element Plus.

Current frontend pages include:

- dashboard summary based on existing backend list APIs;
- simulation account list and creation;
- investment portfolio list, account filter, creation, and current selection;
- portfolio position list for the current selected portfolio;
- risk configuration read, create, and update flows;
- placeholder routes for orders, trades, cash ledger, and later modules.

### Tech Stack

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

### Quick Start

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
- backend integration CI: manually triggered, runs Docker/Testcontainers integration tests;
- frontend CI: runs `npm ci`, type check, lint, and build on push and pull request.

### Project Status

QuantGuard is in active development.

The backend core simulation loop is implemented through simulated accounts, portfolios, instruments, orders, trades, cash ledger records, and risk checks.

The frontend V1 foundation covers dashboard, simulation accounts, investment portfolios, positions, and risk configuration. Risk configuration supports reading, creation, and updates; `maxPositionRatio` is currently stored only and is not yet enforced during order risk checks. Simulated orders, trade records, and cash ledger screens are planned for later frontend milestones.

### Safety Notice

QuantGuard is not a real trading system.

It does not support real brokerage access, real account automation, live market execution, credential harvesting, cookie or token scraping, or UI automation against brokerage clients.

Keep real passwords, API keys, tokens, private keys, broker credentials, account identifiers, logs, local configuration files, and private trading data out of Git.
