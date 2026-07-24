# QuantGuard 后端

QuantGuard（智衡量化交易与风控系统）后端是一个本地自用的 Spring Boot 模拟交易与风控系统。

当前阶段只做：

- 策略分析的后端基础；
- 模拟账户；
- 投资组合；
- 证券标的；
- 只读持仓；
- 模拟订单、成交、资金流水和风控。

当前阶段不做：

- 前端页面；
- 真实券商连接；
- 真实资金交易；
- 自动实盘下单；
- Cookie、Token、客户端逆向或界面抓取；
- 真实行情接入；
- 高频交易。

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

当前项目暂未启用 Flyway，所以 `src/main/resources/db/migration/` 下的 SQL 是“手动执行、纳入 Git 管理”的版本化脚本。

请按顺序执行：

```sql
source src/main/resources/db/migration/V1__create_simulation_account.sql;
source src/main/resources/db/migration/V2__create_investment_portfolio.sql;
source src/main/resources/db/migration/V3__create_instrument_and_position_tables.sql;
source src/main/resources/db/migration/V4__create_risk_config_and_simulated_order_tables.sql;
source src/main/resources/db/migration/V5__create_trade_and_cash_ledger_tables.sql;
source src/main/resources/db/migration/V5_1__harden_cash_ledger.sql;
```

这些迁移脚本都包含：

```sql
USE quant_guard;
```

所以在数据库已创建后，也可以直接用 MySQL Workbench 打开完整脚本并执行。

`V5_1__harden_cash_ledger.sql` 是增量加固脚本：它会调整 `cash_ledger` 的合法流水类型，并新增 `(order_id, entry_type)` 唯一约束。该脚本应在 V5 后手动执行一次，不是可无限重复执行的脚本。

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
```

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

该命令只运行不依赖真实 MySQL 的单元测试和 Web 层测试。

Testcontainers MySQL 集成测试命令：

```powershell
.\mvnw.cmd -P integration verify
```

该集成测试应验证：

- BUY 执行中途失败时，订单仍为 `OPEN`，资金和持仓回滚；
- SELL 执行中途失败时，订单、持仓和现金回滚；
- 两个并发请求执行同一订单时，最多一个成功，且只产生一条成交记录；
- 执行和取消并发操作同一订单时，只能一个操作成功。

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
- read-only portfolio positions;
- simulated orders, trades, cash ledger records, and risk control.

The current scope excludes:

- frontend page implementation;
- real broker connections;
- real-money trading;
- automated live order placement;
- cookie, token, client reverse engineering, or UI scraping;
- real-time market data integration;
- high-frequency trading.

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

Flyway is not enabled yet. SQL files under `src/main/resources/db/migration/` are versioned, Git-managed scripts that must be executed manually.

Run them in order:

```sql
source src/main/resources/db/migration/V1__create_simulation_account.sql;
source src/main/resources/db/migration/V2__create_investment_portfolio.sql;
source src/main/resources/db/migration/V3__create_instrument_and_position_tables.sql;
source src/main/resources/db/migration/V4__create_risk_config_and_simulated_order_tables.sql;
source src/main/resources/db/migration/V5__create_trade_and_cash_ledger_tables.sql;
source src/main/resources/db/migration/V5_1__harden_cash_ledger.sql;
```

Each migration contains:

```sql
USE quant_guard;
```

After the database exists, you can also open and run each full script in MySQL Workbench.

`V5_1__harden_cash_ledger.sql` is an incremental hardening script. It adjusts valid `cash_ledger` entry types and adds the `(order_id, entry_type)` unique constraint. Run it once after V5. It is not intended to be executed repeatedly.

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
```

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

This command only runs unit tests and web-layer tests that do not depend on real MySQL.

Testcontainers MySQL integration test command:

```powershell
.\mvnw.cmd -P integration verify
```

The integration tests verify:

- if BUY execution fails halfway, the order remains `OPEN`, and cash and positions roll back;
- if SELL execution fails halfway, the order, positions, and cash roll back;
- when two concurrent requests execute the same order, at most one succeeds and only one trade record is created;
- when execution and cancellation race on the same order, only one operation can succeed.

## Project Asset Rules

- Important SQL files live in `src/main/resources/db/`.
- Versioned schema scripts live in `src/main/resources/db/migration/`.
- Long-term project progress is tracked in `docs/PROJECT_PROGRESS.md`.
- Commit-safe local configuration belongs in example templates only.
- Real passwords, tokens, private keys, logs, build artifacts, and local configuration files must not be committed.
