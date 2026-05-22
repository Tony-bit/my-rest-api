# Implementation Tasks

## 1. Project Setup

- [x] 1.1 Add Maven dependencies to pom.xml: spring-boot-starter-data-jpa, mysql-connector-java, lombok
- [x] 1.2 Configure MySQL connection in application.yml (host, port, database, username, password)
- [x] 1.3 Configure JPA auto DDL strategy (update) and dialect
- [x] 1.4 Create base entity classes with JPA annotations (@Entity, @Id, @GeneratedValue)

## 2. Domain Entities

- [x] 2.1 Implement Plan entity (id, name, stockCode, stockName, cycle, status, isLocked, createdAt, updatedAt)
- [x] 2.2 Implement PlanCondition entity (id, planId, conditionType, direction, maPeriod, targetPrice)
  - conditionType: PRICE | MA (enum)
  - direction: BUY | SELL
  - maPeriod: 支持 5/10/20/60/120/250（仅 conditionType=MA 时使用）
  - targetPrice: 固定价格阈值（仅 conditionType=PRICE 时使用）
- [x] 2.3 Implement PlanExecution entity (id, planId, tradeDate, direction, triggered, triggerPrice, maValue, conditionId, executed)
  - executed 字段用于幂等保护：标记该预案是否已执行过买入
- [x] 2.4 Implement ActualTrade entity (id, stockCode, stockName, direction, price, quantity, tradeDate, profitLoss, profitLossPercent, createdAt)
- [x] 2.5 Implement DailySnapshot entity (id, planId, snapshotDate, stockCode, planStatus, planReturn, hasActualTrade, actualReturn, openQuantity, avgCostBasis, createdAt)
- [x] 2.6 Define JPA repository interfaces for all entities (JpaRepository)

## 3. Plan CRUD API (spec: trading-plan)

- [x] 3.1 Implement PlanService with create, read, update, delete, list operations
- [x] 3.2 Implement PlanController with POST/GET/PUT/DELETE endpoints at /api/plans
- [x] 3.3 Add status-based editability check: only PENDING plans are editable; return HTTP 409 for HOLDING/CLOSED/EXPIRED
- [x] 3.4 Add delete protection: only PENDING and EXPIRED plans can be deleted
- [x] 3.5 Implement query filters: status, cycle, stockCode
- [x] 3.6 Write unit tests for PlanService

## 4. PlanCondition Management

- [x] 4.1 Implement PlanConditionService and nested resource controller (nested under /api/plans/{planId}/conditions)
- [x] 4.2 Add condition creation/editing only allowed when plan is PENDING
- [x] 4.3 Validate maPeriod ∈ {5, 10, 20, 60, 120, 250} (仅 conditionType=MA); validate targetPrice > 0 (仅 conditionType=PRICE)
- [x] 4.4 Write unit tests for PlanConditionService

## 5. PlanExecution Records

- [x] 5.1 Implement PlanExecutionService and controller at /api/plans/{planId}/executions
- [x] 5.2 Implement plan state machine transitions: PENDING→HOLDING (buy), HOLDING→CLOSED (sell)
- [x] 5.3 Implement isLocked flag: set to true when plan enters HOLDING
- [x] 5.4 Write unit tests for PlanExecutionService

## 6. ActualTrade CRUD API (spec: actual-trade)

- [x] 6.1 Implement ActualTradeService with create, read, update, delete, list operations
- [x] 6.2 Implement ActualTradeController at /api/actual-trades
- [x] 6.3 Implement FIFO matching for profit/loss calculation
- [x] 6.4 Implement profitLoss and profitLossPercent computation on sell trades
- [x] 6.5 Implement open position tracking (avgCostBasis, openQuantity per stock)
- [x] 6.6 Add query filters: stockCode, direction, date range
- [x] 6.7 Write unit tests for ActualTradeService

## 7. Tushare Integration

- [x] 7.1 Create TushareConfig with API token configuration
- [x] 7.2 Implement TushareService for fetching daily K-line data (close price, MA values)
- [x] 7.3 Implement MA calculation: fetch N-day close prices, compute simple moving average
- [x] 7.4 Implement trigger condition evaluation:
  - PRICE 类型: `close >= targetPrice`（买入）/ `close <= targetPrice`（卖出）
  - MA 类型: `|close - MA(N)| / close <= 0.3%`（触碰型语义，买入卖出相同）
  - 仅首次触发（HOLDING 状态前有效）
