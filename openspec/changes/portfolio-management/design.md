## Context

当前系统已有完整的**预案-实盘对比系统**：Plan 实体管理交易预案，ActualTrade 管理实盘记录，DailySnapshot 记录每日持仓快照。数据以扁平列表管理，无分组概念。用户希望引入**组合（Portfolio）**作为更高维度的抽象，支持：
- 按策略风格分组管理预案
- 对比不同组合的收益表现
- 为后续多用户数据隔离预留 `userId` 字段

### 现有数据模型

```
Plan (预案)
├── id, name, stockCode, cycle, planType, status, ...
├── conditions (1:N)
├── executions (1:N)
└── buyPlan (self-reference, for sell plans)

ActualTrade (实盘)
├── id, stockCode, direction, price, quantity, tradeDate, ...
└── profitLoss, profitLossPercent

DailySnapshot (每日快照)
├── id, planId, actualTradeId, snapshotDate, ...
├── planReturn*, actualReturn*
└── cashBalance, marketValue, totalValue
```

### 技术约束

- **语言/框架**: Java 17 + Spring Boot 3.4.3（Maven）
- **持久层**: Spring Data JPA + MySQL
- **现有迁移**: Flyway 管理数据库版本，V1（基础表）、V2（buy_sell_separation）

## Goals / Non-Goals

**Goals:**

- 新增 Portfolio 组合实体，支持 name / description / style / userId / createdAt
- Plan 与 Portfolio 一对多关联（一个 Plan 属于一个 Portfolio）
- Portfolio CRUD API（创建、列表、详情、更新、删除）
- Plan 查询和创建时支持按 portfolioId 筛选和指定
- SELL Plan 自动继承 BUY Plan 的 portfolioId，保证同一交易对在同一组合
- Portfolio 收益概览：收益曲线、总收益、总盈利、总亏损、胜率统计
- Portfolio 维度统计基于 CLOSED Plan 计算
- 已有数据迁移：创建默认组合"预案组合A"，所有现有 Plan 归属该组合
- DailySnapshot 新增 portfolioId 字段，用于收益曲线筛选
- 在 Portfolio、Plan 上预留 `userId` 字段（当前 nullable，后续接认证后可扩展过滤）
- ActualTrade 与 Portfolio 独立，不绑定 portfolioId

**Non-Goals:**

- 不实现用户认证/权限系统（`userId` 仅为预留字段）
- 不实现 Portfolio 级别的资金账户（组合仅做分组和统计，不管理现金）
- 不支持 Portfolio 嵌套（平铺结构）
- ActualTrade 不需要与 Portfolio 关联

## Decisions

### 决策 1: Portfolio 数据模型 — 扁平结构 + userId 预留

**选择**: Portfolio 为独立实体，Plan 通过 `portfolioId` 外键关联。

```
Portfolio (组合)
├── id (PK)
├── name (必填, VARCHAR(100))
├── description (可选, VARCHAR(500))
├── style (自由输入, VARCHAR(50))
├── userId (预留, BIGINT, nullable)
└── createdAt

Plan (预案) [现有实体扩展]
├── portfolioId (FK → portfolio.id, 必填)
└── buyPlanId (继承自 BUY Plan 的 portfolioId)
```

**理由**:
- 扁平结构简单直观，符合当前单用户场景
- `userId` nullable 保证向后兼容，无需强制迁移
- Plan 保留 `portfolioId` 而非完全依赖 Portfolio 层级，便于后续独立扩展

**替代方案**:
- Portfolio 作为 Plan 的一个枚举字段: 不够灵活，风格标签是自由输入
- Portfolio 嵌套层级: 过度复杂，当前无此需求

### 决策 2: Plan-Portfolio 关联 — 必选

**选择**: 新建 Plan 必须指定 portfolioId，已有 Plan 迁移至默认组合。

**理由**:
- 强制关联保证数据完整性，避免孤立 Plan
- 默认组合"预案组合A"承接所有历史数据，平滑迁移

**SELL Plan 继承逻辑**:
- 创建 SELL Plan 时，自动使用对应 BUY Plan 的 portfolioId
- 保证同一交易对（建仓-卖出）在同一组合
- 前端创建 SELL Plan 时不显示组合选择器

### 决策 3: Portfolio 统计计算 — 基于 CLOSED Plan

**选择**: Portfolio 统计基于 CLOSED Plan 计算，收益曲线数据存储在 DailySnapshot 中。

