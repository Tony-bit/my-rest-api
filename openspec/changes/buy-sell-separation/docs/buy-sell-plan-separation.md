# 预案买卖分离设计

## 背景

当前架构中，一个 `Plan` 实体同时包含买入和卖出条件（通过 `PlanCondition.direction` 区分 BUY/SELL），用单一状态机（PENDING → HOLDING → CLOSED）试图管理整个买卖生命周期。

这种设计存在以下问题：

1. **语义模糊**：一个 Plan 到底是"买入计划"还是"卖出计划"？当 Plan 的 `status = HOLDING` 时，它到底是在"等买入"还是"等卖出"？
2. **创建时机耦合**：必须在一个 Plan 里同时建好买入和卖出条件，无法隔几天再创建卖出计划。
3. **状态机冲突**：BUY 条件和 SELL 条件在同一个 Plan 下各自独立触发，但 Plan-level 的 `status` 只能表达一个状态。
4. **PlanCondition 冗余**：一个 Plan 可以有多个 BUY 条件和多个 SELL 条件，但实际业务中，一个完整的交易只需要一个买入条件和一个卖出条件。

**改进方向**：将 BUY 和 SELL 拆分为独立的 Plan，通过 `tradePlanId` 关联，同一个交易计划（买 + 卖）共享同一个 ID。

---

## 核心设计决策

### 决策 1: Plan 分离 — BUY Plan 与 SELL Plan 各自独立

**选择**：每个 Plan 只能是 BUY 或 SELL 之一，不能同时包含两种方向的条件。

```
旧架构:                          新架构:
────────                         ────────────────
Plan (1)                         Plan (BUY)         Plan (SELL)
  ├─ Condition: BUY MA20            ├─ Condition: BUY MA20    ├─ Condition: SELL MA10
  ├─ Condition: SELL MA10            └─ status: PENDING/HOLDING └─ status: PENDING/CLOSED/EXPIRED
  └─ status: PENDING/HOLDING/CLOSED
```

**理由**：
- 语义清晰：每个 Plan 有明确的交易方向
- 生命周期独立：BUY Plan 和 SELL Plan 各自有独立的状态机
- 灵活性高：可以先建 BUY Plan，等建仓后再创建 SELL Plan（间隔任意天数）

---

### 决策 2: 关联方式 — `tradePlanId` 为 BUY Plan 的自增 ID

**选择**：`tradePlanId` 直接使用 BUY Plan 的自增 ID。BUY Plan 和 SELL Plan 为 **1:1 激活关系**——同一时刻最多只有一个非 EXPIRED 状态的 SELL Plan。

**一个 BUY Plan 可以有多个 SELL Plan**（生命周期内）：
- BUY Plan 建仓成功后创建第一个 SELL Plan
- 如果该 SELL Plan 过期（EXPIRED），可以再创建一个新的 SELL Plan
- 同一时刻，只有一个非 EXPIRED 的 SELL Plan 存在

**校验逻辑**：
```java
// 创建 SELL Plan 时的校验
boolean hasActiveSellPlan = planRepository.existsByTradePlanIdAndPlanTypeAndStatusNot(
    buyPlan.getId(), PlanType.SELL, PlanStatus.EXPIRED);
if (hasActiveSellPlan) {
    throw new BusinessException("该 BUY Plan 已存在有效的 SELL Plan，需先等其过期或手动作废");
}
```

**替代方案**：
- 严格 1:1（整个生命周期只允许一个 SELL Plan）：换仓或改策略时无法重新创建，过于死板。
- 一对多（允许同时存在多个 SELL Plans）：业务场景不需要，增加触发引擎复杂度。

---

### 决策 3: SELL Plan 创建时机 — 必须在关联的 BUY Plan 已 HOLDING 后才能创建

**选择**：SELL Plan 只能在关联的 BUY Plan `status == HOLDING` 时创建。