- [x] 7.5 支持 MA120/250 长周期，调用 Tushare 时指定 `fq=pre` 前复权参数
- [x] 7.6 Implement current price fetch for snapshot and view APIs (include high/low/close for日内波动)
- [x] 7.7 Add error handling for Tushare API failures (retry, fallback, logging)
- [x] 7.8 Write unit tests for TushareService
  - MA 触发语义：触碰型 `|close - MA(N)| / close <= 0.3%`，买入卖出相同

## 8. Scheduled Tasks (spec: daily-snapshot)

- [x] 8.1 Enable Spring scheduling (@EnableScheduling)
- [x] 8.2 Implement MarketCloseTask scheduled at 17:00 UTC+8 every trading day
- [x] 8.3 Implement trigger evaluation loop: iterate all PENDING/HOLDING plans, evaluate conditions
- [x] 8.4 Implement validity window check: PENDING plans outside window → EXPIRED
- [x] 8.5 Implement snapshot generation for all active plans
- [x] 8.6 Implement snapshot generation for all open ActualTrade positions
- [x] 8.7 Add holiday handling: skip execution on non-trading days (check against Tushare calendar)
- [ ] 8.8 Write integration tests for MarketCloseTask
- [x] 8.9 Add snapshot cleanup job: delete snapshots older than 3 months

### 8.10 幂等保护（spec: idempotency）

- [ ] 8.10.1 MarketCloseTask 执行前检查 PlanExecution.executed 字段
  - executed == true 时跳过该预案的买入触发评估
  - 同一事务内批量处理，状态转移（PENDING→HOLDING→CLOSED）作为第一道天然去重
- [ ] 8.10.2 PlanExecution 创建时设置 executed = true（买入触发后立即标记）
- [ ] 8.10.3 卖出触发（CLOSED 状态）不依赖 executed 字段，由状态机保证（HOLDING 才能触发卖出）
- [ ] 8.10.4 多预案并发处理：在同一事务内批量更新，避免重复触发

## 9. Period Summary View B (spec: performance-view-b)

- [x] 9.1 Implement PeriodSummaryService with WEEK/MONTH/YEAR aggregation logic
- [x] 9.2 Implement GET /api/views/period-summary endpoint with period query param
- [x] 9.3 Compute planSummary: totalTrades, totalReturn, avgReturn, pendingCount for period
- [x] 9.4 Compute actualSummary: totalTrades, totalReturn, avgReturn for period
- [x] 9.5 Compute gap(%) = planSummary.avgReturn - actualSummary.avgReturn
- [x] 9.6 Return planList and actualList with individual returns
- [x] 9.7 Write unit tests for PeriodSummaryService

## 10. Holdings View C (spec: performance-view-c)

- [x] 10.1 Implement HoldingsService for aggregating current positions
- [x] 10.2 Implement GET /api/views/holdings endpoint
- [x] 10.3 Compute planHoldings: unrealizedPL, unrealizedPL(%), holdDays, 日内波动（high/low/close）for all HOLDING plans
- [x] 10.4 Compute actualHoldings: unrealizedPL, unrealizedPL(%) for all open ActualTrade positions
- [x] 10.5 Compute summary: totalPlanUnrealizedPL, totalActualUnrealizedPL, holdingGap
- [x] 10.6 Implement refresh=true param to force Tushare price refresh
- [x] 10.7 Write unit tests for HoldingsService

## 11. API Response DTOs

- [x] 11.1 Create DTOs for Plan request/response (PlanCreateDTO, PlanResponseDTO, PlanListDTO)
- [x] 11.2 Create DTOs for PlanCondition (PlanConditionDTO)
- [x] 11.3 Create DTOs for ActualTrade (ActualTradeDTO, ActualTradeResponseDTO)
- [x] 11.4 Create DTOs for View B (PeriodSummaryResponseDTO)
- [x] 11.5 Create DTOs for View C (HoldingsResponseDTO, PlanHoldingDTO, ActualHoldingDTO)
- [x] 11.6 Create DTOs for Snapshot (SnapshotResponseDTO)
- [x] 11.7 Add global exception handler (@ControllerAdvice) for validation errors and 409 conflicts

## 12. Configuration and Environment

- [x] 12.1 Externalize Tushare token to application.yml / environment variable
- [x] 12.2 Externalize MySQL credentials to application.yml
- [x] 12.3 Configure Docker: add MySQL service to docker-compose.yml
- [x] 12.4 Add Docker profile (application-docker.yml) with correct MySQL host
- [ ] 12.5 Document environment variables in README

## 13. End-to-End Testing

