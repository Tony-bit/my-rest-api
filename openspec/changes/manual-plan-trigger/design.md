# Design: 手动触发预案评估

## Context

系统现有 `MarketCloseTask` 每日 17:00 自动执行预案评估，但存在以下限制：
1. 无法测试历史日期的触发逻辑
2. 定时任务漏跑时无法补救
3. 用户无法创建并立即触发历史日期的预案

本设计扩展 `Plan` 实体支持 `triggerDate` 字段，并新增手动触发 API，实现历史日期的预案回测能力。

## Goals / Non-Goals

**Goals:**
- 支持创建指定历史日期（3个月内）的预案
- 支持手动触发指定日期的所有待执行预案
- 通过 `PlanExecution.executed` 实现幂等保护
- 前端提供日期选择器和触发按钮

**Non-Goals:**
- 不修改现有定时任务逻辑
- 不支持跨事务的并发冲突处理（定时任务与手动触发不同时运行）
- 不支持快照的历史回溯（快照仍记录到触发日期）

## Decisions

### Decision 1: Plan 实体新增 `triggerDate` 字段

**选项 A**: 新增 `triggerDate` 字段（DATE，可为空）
- 优点：语义清晰，查询方便
- 缺点：需要迁移数据库

**选项 B**: 通过 `triggerDate` 参数传递，不存储
- 优点：无需数据库变更
- 缺点：无法查询历史创建的预案

**选择**: 选项 A。新增 `trigger_date` 字段，默认为当天（当前日期）。

### Decision 2: 手动触发 API 设计

**选项 A**: `POST /api/plans/trigger` + body `{ targetDate }`
- 优点：RESTful，批量触发

**选项 B**: `POST /api/trigger?date=2026-04-22`
- 优点：简单
- 缺点：URL 参数有长度限制

**选择**: 选项 A。请求体包含 `targetDate`，响应包含执行结果详情。

### Decision 3: 历史 K 线获取

复用现有 `TushareService`，调用时传入 `start_date` 和 `end_date`（相同日期）获取单日数据。

### Decision 4: 触发结果返回结构

```json
{
  "targetDate": "2026-04-22",
  "totalPlans": 5,
  "triggered": 2,
  "skipped": 3,
  "details": [
    { "planId": 1, "stockName": "贵州茅台", "status": "triggered" },
    { "planId": 2, "stockName": "宁德时代", "status": "already_executed" },
    { "planId": 3, "stockName": "比亚迪", "status": "condition_not_met" }
  ]
}
```

## Risks / Trade-offs

[Risk] Tushare 历史数据可能缺失（停牌日、节假日）
→ **Mitigation**: 跳过无数据的日期，触发结果中标记 `data_unavailable`

[Risk] 历史日期早于预案创建日期
→ **Mitigation**: 前端限制日期选择范围（3个月内），不处理此边界情况

## Open Questions

1. 是否需要记录手动触发操作的审计日志？
2. 触发结果中的 `condition_not_met` 是否需要显示具体原因？