**校验逻辑**：
```java
public void createSellPlan(SellPlanCreateRequest request) {
    Plan buyPlan = planRepository.findById(request.getBuyPlanId())
        .orElseThrow(() -> new BusinessException("BUY Plan 不存在"));

    if (buyPlan.getStatus() != PlanStatus.HOLDING) {
        throw new BusinessException("关联的 BUY Plan 尚未建仓，无法创建 SELL Plan");
    }

    // 校验：该 tradePlanId 下无有效 SELL Plan（EXPIRED 状态的除外）
    boolean hasActiveSellPlan = planRepository.existsByTradePlanIdAndPlanTypeAndStatusNot(
        buyPlan.getId(), PlanType.SELL, PlanStatus.EXPIRED);
    if (hasActiveSellPlan) {
        throw new BusinessException("该 BUY Plan 已存在有效的 SELL Plan，需先等其过期");
    }

    Plan sellPlan = Plan.builder()
        .tradePlanId(buyPlan.getId())
        .planType(PlanType.SELL)
        .buyPlan(buyPlan)
        .stockCode(buyPlan.getStockCode())
        .stockName(buyPlan.getStockName())
        .cycle(buyPlan.getCycle())
        .executionQuantity(buyPlan.getExecutionQuantity())
        .validUntil(request.getValidUntil())  // 相对于 SELL Plan 创建日
        .conditions(request.getConditions())
        .build();

    return planRepository.save(sellPlan);
}
```

**validUntil 语义**：SELL Plan 的有效期相对于 **SELL Plan 创建日**，而非 BUY Plan 触发日。理由：用户在建仓成功 N 天后才决定建 SELL Plan，从"决定建 SELL" 开始计算有效期更符合用户意图。

**替代方案**：
- SELL Plan 创建时 BUY Plan 可以是任意状态：风险是 BUY Plan 可能最终没有触发建仓，SELL Plan 成为孤儿。
- SELL Plan 创建时 BUY Plan 可以是 PENDING：允许提前规划卖出条件，但同样面临孤儿风险。

---

### 决策 4: SELL Plan 状态机 — 独立 4 状态，支持过期后重建

**选择**：SELL Plan 拥有自己独立的状态机（与 BUY Plan 相同），仅在 BUY Plan 触发后开始检查。

```
SELL Plan 状态机:
PENDING ──卖出条件触发──▶ CLOSED
   │
   └──有效期内未触发──▶ EXPIRED ──重新创建──▶ PENDING（新的 SELL Plan）
```

**生命周期说明**：
1. SELL Plan 创建时状态为 PENDING
2. 每日检查条件，满足则触发 → CLOSED
3. 有效期届满且未触发 → EXPIRED
4. EXPIRED 后，可以重新创建新的 SELL Plan（关联同一个 BUY Plan）

**注意**：SELL Plan 的 PENDING 状态不区分"等待检查"和"尚未激活"——由于创建时 BUY Plan 已是 HOLDING，SELL Plan 创建时即进入检查阶段。

**BUY Plan 状态机**（沿用现有设计）：
```
PENDING ──买入条件触发──▶ HOLDING ──关联的 SELL Plan 触发──▶ CLOSED
   │
   └──有效期内未触发──▶ EXPIRED
```

**BUY Plan 的 CLOSED 触发条件**：当关联的 SELL Plan 触发时，BUY Plan 也变为 CLOSED。这意味着 BUY Plan 的终态不由自己的条件决定，而由 SELL Plan 决定。

---

### 决策 5: PlanCondition 简化 — 一个 Plan 只有一个条件

**选择**：分离后的 Plan 最多只包含一个 `PlanCondition`（BUY Plan 一个买入条件，SELL Plan 一个卖出条件）。

**理由**：
- 一个完整的交易计划本质上只需要一个买入触发条件和一个卖出触发条件
- 多个相同方向的条件的业务价值不明确，反而增加复杂性
- 简化前端和后端逻辑

**PlanCondition 字段调整**：
- 移除 `direction` 字段（Plan.planType 已表达方向）
- PlanCondition.conditionType / maPeriod / targetPrice 保留

---

### 决策 6: PlanExecution 保留，挂在各自 Plan 下

**选择**：PlanExecution 仍挂在 Plan 下，BUY Plan 和 SELL Plan 各自记录自己的执行。

```
Plan (BUY, id=5):
  executions: [PlanExecution(direction=BUY, triggerPrice=100.0)]
  status: HOLDING

Plan (SELL, id=12):
  executions: [PlanExecution(direction=SELL, triggerPrice=105.0)]
  status: CLOSED
```

**现金操作**：
- BUY Plan 触发：扣减 PlanAccount.cashBalance
- SELL Plan 触发：增加 PlanAccount.cashBalance（从持仓市值回补）

SELL Plan 触发时，持仓数量来自关联 BUY Plan 的 executionQuantity。

---

## 数据模型

### Plan 实体变更