**统计口径**:
| 字段 | 计算方式 | 备注 |
|------|---------|------|
| totalReturn | (持仓市值 + 现金 - 基准资金) / 基准资金 × 100% | HOLDING/CLOSED Plan 计入 |
| totalProfit | 所有盈利 CLOSED Plan 的收益金额之和 | |
| totalLoss | 所有亏损 CLOSED Plan 的收益金额之和 | |
| profitCount | 盈利的 CLOSED Plan 数量 | |
| lossCount | 亏损的 CLOSED Plan 数量 | |
| winRate | profitCount / (profitCount + lossCount) × 100% | |
| planCount | 组合内所有 Plan 总数 | 不限状态 |
| holdingCount | 组合内 HOLDING 状态的 Plan 数量 | |

**不计入统计的状态**: PENDING、EXPIRED

**理由**:
- PENDING 状态尚未触发，不产生实际持仓
- EXPIRED 状态条件过期，无实际成交
- CLOSED 状态为已完成结算，有明确盈亏结果

### 决策 4: 数据库迁移策略 — MigrationService

**选择**: 使用 MigrationService 在应用启动时执行数据迁移。

```java
// MigrationService.java
@PostConstruct
public void migrate() {
    // 1. 确保默认组合存在
    ensureDefaultPortfolioExists();

    // 2. 迁移所有现有 Plan 到默认组合
    migrateExistingPlansToDefaultPortfolio();

    // 3. DailySnapshot 填充 portfolioId (通过 Plan 关联追溯)
    populateDailySnapshotPortfolioId();
}
```

**理由**:
- 相比 Flyway 迁移脚本更安全，支持重试
- 可以包含业务逻辑判断（如检查是否已迁移）
- 与现有 V2 buy_sell_separation 迁移分离

### 决策 5: DailySnapshot portfolioId 字段

**选择**: DailySnapshot 新增 `portfolioId` 字段，用于收益曲线筛选。

```sql
ALTER TABLE daily_snapshot ADD COLUMN portfolio_id BIGINT;
ALTER TABLE daily_snapshot ADD CONSTRAINT fk_snapshot_portfolio
    FOREIGN KEY (portfolio_id) REFERENCES portfolio(id);
```

**收益曲线数据来源**:
- 每日收盘后生成的 DailySnapshot 已包含 `planTotalValue`（预案总市值）
- 按 `portfolioId` 分组汇总可得到组合级别的每日收益曲线
- 迁移时通过 Plan → portfolioId 追溯填充

### 决策 6: 收益曲线时间范围

**选择**: 支持 7天 / 30天 / 全部 三个时间范围。

| 时间范围 | 说明 |
|---------|------|
| 7天 | 最近 7 个交易日 |
| 30天 | 最近 30 个自然日 |
| 全部 | 从组合创建至今 |

### 决策 7: API 设计 — RESTful 扩展

**Portfolio CRUD**:

| Method | Endpoint | 说明 |
|--------|----------|------|
| GET | `/api/portfolios` | 组合列表（含统计） |
| POST | `/api/portfolios` | 创建组合 |
| GET | `/api/portfolios/{id}` | 组合详情（含 Plan 列表） |
| PUT | `/api/portfolios/{id}` | 更新组合 |
| DELETE | `/api/portfolios/{id}` | 删除组合（仅当组合内无 Plan 时允许） |
| GET | `/api/portfolios/{id}/overview` | 组合收益概览（含收益曲线） |

**Plan API 扩展**:

- `GET /api/plans?portfolioId=xxx` — 按组合筛选
- `POST /api/plans` — body 含 `portfolioId`（必填）
- `PUT /api/plans/{id}` — 更新时不可切换 portfolioId
- `POST /api/plans/sell` — SELL Plan 自动继承 BUY Plan 的 portfolioId

### 决策 8: 前端路由结构

**选择**: 在现有预案管理模块下添加组合筛选入口。

```
/plans                      — 预案列表（默认显示全部 + 组合切换下拉）
/plans/new                  — 创建预案（含组合选择下拉）
/plans/:id/edit             — 编辑预案（组合信息只读显示）
/plans/:id                  — 预案详情
/portfolios                 — 组合管理列表
/portfolios/new             — 创建组合
/portfolios/:id             — 组合详情（含收益曲线）
/portfolios/:id/edit        — 编辑组合
```

**理由**: 保持与现有路由结构一致，降低用户认知成本。

### 决策 9: userId 字段使用策略

**当前阶段**（单用户）:
- `userId` 字段存在但不做任何过滤
- 所有查询返回全部数据（WHERE userId IS NULL 或无过滤）

