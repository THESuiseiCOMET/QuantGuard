# QuantGuard Frontend

## 中文

QuantGuard Frontend 是 QuantGuard 本地模拟交易与投资组合风控系统的 Vue 3 前端界面。

此前端只服务于本地模拟交易流程。它不会连接真实券商、真实交易账户或实盘撮合。

### 技术栈

- Vue 3
- TypeScript
- Vite
- Vue Router
- Pinia
- Axios
- Element Plus
- Tauri 2
- Rust
- ESLint
- Prettier

### 环境要求

- 当前工作区使用 Node.js 24 或更新版本；
- 后端目录位于 `../quant-guard-backend`；
- 后端默认端口为 `8080`。

### 安装依赖

```powershell
npm install
```

CI 环境使用更严格、可复现的安装命令：

```powershell
npm ci
```

### 启动开发环境

先启动后端：

```powershell
cd ..\quant-guard-backend
.\mvnw.cmd spring-boot:run
```

再启动前端：

```powershell
cd ..\quant-guard-frontend
npm run dev
```

默认前端地址：

```text
http://localhost:5173
```

### Tauri 桌面模式

浏览器开发模式继续使用 `npm run dev` 和 Vite 代理。Tauri 开发模式会先把 V1.2-B 生成的两套运行时复制到临时资源目录，再启动桌面窗口：

```powershell
npm run tauri:dev
```

从仓库根目录构建完整的 Windows 发布候选安装包：

```powershell
.\scripts\build-windows-release.ps1
```