| 变更类型 | 字段 | 说明 |
|---------|------|------|
| 新增 | `planType` (ENUM: BUY, SELL) | 必填，区分 Plan 类型 |
| 新增 | `tradePlanId` (BIGINT) | 同组交易计划共享。BUY Plan: tradePlanId = this.id；SELL Plan: tradePlanId = linkedBuyPlan.id |
| 新增 | `buyPlanId` (BIGINT, FK) | SELL Plan 指向 BUY Plan，BUY Plan 此字段为 null |
| 新增 | `validUntil` | SELL Plan 有效期，相对于创建日 |
| 移除 | `isLocked` | 分离后不再需要，PlanType + Status 组合已足够表达可编辑性 |
| 保留 | `executionQuantity` | BUY Plan 持有。SELL Plan 存储同值（锁定），全仓卖出 |

**唯一性约束**：同一 tradePlanId 下，同时最多只有一个非 EXPIRED 状态的 SELL Plan（通过数据库唯一索引 + EXPIRED 状态排除实现）。EXPIRED 状态的 SELL Plan 不受此约束，可重新创建新的 SELL Plan。

### PlanCondition 实体变更

| 变更类型 | 字段 | 说明 |
|---------|------|------|
| 移除 | `direction` | 不再需要，Plan.planType 已表达方向 |
| 移除 | `isActive` | 一个 Plan 只有一个 Condition，不再需要激活标志 |

### PlanExecution 实体变更

| 变更类型 | 字段 | 说明 |
|---------|------|------|
| 移除 | `direction` | 不再需要，Plan.planType 已表达方向 |
| 移除 | `conditionId` | 简化后只有一个条件，不再需要指向具体 condition |
| 移除 | `quantity` | 不再单独存储，BUY Execution 的 quantity 直接继承 Plan.executionQuantity |
| 新增 | `linkedExecutionId` | SELL Plan 触发时，记录关联的 BUY Execution ID |

### 新建 Enum

```java
public enum PlanType {
    BUY,
    SELL
}
```

---

## API 设计

### 创建 BUY Plan

```
POST /api/plans
{
  "name": "贵州茅台买入计划",
  "stockCode": "600519",
  "stockName": "贵州茅台",
  "planType": "BUY",
  "cycle": "WEEKLY",
  "validUntil": "2026-06-30",
  "executionQuantity": 100,
  "condition": {
    "conditionType": "MA",
    "maPeriod": 20
  }
}
```

### 创建 SELL Plan（关联 BUY Plan）

```
POST /api/plans/sell
{
  "buyPlanId": 5,
  "name": "贵州茅台卖出计划",
  "cycle": "WEEKLY",
  "validUntil": "2026-08-31",
  "condition": {
    "conditionType": "MA",
    "maPeriod": 10
  }
}
```

**校验**：
1. buyPlanId 对应的 Plan 存在
2. buyPlanId 对应的 Plan.status == HOLDING
3. 该 tradePlanId 下尚无 SELL Plan

### 查询交易计划（买+卖）

```
GET /api/plans?tradePlanId=5
```

返回：
```json
{
  "tradePlanId": 5,
  "buyPlan": { ... },
  "sellPlan": { ... }
}
```

---

## 触发引擎变更

### 每日收盘检查逻辑

```java
// 1. 检查 BUY Plans
List<Plan> pendingBuyPlans = planRepository.findByPlanTypeAndStatus(PlanType.BUY, PlanStatus.PENDING);
for (Plan buyPlan : pendingBuyPlans) {
    if (isWithinValidity(buyPlan) && checkCondition(buyPlan.getCondition(), stockData)) {
        executeBuy(buyPlan, stockData);  // status -> HOLDING
    }
}

// 2. 检查 SELL Plans
List<Plan> pendingSellPlans = planRepository.findByPlanTypeAndStatus(PlanType.SELL, PlanStatus.PENDING);
for (Plan sellPlan : pendingSellPlans) {
    if (!isWithinValidity(sellPlan)) {
        expirePlan(sellPlan);  // status -> EXPIRED
        continue;
    }
    Plan buyPlan = sellPlan.getBuyPlan();
    // 关键：关联的 BUY Plan 必须是 HOLDING 才检查 SELL 条件
    if (buyPlan.getStatus() != PlanStatus.HOLDING) {
        continue;  // BUY 尚未建仓，跳过
    }
    if (checkCondition(sellPlan.getCondition(), stockData)) {
        executeSell(sellPlan, buyPlan, stockData);  // status -> CLOSED
        // 关联 BUY Plan 也变为 CLOSED（这笔交易完成了）
        buyPlan.setStatus(PlanStatus.CLOSED);
        planRepository.save(buyPlan);
    }
}
```

