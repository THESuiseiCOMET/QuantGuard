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
npm run build
npm run preview
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
- 账户、投资组合、持仓、风险配置、证券标的和模拟订单的类型化 API 封装。

尚未实现：

- 独立成交记录页面；
- 独立资金流水页面；
- 真实行情；
- 历史 K 线；
- 自动撮合；
- 浮动盈亏计算。

说明：`maxPositionRatio` 当前仅保存配置，现阶段后端尚未执行最大仓位比例检查。

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
npm run build
npm run preview
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
- typed API shells for accounts, portfolios, positions, risk configuration, security instruments, and simulated orders.

Not implemented yet:

- dedicated trade record screen;
- dedicated cash ledger screen;
- real-time quotes;
- historical candlesticks;
- automatic matching;
- floating P/L calculations.

Note: order execution uses the order limit price for local simulated fills and does not represent live market matching. `maxPositionRatio` is currently stored only. The backend does not yet enforce maximum position ratio checks.
