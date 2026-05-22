# Implementation Tasks

## 1. 数据模型变更

- [x] 1.1 创建 `PlanType` 枚举（BUY, SELL）
- [x] 1.2 修改 `Plan` 实体：
  - 新增 `planType` 字段（必填）
  - 新增 `tradePlanId` 字段（BIGINT）
  - 新增 `buyPlanId` 字段（FK，指向 BUY Plan）
  - 新增 `validUntil` 字段
  - 移除 `isLocked` 字段
- [x] 1.3 修改 `PlanCondition` 实体：
  - 移除 `direction` 字段
  - 移除 `isActive` 字段
- [x] 1.4 修改 `PlanExecution` 实体：
  - 移除 `direction` 字段
  - 移除 `conditionId` 字段
  - 移除 `quantity` 字段
  - 新增 `linkedExecutionId` 字段
- [x] 1.5 添加数据库唯一索引：同一 tradePlanId 下只能有一个非 EXPIRED 状态的 SELL Plan（通过应用层校验实现）

## 2. PlanService 重构

- [x] 2.1 重构 `createPlan` 方法：
  - 支持 `planType` 参数
  - BUY Plan：tradePlanId = this.id
  - SELL Plan：必须指定 buyPlanId，校验 BUY Plan 状态为 HOLDING
- [x] 2.2 添加 `createSellPlan` 方法：
  - 校验关联 BUY Plan 存在且状态为 HOLDING
  - 校验无有效 SELL Plan 存在
  - 继承 BUY Plan 的 stockCode, stockName, cycle, executionQuantity
  - tradePlanId = buyPlanId
- [x] 2.3 添加 `findByTradePlanId` 查询方法
- [x] 2.4 更新状态校验：仅 PENDING 状态的 Plan 可编辑
- [x] 2.5 更新删除校验：仅 PENDING 和 EXPIRED 状态可删除

## 3. PlanExecutionService 重构

- [x] 3.1 重构 `executeBuy` 方法：
  - 从 Plan.planType 判断方向
  - 从 Plan.executionQuantity 获取数量
  - 扣减 PlanAccount.cashBalance
- [x] 3.2 实现 `executeSell` 方法：
  - 增加 PlanAccount.cashBalance（从持仓市值回补）
  - 关联 BUY Plan 也变为 CLOSED
  - 记录 linkedExecutionId
- [x] 3.3 更新有效性检查：PENDING 状态超期 → EXPIRED

## 4. MarketCloseTask 重构

- [x] 4.1 分别处理 BUY Plans 和 SELL Plans
- [x] 4.2 SELL Plan 检查时验证关联 BUY Plan 必须为 HOLDING
- [x] 4.3 SELL Plan 触发后同步关闭关联 BUY Plan

## 5. API 端点更新

- [x] 5.1 `POST /api/plans` 支持 planType 参数
- [x] 5.2 新增 `POST /api/plans/sell` 创建 SELL Plan
- [x] 5.3 `GET /api/plans` 支持 tradePlanId 查询参数
- [x] 5.4 响应 DTO 包含 planType, tradePlanId, buyPlanId

## 6. 单元测试

### 6.1 业务流程测试（PlanServiceBuySellSeparationTest）

- [x] 6.1.1 `createBuyPlan_normal` - BUY Plan 创建成功，planType=BUY，tradePlanId=null
- [x] 6.1.2 `createSellPlan_buyNotHolding_throws` - BUY Plan 未建仓时创建 SELL Plan 抛出异常
- [x] 6.1.3 `createSellPlan_buyHolding_success` - BUY Plan 建仓后创建 SELL Plan 成功
- [x] 6.1.4 `createSellPlan_alreadyExists_throws` - 已存在有效 SELL Plan 时再次创建抛出异常
- [x] 6.1.5 `createSellPlan_firstExpired_secondSucceeds` - 第一个 SELL Plan 过期后可创建第二个
- [x] 6.1.6 `findByTradePlanId_returnsBothPlans` - 通过 tradePlanId 查询返回 buyPlan + sellPlan

### 6.2 状态机与触发逻辑测试（MarketCloseTaskBuySellTest）

- [x] 6.2.1 `buyPlan_trigger_createsExecution` - BUY Plan 触发后创建 Execution，扣减 cashBalance
- [x] 6.2.2 `sellPlan_trigger_createsExecution` - SELL Plan 触发后创建 Execution，回补 cashBalance
- [x] 6.2.3 `sellPlan_trigger_closesBuyPlan` - SELL Plan 触发时关联 BUY Plan 也变为 CLOSED
- [x] 6.2.4 `sellPlan_trigger_buyNotHolding_skipped` - 关联 BUY Plan 不为 HOLDING 时跳过检查
- [x] 6.2.5 `sellPlan_expired_duringValidityCheck` - 有效期届满未触发 → EXPIRED