生产桌面客户端从安装资源目录启动自包含的 Python 行情服务和 Java 后端，不依赖系统 Python、JDK、MySQL 或源码目录。用户数据和日志保存在 `%APPDATA%\QuantGuard\`；运行时 staging、Rust target 和安装包均被 Git 忽略。当前候选安装包未签名，Windows 可能显示 SmartScreen 提示。

### API 配置

前端默认使用：

```text
VITE_API_BASE_URL=/api
```

本地开发时，Vite 会把 `/api` 代理到：

```text
http://localhost:8080
```

可以提交 `.env.example`。不要提交真实密钥、Token、Cookie、本机私有配置或真实交易数据。

### 常用脚本

```powershell
npm run dev
npm run type-check
npm run lint
npm run test:e2e
npm run build
npm run preview
npm run tauri:dev
npm run tauri:build
```

### CI 验证

前端 GitHub Actions 会在 push 和 pull request 时运行：

```powershell
npm ci
npm run type-check
npm run lint
npm run build
```

### 当前范围

已实现：

- 应用启动骨架；
- Element Plus 接入；
- Vue Router 路由；
- Pinia 状态管理；
- Axios 对后端 `{ success, message, data }` 响应的解包；
- 带侧边栏和顶部栏的主布局；
- 当前模块和未来模块的占位页面；
- 使用真实列表接口的总览页；
- 模拟账户列表和创建；
- 投资组合列表、账户筛选、创建和当前组合选择；
- 当前组合的持仓列表；
- 风险配置读取、创建和更新；
- 模拟订单页面，支持按组合读取订单、读取真实证券标的、创建模拟限价订单、执行 OPEN 订单、取消 OPEN 订单，展示执行结果，并刷新真实账户资金和持仓；
- 成交记录页面，支持按投资组合读取模拟成交、映射订单和证券信息、方向/证券/关键字筛选，以及查看模拟成交详情；
- 资金流水页面，支持按账户读取本地模拟流水、按当前组合前端筛选、映射订单/成交/证券信息、正负资金变动展示、筛选汇总和流水详情；
- 行情中心页面，支持 A 股代码批量导入预览、确认导入、历史未复权日线同步、证券列表筛选、日 K、成交量、缩放、拖动和详细 tooltip；
- 账户、投资组合、持仓、风险配置、证券标的、模拟订单、模拟成交和资金流水的类型化 API 封装。
- Tauri 桌面启动门禁、动态后端地址注入、失败重试和日志目录入口；
- 浏览器与 Tauri 两种运行模式。

尚未实现：

- 实时行情；
- 分钟 K 线；
- 复权行情；
- 技术指标；
- 自动撮合；
- 回测；
- 策略模块；
- 登录权限；
- 生产部署；
- 真实券商接入；
- 浮动盈亏计算。

说明：QuantGuard 使用用户输入的限价进行本地模拟成交，不接入真实市场撮合，不连接真实券商，也不提交真实证券订单。`maxPositionRatio` 当前仅保存配置，现阶段后端尚未执行最大仓位比例检查。

历史行情来自外部数据提供者，最新日线收盘价不是实时成交价，行情可能存在延迟、缺失或更正，QuantGuard 不提供投资建议。

---

## English

QuantGuard Frontend is the Vue 3 user interface for the QuantGuard local simulation and portfolio risk-control system.

This frontend is for local simulated trading workflows only. It does not connect to real brokers, real trading accounts, or live market execution.

### Tech Stack

- Vue 3
- TypeScript
- Vite
- Vue Router
- Pinia
- Axios
- Element Plus
- Tauri 2
- Rust
- ESLint
- Prettier

### Requirements

- Node.js 24 or newer is currently used in this workspace.
- The backend runs from `../quant-guard-backend`.
- The backend default port is `8080`.

### Install

```powershell
npm install
```

CI uses the stricter reproducible install command:

```powershell
npm ci
```

### Start Development

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

### Tauri Desktop Mode

Browser development continues to use `npm run dev` and the Vite proxy. Tauri development first stages the two V1.2-B runtime directories, then opens the desktop window:

```powershell
npm run tauri:dev
```

Build the Windows release-candidate installer from the repository root:

```powershell
.\scripts\build-windows-release.ps1
```

The production desktop client launches the bundled Python market-data service and Java backend without relying on system Python, a JDK, MySQL, or the source tree. User data and logs live under `%APPDATA%\QuantGuard\`. Runtime staging, Rust target output, and installers are Git-ignored. The current release candidate is unsigned, so Windows may display a SmartScreen warning.

### API Configuration

The frontend uses:

```text
VITE_API_BASE_URL=/api
```

During local development, Vite proxies `/api` to:

```text
http://localhost:8080
```

Commit `.env.example`. Do not commit real secrets, tokens, cookies, machine-specific credentials, or real trading data.

### Scripts

```powershell
npm run dev
npm run type-check
npm run lint
npm run test:e2e
npm run build
npm run preview
npm run tauri:dev
npm run tauri:build
```

### CI Validation

Frontend GitHub Actions runs the following commands on push and pull request:

```powershell
npm ci
npm run type-check
npm run lint
npm run build
```

### Current Scope

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
- risk configuration read, create, and update flows;
- simulated order management, including portfolio-scoped order loading, real security instrument loading, simulated limit order creation, OPEN order execution, OPEN order cancellation, execution result display, and real account cash and position refreshes;
- trade records, including portfolio-scoped simulated trade loading, order and instrument mapping, side/instrument/keyword filters, and simulated trade details;
- cash ledger records, including account-scoped simulated cash ledger loading, current-portfolio frontend filtering, order/trade/instrument mapping, signed cash delta display, filtered summaries, and ledger details;
- market data center, including A-share import preview, confirmed import, unadjusted historical daily sync, instrument list filtering, daily candlesticks, volume bars, zooming, panning, and detailed tooltips;
- typed API shells for accounts, portfolios, positions, risk configuration, security instruments, simulated orders, simulated trades, and cash ledger records.
- Tauri desktop startup gating, dynamic backend URL injection, failure retry, and log-directory access;
- both browser and Tauri runtime modes.

Not implemented yet:

- real-time quotes;
- minute-level bars;
- adjusted prices;
- technical indicators;
- automatic matching;
- backtesting;
- strategy modules;
- authentication/authorization;
- production deployment;
- real brokerage access;
- floating P/L calculations.

Note: QuantGuard uses user-entered limit prices for local simulated fills, does not connect to live market matching, does not connect to real brokers, and does not place real securities orders. `maxPositionRatio` is currently stored only. The backend does not yet enforce maximum position ratio checks.

Historical market data comes from an external provider. The latest daily close is not a real-time trade price; market data may be delayed, missing, or corrected. QuantGuard does not provide investment advice.


V1.3 源码精简、依赖分离、日志限额与验证结果见 [精简记录](../docs/v1.3/SIMPLIFICATION.md)。安装包和自包含运行时构建当前按用户要求暂停；源码验证与预览可继续使用。