- [ ] 13.1 Write integration tests: create plan → trigger evaluation → snapshot generation
- [ ] 13.2 Write integration tests: actual trade FIFO matching and profit calculation
- [ ] 13.3 Write integration tests: View B period summary computation
- [ ] 13.4 Write integration tests: View C holdings computation
- [ ] 13.5 Manual testing checklist: create a plan, trigger buy/sell, generate snapshot, view对比
- [ ] 13.6 Write integration tests: baselineCapital 变更后新快照收益率重算，旧快照不变
- [ ] 13.7 Write integration tests: 知行差计算 = planReturnPct - actualReturnPct

## 14. Global Baseline Capital (spec: baseline-capital)

**决策**: 全局基准资金，两个账户共用同一个起点，知行差基于收益率计算。基准调整规则见 design.md 决策 8、9。

### 14.1 ~ 14.8 核心基础设施

- [ ] 14.1 Implement SystemConfig entity (id, baselineCapital, updatedAt)
  - baselineCapital 默认 500000，DECIMAL(18,2)
  - 系统仅需一条配置记录（单例模式）
- [ ] 14.2 Implement SystemConfigService with get/set baselineCapital
- [ ] 14.3 Implement GET/PUT /api/system-config endpoint
- [ ] 14.4 Refactor PlanAccount entity: add cashBalance field (初始 = baselineCapital)
- [ ] 14.5 Refactor ActualAccount entity: add cashBalance field
- [ ] 14.6 Refactor DailySnapshot: 收益率改用 baselineCapital 而非触发价
  - 公式: `planReturn = (currentTotalValue - baselineCapital) / baselineCapital`
  - 快照历史不回填，新配置从新快照生效
- [ ] 14.7 Refactor PeriodSummaryService: 收益率改用 baselineCapital 重算
- [ ] 14.8 Refactor HoldingsService: 浮盈计算改用 baselineCapital 作为基准

### 14.9 前端基准资金与设置页

**设计决策**：无 onboarding 引导。基准资金输入框始终可见于首页顶部；系统设置页展示账户信息。

#### 14.9.1 前端项目初始化

- [ ] 初始化 Vite + React + TypeScript 项目（frontend/）
- [ ] 安装依赖：tailwindcss, @tanstack/react-query, react-router-dom, echarts, echarts-for-react, axios
- [ ] 配置 Tailwind CSS
- [ ] 配置 React Router（路由清单见 frontend-design.md）
- [ ] 配置 React Query（API client、错误处理）
- [ ] 实现目录结构（按 frontend-design.md 目录结构）

#### 14.9.2 基准资金输入框（首页）

- [ ] 实现 `<BaselineCapitalInput>` 组件
  - 位于首页 KPI 卡片上方，始终可见，始终可编辑
  - 默认值从 `GET /api/system-config` 获取
  - 用户修改后点击"保存"触发 `PUT /api/system-config/baseline-capital`
  - **提高基准**：显示提示"提高到 ¥X → 预案+实盘现金同步充值 +Y"
  - **降低基准**：若 `新基准 > PlanAccount.cashBalance` 则显示警告 + 保存按钮禁用
- [ ] 首页 KPI 卡片改用 baselineCapital 口径计算
  - `预案组合收益率 = (预案现金 + 预案持仓市值 - baselineCapital) / baselineCapital × 100%`
  - `实盘组合收益率 = (实盘现金 + 实盘持仓市值 - baselineCapital) / baselineCapital × 100%`
  - `知行差 = 预案组合收益率 - 实盘组合收益率`
- [ ] 走势图数据处理改用 baselineCapital 口径（GET /api/snapshots → 各日期的 planCashBalance / planMarketValue / actualCashBalance / actualMarketValue）
- [ ] 持仓面板浮盈计算：`Σ(shares × currentPrice) - Σ(shares × costPrice)`，预案侧 + 实盘侧独立计算后合并展示

#### 14.9.3 系统设置页

- [ ] 实现 `/settings` 路由页面 `<SettingsPage>`
- [ ] 实现 `<BaselineCapitalForm>` 组件（含实时影响说明）
- [ ] 实现 `<AccountSummary>` 组件（预案账户信息 + 实盘账户信息）
- [ ] 调用 `GET /api/system-config` 获取 baselineCapital
- [ ] 调用 `GET /api/actual-account` 获取 ActualAccount.cashBalance
- [ ] 调用 `GET /api/views/holdings?refresh=true` 获取持仓市值（计算总资产）
- [ ] 保存时调用 `PUT /api/system-config/baseline-capital`

#### 14.9.4 前端类型定义