### 现金操作

```
BUY Plan 触发:
  PlanAccount.cashBalance -= executionQuantity × triggerPrice

SELL Plan 触发:
  PlanAccount.cashBalance += executionQuantity × triggerPrice
  （executionQuantity 锁定为关联 BUY Plan 的值，即全仓卖出）
```

---

## 前端 UX

### 预案列表

```
┌─────────────────────────────────────────────────────────┐
│  预案管理                                            [新建] │
├─────────────────────────────────────────────────────────┤
│  ● 600519 贵州茅台                                     │
│    ├─ [BUY]  MA20  PENDING  创建于 2026-05-01        │
│    └─ [SELL] MA10  --       未创建                     │
├─────────────────────────────────────────────────────────┤
│  ● 000858 五粮液                                       │
│    ├─ [BUY]  MA20  HOLDING 创建于 2026-04-15          │
│    └─ [SELL] MA10  PENDING  创建于 2026-05-10         │
└─────────────────────────────────────────────────────────┘
```

### 创建 SELL Plan 表单

当用户点击"新建 SELL Plan"时：
- 自动带上关联的 BUY Plan 信息（股票代码、名称、建仓日期）
- **不展示数量字段**——默认全仓卖出，executionQuantity 锁定为关联 BUY Plan 的值
- validUntil 默认从今天起 +30 天

```
┌─────────────────────────────────────────────────────────┐
│  创建卖出预案                                    [取消] │
├─────────────────────────────────────────────────────────┤
│  关联买入预案:  600519 贵州茅台  [HOLDING] 建仓日: 5月1日 │
│  卖出数量:      100 股（系统锁定，与买入数量一致）         │
│  周期:          ○日度  ●周度  ○月度                      │
│  有效期至:      [2026-08-31]                             │
│  卖出条件:                                                │
│    ○ 价格触发  目标价格 [____]                           │
│    ● MA均线   周期 [MA10 ▼]                             │
└─────────────────────────────────────────────────────────┘
```

---

## 迁移计划

由于现有数据量少（用户确认可直接删除），采用**清空重建**策略：

1. 备份现有 `plan`、`plan_condition`、`plan_execution` 表数据（导出 JSON）
2. 删除现有数据
3. 执行 DDL 变更（添加字段、删除字段）
4. 用户重新录入预案（从零开始）

---

## 需求澄清摘要

> 以下为设计决策前的关键问题澄清，已与用户确认。

| # | 主题 | 结论 |
|---|------|------|
| 1 | SELL Plan 过期提醒 | **无提醒**，用户自己负责 |
| 2 | 部分卖出支持 | **仅支持全仓卖出**，简洁明确 |
| 3 | BUY Plan CLOSED 展示 | **无需特殊展示**，CLOSED 就是 CLOSED |
| 4 | SELL Plan 数量展示 | **完全不展示数量字段** |
| 5 | BUY Plan 建仓后创建 SELL 感知 | **依赖用户主动**，不做提示 |
| 6 | 预案列表 API 结构 | **保持简单列表**，`tradePlanId` 前端分组 |
| 7 | `triggerDate` 字段处理 | **仅 BUY Plan 保留**，表示建仓日期 |
| 8 | 数据迁移备份 | **完全自动**，迁移脚本自动备份 JSON |
| 9 | 前端技术栈 | **React + TypeScript + Tailwind CSS + React Query**，搭配现有前端做适配和拓展 |

---

## Open Questions

> 以下为设计决策前的关键问题澄清，已与用户确认。

~~1. **BUY Plan 的 executionQuantity 是否允许与 SELL Plan 不同？** 当前设计要求一致（1:1 关系），但实际可能有部分止损的需求（卖出一半）。~~ ✅ **已解答**：不选数量，默认锁死全仓卖（executionQuantity 继承自关联的 BUY Plan，不可修改）。

~~2. **SELL Plan 的 validUntil 到期后，BUY Plan 还持股中怎么办？** 是否自动创建新的 SELL Plan，或提示用户手动创建？~~ ✅ **已解答**：SELL Plan 过期（EXPIRED）后，允许重新创建新的 SELL Plan（关联同一个 BUY Plan）。用户可手动创建新 SELL Plan，系统不做自动处理。

~~3. **BUY Plan 触发后，是否需要显式创建 SELL Plan，还是可以自动生成一个默认的 SELL Plan？**~~ ✅ **已解答**：不能自动生成，必须由用户手动创建。
