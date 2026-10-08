# QuantGuard

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
- 回本计算器。

桌面客户端默认使用无需 Token 的 Python 行情服务。Tushare 仍作为可选 Provider 保留，可在开发或服务器模式中通过配置文件或环境变量显式启用；桌面界面不展示 Token 设置页。

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

这些脚本只用于浏览器开发和演示，不会安装依赖或写入 Token；MySQL 模式的日志和 PID 保存在 `.runtime/`，桌面 Profile 保存在用户数据目录。V1.2-C 另行提供 Tauri 客户端构建，生产客户端不依赖这些开发脚本。

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
- 前端 CI：push 和 pull request 时运行 `npm ci`、类型检查、lint、浏览器 E2E 和构建；
- Windows 桌面 CI：构建两套自包含运行时、Tauri 客户端和 NSIS 候选安装包，执行离线生命周期 smoke test，并生成 SHA-256 校验文件。

### 项目状态

QuantGuard V1.1 提供本地模拟交易和无 Token 历史行情底座。

V1.2-A 完成 Windows 桌面运行底座：后端可通过 H2 File 在没有 MySQL 的环境中持久运行。

V1.2-B 提供两套 Windows 自包含运行目录：Python 行情服务使用 PyInstaller `onedir`，Spring Boot 后端使用 `jpackage --type app-image` 并携带 Java 21 Runtime。

V1.2-C 将现有 Vue 界面和两套运行时装入 Tauri 2。客户端负责动态选择本机回环端口、依次启动 Python 与 Java、等待健康检查，并在退出时清理其创建的进程树。

V1.2-D 使用统一脚本构建未签名 Windows 发布候选包：

```powershell
.\scripts\build-windows-release.ps1
```

候选安装包、SHA-256 和构建摘要位于被 Git 忽略的 `dist\windows-release\`。默认数据与日志仍保存在 `%APPDATA%\QuantGuard\`，不写入安装目录。当前社区候选版本未进行商业代码签名，Windows 可能显示 SmartScreen 提示。

### Windows 安装与数据

- 系统：Windows 11 x64；安装方式为当前用户安装，不要求预装 Python、JDK、MySQL、Node.js 或 Rust。
- WebView2：安装包使用在线 bootstrapper 策略；缺少 WebView2 时需要网络完成依赖安装，因此当前安装包不承诺完全离线安装。
- 数据目录：`%APPDATA%\QuantGuard\data\`。
- 日志目录：`%APPDATA%\QuantGuard\logs\`。
- 卸载策略：卸载应用默认保留 `%APPDATA%\QuantGuard\` 中的模拟交易数据；需要彻底清除时，请在确认备份后手动删除该目录。
- 备份：完全退出 QuantGuard 并确认后台进程结束后，复制整个 `%APPDATA%\QuantGuard\data\` 目录。恢复前同样先关闭客户端和后台进程，再用备份目录替换数据目录。不建议在 H2 正在运行时直接复制数据库文件。
- 网络：已有账户、订单、持仓和本地 K 线可离线查看；搜索新证券和同步行情需要访问外部数据源。

详细变化、安装说明与已知限制见 [V1.2.0 发布说明](docs/releases/v1.2.0.md)。

准备好项目虚拟环境中的运行与构建依赖后，可从仓库根目录执行：

```powershell
.\quant-guard-market-data-python\.venv\Scripts\python.exe -m pip install `
  -r .\quant-guard-market-data-python\requirements-build.txt
.\scripts\build-desktop-runtimes.ps1
```

输出位于被 Git 忽略的 `dist\desktop-runtime\`：

```text
desktop-runtime/
├─ market-data/
│  ├─ quantguard-market-data.exe
│  └─ PyInstaller dependencies
└─ backend/
   ├─ QuantGuardBackend.exe
   ├─ app/
   └─ runtime/
```

统一脚本会运行 Python 和 Java 测试、构建两个运行目录，并在移除 Python/JDK 环境路径后验证健康检查、H2 建库与重启持久化。真实行情网络调用仅供手动 smoke 使用，不作为 CI 绿色条件：

```powershell
.\scripts\test-desktop-runtimes.ps1 -RunLiveMarketData
```

当前 H2 2.4 会显示 Flyway 尚未充分验证该新版本的提示。本地和 CI 迁移测试会继续保留该提示与门禁，本阶段不为隐藏警告而盲目降级依赖。

后端核心模拟链路已经覆盖模拟账户、投资组合、证券标的、订单、成交、资金流水和风控检查。

V1.2 支持上交所和深交所普通 A 股、ETF 与指数的目录搜索和历史未复权日线。股票与 ETF 可以参与模拟交易，指数仅用于行情展示。证券目录使用六小时进程内缓存：首次加载依赖网络，Python 服务重启后会重新建立缓存，上游不可用时首次搜索可能失败。ETF 上游存在偶发不可用；客户端会显示明确错误，本地已有数据不受影响。实时行情、分钟行情、复权行情、自动撮合、回测、策略执行、登录权限、代码签名、自动更新和真实券商接入不在当前版本范围。

Tushare Provider 仍可在开发或服务器模式中通过环境变量配置，但桌面客户端默认使用无 Token 行情服务并隐藏相关设置入口。




Keep real passwords, API keys, tokens, private keys, broker credentials, account identifiers, logs, local configuration files, and private trading data out of Git.