- [ ] 定义 `SystemConfig` 类型（baselineCapital, updatedAt）
- [ ] 定义 `PlanAccount` 类型（cashBalance）
- [ ] 定义 `ActualAccount` 类型（cashBalance）
- [ ] 定义 `HoldingsResponse` 中 planCashBalance / actualCashBalance 字段

#### 14.9.5 API 层

- [ ] 实现 `api/systemConfig.ts`（GET /api/system-config, PUT /api/system-config/baseline-capital）
- [ ] 实现 `api/actualAccount.ts`（GET /api/actual-account）
- [ ] 实现 `useSystemConfig` hook
- [ ] React Query key 约定：`['system-config'], ['actual-account']`

### 14.10 基准资金联动规则（spec: baseline-capital-override）

- [ ] 14.10.1 实现 PUT /api/system-config/baseline-capital 调整接口
- [ ] 14.10.2 **提高基准**（newBaseline > currentBaseline）：
  - PlanAccount.cashBalance 重置为 newBaseline（直接赋值）
  - ActualAccount.cashBalance 同步重置为 newBaseline（保持两者一致）
  - 操作原子性：与 SystemConfig.baselineCapital 更新在同一事务内
- [ ] 14.10.3 **降低基准可行性检测**（newBaseline < currentBaseline）：
  - 检测条件: `newBaseline <= PlanAccount.cashBalance`
  - 若不通过：返回 HTTP 400，错误信息说明"新基准 {newBaseline} 元超过当前现金余额 {cashBalance} 元，无法降低"
  - 若通过：PlanAccount.cashBalance + ActualAccount.cashBalance 均重置为 newBaseline，SystemConfig.baselineCapital 同步更新
- [ ] 14.10.4 现金回拨逻辑说明：降低基准时仅涉及现金回拨，不变现任何虚拟持仓
- [ ] 14.10.5 基准调整时同步联动 ActualAccount.cashBalance（与 PlanAccount.cashBalance 同步充值/回拨）

### 14.11 现金余额实时同步维护

- [ ] 14.11.1 **PlanAccount.cashBalance 同步扣减**（PlanExecution 买入触发时）
  - `交易金额 = triggerPrice × quantity`
  - 校验: `交易金额 <= PlanAccount.cashBalance`，不足则拒绝触发，返回错误
  - 满足: `PlanAccount.cashBalance -= 交易金额`
- [ ] 14.11.2 **PlanAccount.cashBalance 同步回补**（PlanExecution 卖出触发时）
  - `PlanAccount.cashBalance += 卖出金额`
  - 卖出触发后 plan 进入 CLOSED
- [ ] 14.11.3 **ActualAccount.cashBalance 同步扣减**（ActualTrade 买入录入时）
  - `交易金额 = price × quantity`
  - 校验: `交易金额 <= ActualAccount.cashBalance`，不足则拒绝录入，返回错误
  - 满足: `ActualAccount.cashBalance -= 交易金额`
- [ ] 14.11.4 **ActualAccount.cashBalance 同步回补**（ActualTrade 卖出录入时）
  - `ActualAccount.cashBalance += 卖出金额`
  - 卖出录入后触发 FIFO 匹配计算盈亏
- [ ] 14.11.5 ActualAccount 初始现金录入接口（PUT /api/actual-account/cash-balance）
  - 用户可手动修改现金余额（如录入真实账户的初始资金）
  - 修改后同步影响后续所有收益计算

### 14.12 收益率基准切换

- [ ] 14.12.1 DailySnapshot 收益率改用 baselineCapital 计算
  - 公式: `planReturn = (currentTotalValue - baselineCapital) / baselineCapital × 100%`
  - `currentTotalValue = PlanAccount.cashBalance + Σ(持仓股数 × 当前市价)`
  - 快照历史不回填，新配置从新快照生效
- [ ] 14.12.2 PeriodSummaryService 收益率改用 baselineCapital 重算
  - CLOSED plan 的 returnPercent 基于 baselineCapital 计算
  - HOLDING plan 的浮盈基于 baselineCapital 计算总市值
- [ ] 14.12.3 HoldingsService 浮盈计算改用 baselineCapital
  - 预案侧总浮盈基于 `Σ(持仓股数 × 当前市价) - Σ(持仓股数 × 成本价)`，总市值 = PlanAccount.cashBalance + Σ持仓市值
  - 实盘侧总浮盈同上，基于 ActualAccount.cashBalance + Σ持仓市值
- [ ] 14.12.4 知行差公式不变：`gap = planReturnPct - actualReturnPct`（两端均已改用 baselineCapital）

## 15. 预案触发条件编辑（spec: plan-condition-edit）

