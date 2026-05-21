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

**选择**：`tradePlanId` 直接使用 BUY Plan 的自增 ID，BUY Plan 和 SELL Plan 一对一关联。

```java
// BUY Plan 创建时
Plan buyPlan = Plan.builder()
    .tradePlanId(buyPlan.getId())  // 先设为 null，保存后回填
    .planType(PlanType.BUY)
    ...
    .build();
buyPlan.setTradePlanId(buyPlan.getId());  // tradePlanId = 自己的 id
planRepository.save(buyPlan);

// SELL Plan 创建时
Plan sellPlan = Plan.builder()
    .tradePlanId(buyPlan.getId())  // 继承 BUY Plan 的 id
    .planType(PlanType.SELL)
    .buyPlan(buyPlan)              // 关联到 BUY Plan
    ...
    .build();
```

**等价关系**：
```
Plan (BUY):   id = 5,   tradePlanId = 5,  planType = BUY,  buyPlan = null
Plan (SELL):  id = 12,  tradePlanId = 5,  planType = SELL, buyPlan = Plan(5)
```

通过 `tradePlanId = 5` 可查询同一笔交易（买 + 卖）的所有 Plans。

**替代方案**：
- UUID 作为 tradePlanId：需要额外生成，不与任何实体 ID 绑定，增加存储但语义更纯粹。
- 一对多（一个 BUY Plan 对多个 SELL Plans）：当前业务不需要，暂不考虑。

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

    // 校验该 BUY Plan 尚未关联 SELL Plan
    boolean hasSellPlan = planRepository.existsByTradePlanIdAndPlanType(
        buyPlan.getId(), PlanType.SELL);
    if (hasSellPlan) {
        throw new BusinessException("该 BUY Plan 已关联 SELL Plan，不可重复关联");
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

### 决策 4: SELL Plan 状态机 — 独立 4 状态

**选择**：SELL Plan 拥有自己独立的状态机（与 BUY Plan 相同），仅在 BUY Plan 触发后开始检查。

```
SELL Plan 状态机:
PENDING ──卖出条件触发──▶ CLOSED
   │
   └──有效期内未触发──▶ EXPIRED
```

**注意**：SELL Plan 的 PENDING 状态包含两种语义：
1. **等待阶段**：BUY Plan 尚未触发建仓（虽然此时 SELL Plan 不应被创建）
2. **检查阶段**：BUY Plan 已 HOLDING，系统每日检查 SELL 条件是否满足

由于决策 3 要求 BUY Plan 必须 HOLDING 才能创建 SELL Plan，实际上 SELL Plan 创建时即进入"检查阶段"。

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
| 新增 | `tradePlanId` (BIGINT, FK) | 指向 BUY Plan 的 id，同一交易计划共享 |
| 新增 | `buyPlanId` (BIGINT, FK) | SELL Plan 指向 BUY Plan，BUY Plan 此字段为 null |
| 新增 | `validUntil` | SELL Plan 有效期，相对于创建日 |
| 移除 | `isLocked` | 分离后不再需要，PlanType + Status 组合已足够表达可编辑性 |
| 保留 | `executionQuantity` | BUY Plan 和 SELL Plan 各持自己的数量 |

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
| 新增 | `linkedExecutionId` | SELL Plan 触发时，可选记录关联的 BUY Execution ID |

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
// 1. 检查 BUY Plans（现有逻辑，简化）
List<Plan> pendingBuyPlans = planRepository.findByPlanTypeAndStatus(PlanType.BUY, PlanStatus.PENDING);
for (Plan buyPlan : pendingBuyPlans) {
    if (isWithinValidity(buyPlan) && checkCondition(buyPlan.getCondition(), stockData)) {
        executeBuy(buyPlan, stockData);  // status -> HOLDING
    }
}

// 2. 检查 SELL Plans（新增）
List<Plan> pendingSellPlans = planRepository.findByPlanTypeAndStatus(PlanType.SELL, PlanStatus.PENDING);
for (Plan sellPlan : pendingSellPlans) {
    if (!isWithinValidity(sellPlan)) {
        expirePlan(sellPlan);  // status -> EXPIRED
        continue;
    }
    Plan buyPlan = sellPlan.getBuyPlan();
    if (buyPlan.getStatus() != PlanStatus.HOLDING) {
        continue;  // 关联的 BUY Plan 尚未建仓，跳过检查
    }
    if (checkCondition(sellPlan.getCondition(), stockData)) {
        executeSell(sellPlan, buyPlan, stockData);  // status -> CLOSED
        closeLinkedBuyPlan(buyPlan);  // 关联 BUY Plan 也变为 CLOSED
    }
}
```

### 现金操作

```
BUY Plan 触发:
  PlanAccount.cashBalance -= executionQuantity × triggerPrice

SELL Plan 触发:
  PlanAccount.cashBalance += executionQuantity × triggerPrice
  （executionQuantity 来自关联 BUY Plan）
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
- 展示关联的 BUY Plan 的 executionQuantity（不可修改，卖出的数量与买入一致）
- validUntil 默认从今天起 +30 天

```
┌─────────────────────────────────────────────────────────┐
│  创建卖出预案                                    [取消] │
├─────────────────────────────────────────────────────────┤
│  关联买入预案:  600519 贵州茅台  [HOLDING] 建仓日: 5月1日 │
│  卖出数量:      100 股（与买入一致）                     │
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

## Open Questions

1. **BUY Plan 的 executionQuantity 是否允许与 SELL Plan 不同？** 当前设计要求一致（1:1 关系），但实际可能有部分止损的需求（卖出一半）。
2. **SELL Plan 的 validUntil 到期后，BUY Plan 还持股中怎么办？** 是否自动创建新的 SELL Plan，或提示用户手动创建？
3. **BUY Plan 触发后，是否需要显式创建 SELL Plan，还是可以自动生成一个默认的 SELL Plan？**