**未来阶段**（接入认证后）:
- 从 SecurityContext 获取当前用户 ID
- 在 Repository 层统一注入 `userId` 过滤条件
- 可通过 AOP/BaseRepository 模式统一处理

## Data Model

### Portfolio 实体

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT (PK) | 主键 |
| name | VARCHAR(100) | 组合名称，必填 |
| description | VARCHAR(500) | 组合描述 |
| style | VARCHAR(50) | 风格标签（自由输入） |
| userId | BIGINT | 用户ID（预留，当前 nullable） |
| createdAt | DATETIME | 创建时间 |

### Plan 实体变更

| 字段 | 类型 | 说明 |
|------|------|------|
| portfolioId | BIGINT (FK) | 所属组合ID（新增，必填） |

### DailySnapshot 实体变更

| 字段 | 类型 | 说明 |
|------|------|------|
| portfolioId | BIGINT (FK) | 所属组合ID（新增，用于收益曲线筛选） |

### ActualTrade 实体（不变）

ActualTrade 与 Portfolio 独立，不存储 portfolioId。

## API Contracts

### GET /api/portfolios

Response:
```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "name": "预案组合A",
      "description": "默认组合",
      "style": "趋势跟踪",
      "planCount": 12,
      "holdingCount": 2,
      "totalReturn": 15.32,
      "totalProfit": 25600.00,
      "totalLoss": -8200.00,
      "profitCount": 8,
      "lossCount": 4,
      "winRate": 67.0
    }
  ]
}
```

### GET /api/portfolios/{id}

Response:
```json
{
  "code": 200,
  "data": {
    "id": 1,
    "name": "预案组合A",
    "description": "默认组合",
    "style": "趋势跟踪",
    "createdAt": "2026-05-01T10:00:00",
    "summary": {
      "planCount": 12,
      "holdingCount": 2,
      "totalReturn": 15.32,
      "totalProfit": 25600.00,
      "totalLoss": -8200.00,
      "profitCount": 8,
      "lossCount": 4,
      "winRate": 67.0
    },
    "plans": [
      {
        "id": 1,
        "name": "比亚迪建仓",
        "stockCode": "002594",
        "planType": "BUY",
        "status": "HOLDING",
        "returnPercent": 5.32
      }
    ]
  }
}
```

### GET /api/portfolios/{id}/overview

Response:
```json
{
  "code": 200,
  "data": {
    "portfolio": {
      "id": 1,
      "name": "预案组合A",
      "description": "默认组合",
      "style": "趋势跟踪",
      "createdAt": "2026-01-15T10:00:00"
    },
    "summary": {
      "totalReturn": 15.32,
      "totalProfit": 25600.00,
      "totalLoss": -8200.00,
      "profitCount": 8,
      "lossCount": 4,
      "winRate": 67.0,
      "planCount": 12,
      "holdingCount": 2
    },
    "returnCurve": [
      { "date": "2026-05-15", "value": 12.50 },
      { "date": "2026-05-16", "value": 13.20 },
      { "date": "2026-05-17", "value": 15.32 }
    ]
  }
}
```

Query Parameters:
| 参数 | 类型 | 说明 |
|------|------|------|
| range | string | 时间范围: `7d` / `30d` / `all` (默认 `30d`) |

### GET /api/plans?portfolioId=1

Response:
```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "name": "比亚迪建仓",
      "portfolioId": 1,
      "stockCode": "002594",
      "planType": "BUY",
      "status": "HOLDING",
      "returnPercent": 5.32,
      "createdAt": "2026-05-01T10:00:00"
    }
  ]
}
```

### POST /api/plans

Request:
```json
{
  "name": "比亚迪建仓",
  "stockCode": "002594",
  "stockName": "比亚迪",
  "cycle": "SHORT",
  "planType": "BUY",
  "portfolioId": 1,
  "condition": {
    "conditionType": "MA",
    "maPeriod": 20
  }
}
```

### POST /api/plans/sell

Request:
```json
{
  "buyPlanId": 1,
  "name": "比亚迪卖出",
  "cycle": "SHORT",
  "condition": {
    "conditionType": "MA",
    "maPeriod": 60
  }
}
```

Response:
```json
{
  "code": 200,
  "data": {
    "id": 2,
    "name": "比亚迪卖出",
    "portfolioId": 1,
    "buyPlanId": 1,
    "planType": "SELL",
    "status": "PENDING"
  }
}
```

