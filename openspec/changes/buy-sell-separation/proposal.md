# Proposal: 预案买卖分离

## 摘要

将现有的单一 Plan 拆分为 BUY Plan 和 SELL Plan 两个独立实体，通过 `tradePlanId` 关联。解决当前设计中语义模糊、创建时机耦合、状态机冲突等问题。

## 问题陈述

当前架构中，一个 `Plan` 实体同时包含买入和卖出条件（通过 `PlanCondition.direction` 区分 BUY/SELL），存在以下问题：

1. **语义模糊**：Plan 到底是"买入计划"还是"卖出计划"
2. **创建时机耦合**：必须在一个 Plan 里同时建好买入和卖出条件
3. **状态机冲突**：BUY 和 SELL 条件在同一个 Plan 下各自独立触发
4. **PlanCondition 冗余**：一个 Plan 可以有多个 BUY/SELL 条件

## 解决方案

采用 BUY/SELL 分离设计：
- 每个 Plan 只能是 BUY 或 SELL 之一
- 通过 `tradePlanId` 关联同一交易计划（买+卖）
- SELL Plan 必须在 BUY Plan HOLDING 后才能创建
- SELL Plan 有独立的状态机，支持过期后重建

## 影响范围

- **数据模型**：Plan, PlanCondition, PlanExecution 实体变更
- **服务层**：PlanService, PlanExecutionService, MarketCloseTask 重构
- **API 层**：新增 SELL Plan 创建接口
- **测试**：完整覆盖新的业务流程和状态机

## 资源需求

- 后端开发：3-4 天
- 测试：2 天
- 前端适配：1 天

## 验收标准

- BUY Plan 和 SELL Plan 可独立创建和管理
- SELL Plan 创建时校验 BUY Plan 必须为 HOLDING
- SELL Plan 触发后同步关闭关联 BUY Plan
- 所有新增接口有单元测试覆盖