### 6.3 核心规则验证

| 序号 | 业务规则 | 测试用例 | 验证点 |
|------|----------|----------|--------|
| [x] 6.3.1 | PlanType 枚举 | `planType_enum_values` | BUY/SELL 两个枚举值 |
| [x] 6.3.2 | tradePlanId 自增 | `tradePlanId_equalsBuyPlanId` | SELL Plan 的 tradePlanId = 关联 BUY Plan.id |
| [x] 6.3.3 | 唯一性约束 | `uniqueSellPlan_perTradePlanId` | 同一 tradePlanId 下只能有一个非 EXPIRED 的 SELL Plan |
| [x] 6.3.4 | 持仓数量锁定 | `executionQuantity_lockedFromBuyPlan` | SELL Plan 的 executionQuantity 继承自 BUY Plan |

### 6.4 测试适配

- [x] 6.4.1 适配所有现有测试以支持新的 Entity/DTO 结构
- [x] 6.4.2 移除不兼容的旧测试文件
- [x] 6.4.3 新增 `MarketCloseTaskBuySellTest` 和 `MarketCloseTaskTestFixtures`

**测试运行结果：133 tests passed, 0 failures**

## 7. Fixture 扩展

- [x] 7.1 扩展 `TestFixtures.PlanBuilder`：增加 `planType`、`tradePlanId`、`buyPlan` 字段
- [x] 7.2 扩展 `TestFixtures.ConditionBuilder`：移除 `direction` 字段（由 Plan.planType 表达）
- [x] 7.3 扩展 `TestFixtures.ExecutionBuilder`：移除 `direction` 字段

## 8. 迁移脚本

> 以下任务需要在部署前执行，涉及数据库变更。

- [x] 8.1 编写数据迁移 SQL 脚本（清空重建策略）
- [x] 8.2 备份现有数据到 JSON（`DataMigrationHelper` + `MigrationService`）
- [x] 8.3 DDL 变更（`MigrationService.applyDDLChanges()`）
- [x] 8.4 更新前端代码适配新的 API 响应格式

### 迁移 API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/admin/migration/status` | GET | 获取迁移状态和备份统计 |
| `/api/admin/migration/run` | POST | 执行完整迁移（备份 + DDL + 迁移 + 验证） |
| `/api/admin/migration/health` | GET | 健康检查 |

### 迁移执行流程

```
1. POST /api/admin/migration/run
   ├── 备份数据到 JSON（backups/ 目录）
   ├── 应用 DDL 变更
   │   ├── 添加 plan_type, trade_plan_id, buy_plan_id, valid_until 字段
   │   ├── 删除 is_locked, direction, is_active, condition_id, quantity 字段
   │   └── 创建索引
   ├── 迁移现有 Plan（设为 BUY 类型，trade_plan_id = id）
   └── 验证迁移结果
2. 返回 MigrationResult（成功/失败、备份文件路径、迁移记录数）
```

### 手动备份 SQL（备选方案）

如需手动备份，可使用 `src/main/resources/db/migration/V2__buy_sell_separation.sql`

---

## 任务依赖关系图

```
1.1 PlanType 枚举 ✓
    └─ 1.2 Plan 实体修改 ✓
        ├─ 2.1 PlanService.createPlan 重构 ✓
        └─ 5.1/5.4 API 端点更新 ✓

1.3 PlanCondition 简化 ✓ → 2.1, 7.2
1.4 PlanExecution 简化 ✓ → 3.1, 3.2
1.5 唯一性约束 ✓ → 6.3.3

2.2 createSellPlan ✓ → 6.1.2 ~ 6.1.5
2.3 findByTradePlanId ✓ → 6.1.6
3.1 executeBuy ✓ → 6.2.1
3.2 executeSell ✓ → 6.2.2 ~ 6.2.3
3.3 有效性检查 ✓ → 6.2.4 ~ 6.2.5
4.1 ~ 4.3 MarketCloseTask ✓ → 集成测试覆盖 ✓

7.1 ~ 7.3 测试 Fixture 扩展 ✓ → 6.x 所有测试 ✓
8.1 ~ 8.4 迁移脚本（独立于开发任务，待执行）
```

## 当前进度

- **已完成任务**：1.1-1.5, 2.1-2.5, 3.1-3.3, 4.1-4.3, 5.1-5.4, 6.1-6.3, 6.4.1-6.4.3, 7.1-7.3, 8.1-8.4（**全部完成**）
