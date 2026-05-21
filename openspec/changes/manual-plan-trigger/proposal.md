# Proposal: 手动触发预案评估

## Why

目前系统仅支持每日 17:00 定时执行预案评估，用户无法回溯测试历史日期的预案触发逻辑，也无法补触发因定时任务漏跑而未执行的预案。本功能提供手动触发能力，支持指定任意历史日期（3个月内）的预案执行。

核心价值：**让用户能够回测历史预案表现，补救漏跑的定时任务。**

## What Changes

- **Plan 实体扩展**：新增 `triggerDate` 字段，支持指定历史触发日期
- **手动触发 API**：新增 `POST /api/trigger` 接口，遍历指定日期的所有待执行预案
- **历史 K 线获取**：复用现有 TushareService 获取任意历史日期的日K线数据
- **前端 UI**：
  - 创建预案页新增日期选择器（默认当天，最远可选3个月前）
  - 预案列表页顶部新增"手动触发"按钮
- **幂等保护**：通过 `PlanExecution.executed` 字段防止重复执行

## Capabilities

### New Capabilities

- `manual-trigger`: 手动触发指定日期的预案评估，遍历所有 `triggerDate == targetDate` 且 `executed == false` 的预案，评估条件并执行
- `historical-plan`: 支持创建历史日期的预案，触发日期可指定为任意3个月内的日期

### Modified Capabilities

- `trading-plan`: 新增 `triggerDate` 字段，创建时可指定历史日期

## Impact

- **数据库**：Plan 表新增 `trigger_date` 字段（DATE 类型，可为空，默认当天）
- **API**：新增 `POST /api/trigger` 端点（参数：`targetDate`）
- **前端**：PlanCreate.tsx 新增日期选择器，PlanList.tsx 新增触发按钮
- **定时任务**：现有 `MarketCloseTask` 不受影响，手动触发与定时任务逻辑独立
