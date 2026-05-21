# Proposal: 预案与实盘对比系统

## Why

用户使用"预案"（预设的股票买卖条件）进行模拟交易，同时在真实账户中操作实盘。系统需要：
1. 根据预设条件自动判断预案是否触发，记录预案收益
2. 允许用户录入真实买卖记录，独立计算实盘收益
3. 将预案收益与实盘收益进行对比（知行差分析）

核心价值：**让用户量化"知"（预案逻辑）vs"行"（真实操作）之间的差距。**

## What Changes

- **新增实体**：Plan（预案）、PlanCondition（触发条件）、PlanExecution（实施记录）、ActualTrade（实盘记录）、DailySnapshot（每日快照）
- **新增 API**：Plan CRUD、ActualTrade CRUD、视图 B/C 数据接口
- **新增定时任务**：每个交易日 17:00（UTC+8）检查预案触发条件 + 生成每日快照
- **新增视图 B**：按时间段汇总对比（本周/本月 预案收益 vs 实盘收益）
- **新增视图 C**：当前持仓概览（预案浮盈/浮亏 vs 实盘浮盈/浮亏）
- **数据库依赖**：引入 MySQL + JPA（Spring Boot 项目无现有数据库依赖）
- **外部数据依赖**：Tushare 日K线（`high`/`low`/`close`/`volume`），数据完全满足，无缺口

## Capabilities

### Trigger Condition Spec

触发条件分为两类，**语义 B（站上/跌破）**，结合预案生命周期自然去重：

| 类型 | 触发条件 | 说明 |
|---|---|---|
| 固定价格 | `close >= targetPrice`（买入）/ `close <= targetPrice`（卖出） | 用户设定目标价 |
| MA 均线 | `close >= MA(N)`（买入）/ `close <= MA(N)`（卖出） | 收盘价站上/跌破均线 |

- **MA 周期**：支持 N = 5 / 10 / 20 / 60 / 120 / 250
- **去重逻辑**：仅首次触发有效，预案进入 holding 状态后条件再满足不重复触发
- **日内波动展示**：`high` / `low` / `close` 直接取自 Tushare 日K线字段

### New Capabilities

- `trading-plan`: 预案管理，包括创建/编辑/删除预案，预案生命周期（pending/holding/closed/expired），有效期窗口控制
- `plan-condition`: 触发条件管理，支持固定价格和 MA 均线两种类型，语义 B（站上/跌破）判断
- `plan-execution`: 预案实施记录，每触发一次生成一条记录，记录触发时间、价格、是否执行
- `actual-trade`: 实盘记录管理，用户手动录入每笔买卖，支持滞后录入，独立计算实盘收益
- `daily-snapshot`: 每日快照，每个交易日 17:00 生成，记录所有活跃预案和实盘的当前收益快照
- `performance-view-b`: 视图 B，按时间段（本周/本月）汇总预案 vs 实盘收益对比
- `performance-view-c`: 视图 C，当前持仓概览，显示预案和实盘的浮盈/浮亏

### Modified Capabilities

（无现有 capabilities，全部为新增）

## Impact

- **数据库**：新增 5 张表（plan, plan_condition, plan_execution, actual_trade, daily_snapshot）
- **API**：新增约 10+ 个 REST 端点（Plan CRUD, ActualTrade CRUD, View B/C）
- **定时任务**：新增每日 17:00 的调度任务
- **依赖**：pom.xml 新增 spring-boot-starter-data-jpa、mysql-connector-java、spring-boot-starter-data-redis（可选缓存）
- **外部集成**：Tushare 日K线接口，无数据缺口；需指定 `fq=pre` 前复权（MA120/250 长周期必需）
- **缓存策略**：日K线数据缓存 TTL 86400s（24h），次日 17:00 定时任务前刷新