**注意**: SELL Plan 的 `portfolioId` 自动继承自 `buyPlanId` 对应的 BUY Plan，无需前端传入。

## Portfolio 详情页设计

```
┌─────────────────────────────────────────────────────────────────────────┐
│  ← 返回  |  预案组合A                                           编辑    │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  趋势跟踪 · 创建于 2026-01-15                                          │
│                                                                         │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  [7天]  [30天]  [全部]                                          │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  收益曲线                                                                │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                                                                 │   │
│  │    +15% ────────────────────────────●                          │   │
│  │          ╲                      ╱                               │   │
│  │    +10% ──╲────────────────────╱────────────────────           │   │
│  │            ╲                ╱                                    │   │
│  │     +5% ───╲──────────────╱───────────────────────           │   │
│  │              ╲          ╱                                         │   │
│  │      0% ─────╲────────╱─────────────────────────────         │   │
│  │               ╲      ╱                                            │   │
│  │    -5% ───────●─────╱───────────────────────────────         │   │
│  │                  ↑                                               │   │
│  │                 today                                            │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  收益统计                                                                │
│  ┌────────────────────┐  ┌────────────────────┐  ┌────────────────┐ │
│  │ 总收益              │  │ 总盈利              │  │ 总亏损          │ │
│  │ +15.32%            │  │ +¥25,600           │  │ -¥8,200        │ │
│  └────────────────────┘  └────────────────────┘  └────────────────┘ │
│  ┌────────────────────┐  ┌────────────────────┐                     │
│  │ 胜率                │  │ 预案数              │                     │
│  │ 67% (8/12)        │  │ 12 个              │                     │
│  └────────────────────┘  └────────────────────┘                     │
│                                                                         │
│  预案列表                                                                │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │  状态         名称              股票      收益        操作       │   │
│  │  ────────────────────────────────────────────────────────────  │   │
│  │  🟢持仓       比亚迪建仓        002594    +5.32%     查看详情   │   │
│  │  🔴持仓       宁德时代建仓      300750    +12.80%    查看详情   │   │
│  │  🟡待执行     隆基绿能建仓      601012    —          查看详情   │   │
│  │  ⚪已过期     中芯国际建仓      688981    —          查看详情   │   │
│  │  ✅已结算     万科A建仓         000002    -3.20%     查看详情   │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

## Risks / Trade-offs

| Risk | Severity | Mitigation |
|------|----------|------------|
| 删除 Portfolio 时仍有 Plan 归属 | 中 | API 层面校验并返回 409 Conflict，提示用户先迁移 Plan |
| userId 字段分散在多个实体 | 低 | 预留阶段仅做字段添加，不改变查询逻辑；后续通过 BaseRepository 统一注入 |
| 迁移脚本执行失败 | 中 | MigrationService 支持幂等操作，可重试 |
| Portfolio 统计计算性能 | 低 | Portfolio 统计为实时聚合查询，数据量小（百级 Plan）时无性能问题 |
| 前端组合切换体验断点 | 低 | PlanController.list() 默认返回全部 Plan，需明确告知前端传参 |
| DailySnapshot 追溯填充 portfolioId | 中 | 迁移时按 Plan → portfolioId 批量更新，事务保证一致性 |

## Migration Plan

1. **Phase 1（数据库迁移）**: Flyway 迁移脚本 V3__portfolio.sql
   - 创建 portfolio 表
   - Plan 表加 portfolio_id 外键
   - DailySnapshot 表加 portfolio_id 字段
   - Portfolio、Plan 加 user_id 字段

2. **Phase 2（数据迁移）**: MigrationService 启动时执行
   - 创建默认组合 "预案组合A" (id=1)
   - 迁移所有现有 Plan 的 portfolioId = 1
   - 追溯填充 DailySnapshot 的 portfolioId（通过 Plan 关联）

3. **Phase 3（后端 CRUD）**: 实现 Portfolio 实体、Repository、Service、Controller

4. **Phase 4（Plan 关联）**: Plan API 支持 portfolioId 参数，SELL Plan 继承逻辑

5. **Phase 5（收益概览）**: PortfolioService 实现收益曲线计算和统计聚合

6. **Phase 6（前端页面）**: 组合管理列表、详情页（含收益曲线）、创建/编辑页

7. **Phase 7（测试验证）**: 单元测试 + 集成测试覆盖所有场景

**回滚策略**: 
- MigrationService 支持幂等操作，重启后可重新执行
- 数据库迁移通过 Flyway 版本管理，支持降级

## Open Questions

（已全部解决）