**User Story**: 作为用户，我希望在预案触发前（状态为 PENDING 或 HOLDING）能够修改触发条件（MA 周期或目标价格），以便根据市场变化灵活调整策略。

### 15.1 后端支持

- [x] 15.1.1 修改 `PlanDTO.UpdateRequest` 新增 `condition` 字段（类型 `ConditionDTO.CreateRequest`）
- [x] 15.1.2 修改 `PlanService.update()` 放开状态校验
  - 允许 PENDING 和 HOLDING 状态的预案编辑条件
  - 仅当状态为 CLOSED 或 EXPIRED 时拒绝编辑
- [x] 15.1.3 实现条件 Upsert 逻辑
  - 预案已有条件 → 更新已有 `PlanCondition` 记录
  - 预案无条件 → 创建新的 `PlanCondition` 记录
  - 条件验证规则不变（MA 周期 ∈ {5,10,20,60,120,250}，目标价格 > 0）
- [x] 15.1.4 更新单元测试 `PlanServiceTest`
  - 测试 PENDING 状态可编辑条件
  - 测试 HOLDING 状态可编辑条件
  - 测试 CLOSED 状态不可编辑（返回 409）
  - 测试 EXPIRED 状态不可编辑（返回 409）
  - 测试 MA → PRICE 类型切换
  - 测试 PRICE → MA 类型切换

### 15.2 前端支持

- [x] 15.2.1 修改 `UpdatePlanRequest` 类型新增 `condition` 字段
- [x] 15.2.2 修改 `PlanEdit.tsx` 添加条件编辑区域
  - 与 `PlanCreate.tsx` 使用相同的条件表单组件
  - 编辑页加载时显示当前条件值（从 `GET /plans/:id` 响应中获取）
  - 保存时将条件一并提交
- [x] 15.2.3 状态提示
  - PENDING/HOLDING 状态：显示可编辑条件的提示
  - CLOSED/EXPIRED 状态：隐藏条件编辑区域或显示只读

### 15.3 验收标准

```
场景 1: PENDING 状态编辑 MA 周期
  前提: 预案 A 状态 PENDING，条件为 MA20
  操作: 编辑预案，将 MA20 改为 MA10
  期望: 保存成功，条件更新为 MA10

场景 2: HOLDING 状态编辑目标价格
  前提: 预案 B 状态 HOLDING，条件为 PRICE ¥1800
  操作: 编辑预案，将目标价改为 ¥2000
  期望: 保存成功，条件更新为 ¥2000

场景 3: HOLDING 状态切换条件类型
  前提: 预案 C 状态 HOLDING，条件为 MA10
  操作: 编辑预案，将类型改为 PRICE，目标价 ¥1900
  期望: 保存成功，条件类型切换为 PRICE

场景 4: CLOSED 状态不可编辑
  前提: 预案 D 状态 CLOSED
  操作: 尝试编辑条件
  期望: 前端隐藏编辑入口 / 后端返回 409

场景 5: EXPIRED 状态不可编辑
  前提: 预案 E 状态 EXPIRED
  操作: 尝试编辑条件
  期望: 前端隐藏编辑入口 / 后端返回 409
```

---

## 任务依赖关系图

```
14.1 SystemConfig entity
    └─ 14.2 SystemConfigService
        ├─ 14.3 API endpoint
        └─ 14.10.1 ~ 14.10.5 基准联动规则

14.4 PlanAccount entity (cashBalance)
    └─ (PlanAccount.cashBalance 由 14.11.1/14.11.2 维护)
    └─ (PlanAccount.cashBalance 由 14.10.3 引用检测)

14.5 ActualAccount entity
    ├─ 14.9.4 ActualAccount 初始化
    ├─ 14.11.3/14.11.4 ActualAccount.cashBalance 实时同步维护
    └─ 14.10.5 基准调整时同步联动

14.6 DailySnapshot 重算依赖 14.1 + 14.4 + 14.12.1
14.7 PeriodSummary 重算依赖 14.1 + 14.6 + 14.12.2
14.8 Holdings 重算依赖 14.1 + 14.7 + 14.12.3
14.9 前端引导依赖 14.1 + 14.5 + 14.9.4

14.11 PlanAccount.cashBalance 实时同步 → 依赖 14.4
14.12 收益率基准切换 → 依赖 14.1 + 14.4 + 14.5 + 14.11

8.10 幂等保护 独立于 14.x，可在 Phase 3 定时任务阶段并行完成

买卖分离变更独立于 14.x，详见新 change: `buy-sell-separation`

15. 预案条件编辑 独立于 14.x，可独立完成
```
