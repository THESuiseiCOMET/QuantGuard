# QuantGuard 后端

QuantGuard（智衡量化交易与风控系统）后端是一个本地自用的 Spring Boot 模拟交易与风控系统。

当前阶段只做：

- 策略分析的后端基础；
- 模拟账户；
- 投资组合；
- 证券标的；
- 只读持仓；
- 后续模拟订单、成交、资金流水和风控。

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
```

这些迁移脚本都包含：

```sql
USE quant_guard;
```

所以在数据库已创建后，也可以直接用 MySQL Workbench 打开完整脚本并执行。

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

## 项目资产规则

- 重要 SQL 保存在 `src/main/resources/db/`；
- 版本化表结构脚本保存在 `src/main/resources/db/migration/`；
- 长期项目进度保存在 `docs/PROJECT_PROGRESS.md`；
- 可提交的本地配置只放 example 模板；
- 真实密码、Token、私钥、日志、构建产物和本地配置不得提交。
