# 测试用例文档

## 概述

- **框架**: JUnit 5 + Mockito（纯单元测试）
- **测试层**: Service 层（不含 Controller）
- **外部依赖策略**: Tushare 全量 mock；数据库依赖 Repository mock，不启动真实 DB
- **测试文件位置**: `src/test/java/com/example/myapi/service/`
- **Fixture 策略**: 共享的 `TestFixtures` 类预置标准测试数据 builder

---

## 测试 Fixture 规范

```java
// 位置: src/test/java/com/example/myapi/service/TestFixtures.java
// 所有测试共享，字段值固定为便于心算的数字
```

| Fixture 名称 | 用途 | 关键字段 |
|---|---|---|
| `PlanBuilder` | Plan 实体，默认 PENDING，未锁定 | stockCode="000001", name="测试预案", cycle=DAILY |
| `PlanConditionBuilder` | PlanCondition，默认激活 | type=PRICE, direction=BUY, targetPrice=10.00 |
| `PlanExecutionBuilder` | PlanExecution，已触发 | triggerPrice=10.00, direction=BUY, triggered=true |
| `ActualTradeBuilder` | ActualTrade，买入 | stockCode="000001", price=10.00, qty=100 |
| `KLineDataBuilder` | Tushare K 线数据 | close=11.00, high=11.50, low=10.50, vol=1000000 |

> **数值约定**: 所有价格/数量设为方便计算的值。如无特殊说明：
> - 买入价: `10.00`
> - 当前价/卖出价: `11.00`（对应 +10% 收益）
> - 数量: `100`（或 100 的整倍数）
> - MA 周期: `5`（最快均线，避免长回填）

---

## 一、TushareServiceTest

### 依赖 mock
- `TushareConfig` — `getToken()` → `"test_token"`, `getBaseUrl()` → `"http://test"`, `getTimeout()` → `5000`

### 1.1 `getDailyKLine` — 正常返回

| ID | TC-1.1.1 |
|---|---|
| 描述 | 指定日期有数据时，返回完整 KLineData |
| Mock | `callApi` → JSON 含 `items: [[..., 10.5, 11.0, 11.5, 12.0, 1000000]]`（下标: 2=open, 3=high, 4=low, 5=close, 6=vol）|
| 验证 | `stockCode="000001.SZ"`, `date=2026-05-20`, `close=12.0`, `high=11.5`, `low=11.0`, `open=10.5` |

| ID | TC-1.1.2 |
|---|---|
| 描述 | 指定日期无数据时，返回 Optional.empty() |
| Mock | `callApi` → 返回 `{items: []}` |
| 验证 | 返回 `Optional.empty()` |

| ID | TC-1.1.3 |
|---|---|
| 描述 | usePreFQ=true 时，请求参数包含 `fq=pre` |
| Mock | `callApi` 捕获 params |
| 验证 | params 包含 `fq=pre` |
| 备注 | 仅验证 params 被正确传递，不验证 Tushare 行为 |

| ID | TC-1.1.4 |
|---|---|
| 描述 | Tushare API 抛异常时，返回 Optional.empty() |
| Mock | `callApi` 抛 `IOException` |
| 验证 | 返回 `Optional.empty()`，无异常上抛 |

### 1.2 `calculateMA` — 均线计算

| ID | TC-1.2.1 |
|---|---|
| 描述 | 返回周期内收盘价的简单移动平均 |
| Mock | `callApi` 返回 N 条 K 线，close 价格分别为 `10,11,12,13,14` |
| 输入 | period=5, date=2026-05-20 |
| 验证 | 结果 = (10+11+12+13+14)/5 = 12.00，精度 4 位小数 |

| ID | TC-1.2.2 |
|---|---|
| 描述 | 不足 N 天数据时，向前回溯补齐 |
| Mock | 当天 `callApi` 返回 2 条，向前逐日 `getDailyKLine` 补足 5 条 |
| 验证 | 累计调用 `getDailyKLine` 至少 3 次，最终返回 MA 值 |

| ID | TC-1.2.3 |
|---|---|
| 描述 | 回溯超过 1 年仍数据不足时，返回 null |
| Mock | `getDailyKLine` 每次返回 `Optional.empty()` |
| 验证 | `calculateMA` 返回 null |

| ID | TC-1.2.4 |
|---|---|
| 描述 | 计算 MA5，使用 `fq=pre` 参数 |
| Mock | `callApi` 捕获 params |
| 验证 | params 中包含 `fq=pre` |

| ID | TC-1.2.5 |
|---|---|
| 描述 | Tushare API 失败时，吞异常并返回 null |
| Mock | `callApi` 抛异常 |
| 验证 | 返回 null，无异常上抛 |

### 1.3 `evaluateCondition` — 触发条件判断

| ID | TC-1.3.1 |
|---|---|
| 描述 | PRICE 买入：收盘价 ≥ 目标价，触发 |
| 构造 | condition: PRICE, BUY, targetPrice=10.00；kData: close=11.00 |
| 验证 | 返回 `true` |

| ID | TC-1.3.2 |
|---|---|
| 描述 | PRICE 买入：收盘价 < 目标价，不触发 |
| 构造 | condition: PRICE, BUY, targetPrice=12.00；kData: close=11.00 |
| 验证 | 返回 `false` |

| ID | TC-1.3.3 |
|---|---|
| 描述 | PRICE 卖出：收盘价 ≤ 目标价，触发 |
| 构造 | condition: PRICE, SELL, targetPrice=12.00；kData: close=11.00 |
| 验证 | 返回 `true` |

| ID | TC-1.3.4 |
|---|---|
| 描述 | PRICE 卖出：收盘价 > 目标价，不触发 |
| 构造 | condition: PRICE, SELL, targetPrice=10.00；kData: close=11.00 |
| 验证 | 返回 `false` |

| ID | TC-1.3.5 |
|---|---|
| 描述 | MA 买入：收盘价 ≥ MA(N)，触发 |
| 构造 | condition: MA, BUY, maPeriod=5；kData: close=12.00；maValue=11.00 |
| 验证 | 返回 `true` |

| ID | TC-1.3.6 |
|---|---|
| 描述 | MA 买入：收盘价 < MA(N)，不触发 |
| 构造 | condition: MA, BUY；kData: close=10.00；maValue=11.00 |
| 验证 | 返回 `false` |

| ID | TC-1.3.7 |
|---|---|
| 描述 | MA 卖出：收盘价 ≤ MA(N)，触发 |
| 构造 | condition: MA, SELL；kData: close=10.00；maValue=11.00 |
| 验证 | 返回 `true` |

| ID | TC-1.3.8 |
|---|---|
| 描述 | MA 卖出：收盘价 > MA(N)，不触发 |
| 构造 | condition: MA, SELL；kData: close=12.00；maValue=11.00 |
| 验证 | 返回 `false` |

| ID | TC-1.3.9 |
|---|---|
| 描述 | MA 类型但 maValue 为 null 时，不触发 |
| 构造 | condition: MA, BUY；maValue=null |
| 验证 | 返回 `false` |

| ID | TC-1.3.10 |
|---|---|
| 描述 | 边界：收盘价等于 MA 值（站上），触发买入 |
| 构造 | condition: MA, BUY；kData: close=11.00；maValue=11.00 |
| 验证 | 返回 `true`（`>=` 语义） |

| ID | TC-1.3.11 |
|---|---|
| 描述 | 边界：收盘价等于目标价（站上），触发买入 |
| 构造 | condition: PRICE, BUY；kData: close=10.00；targetPrice=10.00 |
| 验证 | 返回 `true`（`>=` 语义） |

### 1.4 `isTradingDay` — 交易日判断

| ID | TC-1.4.1 |
|---|---|
| 描述 | Tushare 返回 is_open=1，返回 true |
| Mock | `callApi` 返回 `{items: [[..., "1"]], fields: ["...", "is_open"]}` |
| 验证 | 返回 `true` |

| ID | TC-1.4.2 |
|---|---|
| 描述 | Tushare 返回 is_open=0，返回 false |
| Mock | `callApi` 返回 `{items: [[..., "0"]], fields: ["...", "is_open"]}` |
| 验证 | 返回 `false` |

| ID | TC-1.4.3 |
|---|---|
| 描述 | Tushare API 调用失败时，默认返回 true（不阻塞定时任务） |
| Mock | `callApi` 抛异常 |
| 验证 | 返回 `true` |

| ID | TC-1.4.4 |
|---|---|
| 描述 | Tushare 返回空数据时，默认返回 true |
| Mock | `callApi` 返回 `{items: []}` |
| 验证 | 返回 `true` |

---

## 二、PlanServiceTest

### 依赖 mock
- `PlanRepository`

### 2.1 `create` — 预案创建

| ID | TC-2.1.1 |
|---|---|
| 描述 | 正常创建含条件的预案 |
| 构造 | 请求含 2 个条件：PRICE 买入 + MA 卖出 |
| 验证 | `planRepository.save()` 被调用 1 次；返回 plan.id 非空；conditions.size()=2 |

| ID | TC-2.1.2 |
|---|---|
| 描述 | 创建时不带条件，conditions 为空 |
| 验证 | plan 保存成功，conditions 列表为空 |

| ID | TC-2.1.3 |
|---|---|
| 描述 | MA 条件 maPeriod 不在允许集合内，抛异常 |
| 构造 | maPeriod=7（无效） |
| 验证 | 抛 `BusinessException`，消息含 "MA 均线周期必须是 5/10/20/60/120/250" |

| ID | TC-2.1.4 |
|---|---|
| 描述 | PRICE 条件 targetPrice <= 0，抛异常 |
| 构造 | targetPrice=0 |
| 验证 | 抛 `BusinessException`，消息含 "大于 0" |

| ID | TC-2.1.5 |
|---|---|
| 描述 | PRICE 条件 targetPrice 为 null，抛异常 |
| 验证 | 抛 `BusinessException` |

| ID | TC-2.1.6 |
|---|---|
| 描述 | conditionType 为 null，抛异常 |
| 验证 | 抛 `BusinessException`，消息含 "conditionType" |

| ID | TC-2.1.7 |
|---|---|
| 描述 | direction 为 null，抛异常 |
| 验证 | 抛 `BusinessException`，消息含 "direction" |

### 2.2 `update` — 预案更新

| ID | TC-2.2.1 |
|---|---|
| 描述 | PENDING 预案可正常编辑 |
| 构造 | plan.status=PENDING |
| 验证 | `planRepository.save()` 被调用；返回更新后的 plan |

| ID | TC-2.2.2 |
|---|---|
| 描述 | HOLDING 预案不可编辑，抛 409 |
| 构造 | plan.status=HOLDING |
| 验证 | 抛 `BusinessException`，statusCode=409 |

| ID | TC-2.2.3 |
|---|---|
| 描述 | CLOSED 预案不可编辑，抛 409 |
| 验证 | 抛 `BusinessException`，statusCode=409 |

| ID | TC-2.2.4 |
|---|---|
| 描述 | EXPIRED 预案不可编辑，抛 409 |
| 验证 | 抛 `BusinessException`，statusCode=409 |

| ID | TC-2.2.5 |
|---|---|
| 描述 | 只传部分字段，只更新非 null 字段 |
| 构造 | 只传 `name` 为 "新名称"，其他 null |
| 验证 | plan.name="新名称"，其余字段不变 |

| ID | TC-2.2.6 |
|---|---|
| 描述 | 更新时修改 cycle |
| 验证 | cycle 被正确更新 |

### 2.3 `delete` — 预案删除

| ID | TC-2.3.1 |
|---|---|
| 描述 | PENDING 预案可删除 |
| 构造 | plan.status=PENDING |
| 验证 | `planRepository.delete()` 被调用 |

| ID | TC-2.3.2 |
|---|---|
| 描述 | EXPIRED 预案可删除 |
| 验证 | `planRepository.delete()` 被调用 |

| ID | TC-2.3.3 |
|---|---|
| 描述 | HOLDING 预案不可删除，抛 409 |
| 验证 | 抛 `BusinessException`，statusCode=409；`delete()` 不被调用 |

| ID | TC-2.3.4 |
|---|---|
| 描述 | CLOSED 预案不可删除，抛 409 |
| 验证 | 抛 `BusinessException`，statusCode=409 |

| ID | TC-2.3.5 |
|---|---|
| 描述 | 删除不存在的预案，抛 404 |
| Mock | `findById` → empty |
| 验证 | 抛 `BusinessException`，statusCode=404 |

### 2.4 `list` — 查询

| ID | TC-2.4.1 |
|---|---|
| 描述 | 无过滤条件，返回全部 |
| 验证 | `planRepository.findAll()` 被调用 |

| ID | TC-2.4.2 |
|---|---|
| 描述 | 只传 status 过滤 |
| 验证 | `planRepository.findByStatus()` 被调用 |

| ID | TC-2.4.3 |
|---|---|
| 描述 | 只传 stockCode 过滤 |
| 验证 | `planRepository.findByStockCode()` 被调用 |

| ID | TC-2.4.4 |
|---|---|
| 描述 | status 和 stockCode 同时过滤 |
| 验证 | `planRepository.findByStatusAndStockCode()` 被调用 |

### 2.5 `findActivePlans`

| ID | TC-2.5.1 |
|---|---|
| 描述 | 只返回 PENDING 和 HOLDING 状态的 plan |
| 验证 | `planRepository.findByStatusIn([PENDING, HOLDING])` 被调用 |

---

## 三、PlanExecutionServiceTest

### 依赖 mock
- `PlanExecutionRepository`
- `PlanRepository`

### 3.1 `recordExecution` — 实施记录

| ID | TC-3.1.1 |
|---|---|
| 描述 | 正常记录一条执行，含 triggerPrice/closePrice/maValue |
| 构造 | triggerPrice=10.00, closePrice=10.05, maValue=null, conditionId 有值 |
| 验证 | `executionRepository.save()` 被调用 1 次；execution.direction=BUY, triggered=true, executed=true |

| ID | TC-3.1.2 |
|---|---|
| 描述 | conditionId 为 null 时，condition 字段为 null（不抛异常） |
| 验证 | `executionRepository.save()` 正常完成 |

| ID | TC-3.1.3 |
|---|---|
| 描述 | conditionId 存在时，正确关联到 plan.conditions 中的对应条件 |
| 验证 | 保存的 execution.condition 字段不为 null |

### 3.2 `transitionState` — 状态转换

| ID | TC-3.2.1 |
|---|---|
| 描述 | PENDING → HOLDING：状态变更且 isLocked=true |
| 构造 | newStatus=HOLDING，plan 当前 PENDING |
| 验证 | `plan.setStatus(HOLDING)` + `plan.setIsLocked(true)`；`planRepository.save()` 被调用 |

| ID | TC-3.2.2 |
|---|---|
| 描述 | HOLDING → CLOSED：只改状态，isLocked 不变 |
| 构造 | newStatus=CLOSED，plan 当前 HOLDING，isLocked=true |
| 验证 | `plan.setStatus(CLOSED)`；`isLocked` 保持 true；`planRepository.save()` 被调用 |

| ID | TC-3.2.3 |
|---|---|
| 描述 | 任意状态转换后 plan 均被保存 |
| 验证 | `planRepository.save(plan)` 被调用 1 次 |

### 3.3 `calculateCurrentReturn` — 收益计算

| ID | TC-3.3.1 |
|---|---|
| 描述 | 有买入记录，当前价高于成本，返回正收益百分比 |
| 构造 | 1 笔 BUY: triggerPrice=10.00；currentPrice=11.00 |
| 验证 | 结果 ≈ +10.0000%（公式：(11-10)/10 * 100）|

| ID | TC-3.3.2 |
|---|---|
| 描述 | 当前价低于成本，返回负收益百分比 |
| 构造 | 1 笔 BUY: triggerPrice=10.00；currentPrice=9.00 |
| 验证 | 结果 ≈ -10.0000% |

| ID | TC-3.3.3 |
|---|---|
| 描述 | 多笔买入，取平均成本 |
| 构造 | BUY1: triggerPrice=10.00，BUY2: triggerPrice=20.00；currentPrice=15.00 |
| 验证 | avgCost=15.00，结果 = 0% |

| ID | TC-3.3.4 |
|---|---|
| 描述 | 无买入记录，返回 0 |
| 验证 | 结果 = 0.0000 |

### 3.4 `getByPlanId`

| ID | TC-3.4.1 |
|---|---|
| 描述 | planId 不存在，抛 404 |
| Mock | `planRepository.existsById(id)` → false |
| 验证 | 抛 `BusinessException`，statusCode=404 |

| ID | TC-3.4.2 |
|---|---|
| 描述 | planId 存在，返回按 tradeDate ASC 排序的执行记录 |
| 验证 | `executionRepository.findByPlanIdOrderByTradeDateAsc()` 被调用 |

---

## 四、ActualTradeServiceTest

### 依赖 mock
- `ActualTradeRepository`

### 4.1 `create` — 实盘记录创建

| ID | TC-4.1.1 |
|---|---|
| 描述 | 创建买入记录，quantity=100, price=10.00 |
| 验证 | `tradeRepository.save()` 被调用 1 次；direction=BUY；profitLoss=null |

| ID | TC-4.1.2 |
|---|---|
| 描述 | 创建卖出记录，触发 FIFO 匹配 |
| 构造 | 已有 1 笔未匹配买入: BUY price=10.00 qty=100；新建 SELL price=12.00 qty=50 |
| 验证 | `matchAndCalculateProfitLoss()` 被调用；sell.profitLoss=+100.00（(12-10)*50）；sell.isMatched=true |

| ID | TC-4.1.3 |
|---|---|
| 描述 | 卖出时无匹配买入，记录警告日志但不抛异常 |
| 构造 | 无未匹配买入 |
| 验证 | 卖单正常保存，profitLoss=null，`log.warn` 被调用 |

### 4.2 FIFO 匹配核心场景

| ID | TC-4.2.1 |
|---|---|
| 描述 | 单笔卖单匹配单笔买入 |
| 构造 | BUY1: price=10, qty=100；SELL: price=12, qty=100 |
| 验证 | avgCost=10.00，profitLoss=(12-10)*100=200.00，profitLossPercent=20% |

| ID | TC-4.2.2 |
|---|---|
| 描述 | 单笔卖单匹配多笔买入（FIFO） |
| 构造 | BUY1: 1月1日 price=10 qty=100；BUY2: 1月10日 price=12 qty=100；SELL: 1月15日 price=14 qty=150 |
| 验证 | 匹配 BUY1 全部 100 股 + BUY2 的 50 股；avgCost=(10*100+12*50)/150=10.67；profitLoss=(14-10.67)*150≈500.00 |

| ID | TC-4.2.3 |
|---|---|
| 描述 | 卖单数量 > 所有未匹配买入数量，部分匹配 |
| 构造 | BUY1: price=10 qty=100；BUY2: price=12 qty=100；SELL: price=15 qty=250 |
| 验证 | 匹配全部 200 股买入；remainingQty=50 未匹配；sell.isMatched=true（部分成交也标记为已匹配）|

| ID | TC-4.2.4 |
|---|---|
| 描述 | 卖单数量 < 匹配买入数量，部分卖出 |
| 构造 | BUY1: price=10 qty=100；BUY2: price=12 qty=100；SELL: price=13 qty=50 |
| 验证 | 仅匹配 BUY1 的 50 股；avgCost=10；profitLoss=(13-10)*50=150.00；BUY1 剩余 50 股未匹配 |

| ID | TC-4.2.5 |
|---|---|
| 描述 | 卖出亏损场景 |
| 构造 | BUY: price=20 qty=100；SELL: price=15 qty=100 |
| 验证 | profitLoss=(15-20)*100=-500.00；profitLossPercent=-25% |

| ID | TC-4.2.6 |
|---|---|
| 描述 | 多笔卖单按时间顺序处理，后卖单使用剩余未匹配买入 |
| 构造 | BUY1: price=10 qty=100；SELL1: price=12 qty=50（匹配 BUY1 的 50 股，BUY1 剩余 50 股）；SELL2: price=11 qty=30（匹配 BUY1 剩余 30 股）|
| 验证 | SELL1 profitLoss=(12-10)*50=100；SELL2 profitLoss=(11-10)*30=30；BUY1 剩余 20 股未匹配 |

| ID | TC-4.2.7 |
|---|---|
| 描述 | 买入按 tradeDate 升序匹配（先入先出） |
| 构造 | BUY1: 1月1日 price=15 qty=100；BUY2: 1月10日 price=10 qty=100；SELL: price=12 qty=100 |
| 验证 | 先匹配 BUY1（更早），profitLoss=(12-15)*100=-300；BUY2 保持未匹配 |

### 4.3 `update` — 修改记录

| ID | TC-4.3.1 |
|---|---|
| 描述 | 更新 SELL 记录后重新计算收益 |
| 构造 | 已有 SELL trade，修改 price；recalculateSellProfit 被调用 |
| 验证 | `recalculateSellProfit()` 被调用 1 次 |

| ID | TC-4.3.2 |
|---|---|
| 描述 | 更新 BUY 记录，不触发匹配重算 |
| 验证 | `matchAndCalculateProfitLoss()` 不被调用 |

| ID | TC-4.3.3 |
|---|---|
| 描述 | 更新不存在的记录，抛 404 |
| Mock | `tradeRepository.findById` → empty |
| 验证 | 抛 `BusinessException`，statusCode=404 |

| ID | TC-4.3.4 |
|---|---|
| 描述 | 只传部分字段，只更新非 null 字段 |
| 验证 | 未传字段保持原值 |

### 4.4 `list` — 查询过滤

| ID | TC-4.4.1 |
|---|---|
| 描述 | 只传 stockCode 过滤 |
| 验证 | `tradeRepository.findByStockCode()` 被调用 |

| ID | TC-4.4.2 |
|---|---|
| 描述 | 只传日期范围过滤 |
| 验证 | `tradeRepository.findByTradeDateBetween()` 被调用 |

| ID | TC-4.4.3 |
|---|---|
| 描述 | stockCode + 日期范围同时过滤 |
| 验证 | `tradeRepository.findByStockCodeAndTradeDateBetween()` 被调用 |

| ID | TC-4.4.4 |
|---|---|
| 描述 | 在过滤结果上追加 direction 过滤 |
| 构造 | 有 5 条 trades，其中 2 条为 SELL |
| 验证 | 返回结果只含 SELL 方向的记录 |

### 4.5 `delete`

| ID | TC-4.5.1 |
|---|---|
| 描述 | 删除存在的记录 |
| 验证 | `tradeRepository.delete()` 被调用 |

| ID | TC-4.5.2 |
|---|---|
| 描述 | 删除不存在的记录，抛 404 |
| 验证 | 抛 `BusinessException`，statusCode=404 |

---

## 五、PeriodSummaryServiceTest

### 依赖 mock
- `PlanRepository`
- `PlanExecutionRepository`
- `ActualTradeRepository`

### 5.1 `getSummary` — 周期汇总

| ID | TC-5.1.1 |
|---|---|
| 描述 | WEEK：返回当前自然周的起始日期 |
| 构造 | today=2026-05-21（周四） |
| 验证 | period="WEEK"，start=2026-05-18（周一），end=2026-05-21 |

| ID | TC-5.1.2 |
|---|---|
| 描述 | MONTH：返回当月首日 |
| 构造 | today=2026-05-21 |
| 验证 | period="MONTH"，start=2026-05-01，end=2026-05-21 |

| ID | TC-5.1.3 |
|---|---|
| 描述 | YEAR：返回当年首日 |
| 验证 | period="YEAR"，start=2026-01-01，end=2026-05-21 |

| ID | TC-5.1.4 |
|---|---|
| 描述 | 无效 period 值，抛 IllegalArgumentException |
| 构造 | period="DECADE" |
| 验证 | 抛 `IllegalArgumentException` |

| ID | TC-5.1.5 |
|---|---|
| 描述 | 按创建时间过滤 plan，只纳入区间内 plan |
| 构造 | plan1: createdAt 在区间内；plan2: createdAt 在区间外 |
| 验证 | planList 只含 plan1 |

| ID | TC-5.1.6 |
|---|---|
| 描述 | PENDING plan 的 returnPercent 为 0（不参与收益计算）|
| 构造 | plan.status=PENDING |
| 验证 | plan.returnPercent=0 |

| ID | TC-5.1.7 |
|---|---|
| 描述 | HOLDING plan（有买无卖），returnPercent 为 0 |
| 构造 | plan.status=HOLDING，有 BUY 无 SELL |
| 验证 | plan.returnPercent=0 |

| ID | TC-5.1.8 |
|---|---|
| 描述 | CLOSED plan（已触发卖出），正确计算收益率 |
| 构造 | BUY triggerPrice=10.00；SELL triggerPrice=12.00 |
| 验证 | returnPercent=20.00 |

| ID | TC-5.1.9 |
|---|---|
| 描述 | gap = planAvgReturn - actualAvgReturn |
| 构造 | planAvgReturn=15%，actualAvgReturn=10% |
| 验证 | gapPercent=5.00 |

| ID | TC-5.1.10 |
|---|---|
| 描述 | 实盘汇总只计入 SELL 且 profitLoss 非 null 的记录 |
| 构造 | 含 BUY 记录和 profitLoss=null 的 SELL |
| 验证 | actualList 不含 BUY 和 profitLoss=null 的记录 |

| ID | TC-5.1.11 |
|---|---|
| 描述 | 无数据时，返回零值而非异常 |
| 构造 | planRepository 和 tradeRepository 均返回空 |
| 验证 | pendingCount=0, totalReturn=0, avgReturn=0, gap=0 |

| ID | TC-5.1.12 |
|---|---|
| 描述 | 多个 plan 时，avgReturn = totalReturn / plan 总数 |
| 构造 | plan1: return=10%，plan2: return=20% |
| 验证 | planAvgReturn=15% |

---

## 六、HoldingsServiceTest

### 依赖 mock
- `PlanRepository`
- `PlanExecutionRepository`
- `ActualTradeRepository`
- `TushareService`

### 6.1 `getHoldings` — 持仓概览

| ID | TC-6.1.1 |
|---|---|
| 描述 | 无 HOLDING plan 时，planHoldings 为空 |
| Mock | `planRepository.findByStatus(HOLDING)` → [] |
| 验证 | `response.planHoldings` 为空；summary.totalPlanUnrealizedPL=0 |

| ID | TC-6.1.2 |
|---|---|
| 描述 | 有 HOLDING plan，返回含浮盈数据 |
| 构造 | plan HOLDING；1 笔 BUY triggerPrice=10.00；Tushare 返回 currentPrice=11.00 |
| 验证 | unrealizedPL=1.00；unrealizedPLPercent≈10% |

| ID | TC-6.1.3 |
|---|---|
| 描述 | 浮亏场景 |
| 构造 | BUY triggerPrice=10.00；currentPrice=9.00 |
| 验证 | unrealizedPL=-1.00；unrealizedPLPercent≈-10% |

| ID | TC-6.1.4 |
|---|---|
| 描述 | holdDays = 当前日期 - 首次买入日期 |
| 构造 | 首次 BUY tradeDate=2026-05-01；today=2026-05-21 |
| 验证 | holdDays=20 |

| ID | TC-6.1.5 |
|---|---|
| 描述 | planHoldings 包含日内波动数据（high/low/close）|
| Mock | Tushare 返回 KLineData含 high=11.50, low=10.50, close=11.00 |
| 验证 | planHoldings[0].highPrice=11.50；lowPrice=10.50；closePrice=11.00 |

| ID | TC-6.1.6 |
|---|---|
| 描述 | 无实盘持仓时，actualHoldings 为空 |
| Mock | `tradeRepository.findAll()` → [] |
| 验证 | `response.actualHoldings` 为空；summary.totalActualUnrealizedPL=0 |

| ID | TC-6.1.7 |
|---|---|
| 描述 | 实盘持仓按 stockCode 聚合，多只股票分别展示 |
| 构造 | 3 笔未匹配 BUY: 2 笔 000001（各 qty=100），1 笔 600000（qty=200）|
| 验证 | actualHoldings.size()=2；000001 的 avgCostBasis = (10*100+12*100)/200=11.00 |

| ID | TC-6.1.8 |
|---|---|
| 描述 | 实盘浮盈以总持仓成本为基准计算 |
| 构造 | 总成本=(10*100+12*100)=2200；总数量=200；avgCostBasis=11.00；currentPrice=12.00 |
| 验证 | unrealizedPL=(12-11)*200=200；unrealizedPLPercent≈9.09% |

| ID | TC-6.1.9 |
|---|---|
| 描述 | holdingGap = 预案总浮盈% - 实盘总浮盈% |
| 构造 | plan unrealizedPLPercent=15%；actual unrealizedPLPercent=10% |
| 验证 | holdingGap=5.00 |

| ID | TC-6.1.10 |
|---|---|
| 描述 | 只有预案无实盘时，holdingGap=0 |
| 构造 | planHoldings 非空，actualHoldings 为空 |
| 验证 | holdingGap=0 |

| ID | TC-6.1.11 |
|---|---|
| 描述 | 只有实盘无预案时，holdingGap=0 |
| 构造 | actualHoldings 非空，planHoldings 为空 |
| 验证 | holdingGap=0 |

| ID | TC-6.1.12 |
|---|---|
| 描述 | Tushare 返回空时，跳过该 plan，不抛异常 |
| Mock | `tushareService.getDailyKLine()` → Optional.empty() |
| 验证 | 该 plan 不出现在 planHoldings 中；其他 plan 正常处理 |

| ID | TC-6.1.13 |
|---|---|
| 描述 | plan 有 HOLDING 状态但无买入记录，跳过 |
| 构造 | plan HOLDING，executionRepository 返回空列表 |
| 验证 | 该 plan 不出现在 planHoldings 中 |

---

## 七、MarketCloseTaskTest

### 依赖 mock
- `PlanRepository`
- `PlanExecutionRepository`
- `DailySnapshotRepository`
- `ActualTradeRepository`
- `TushareService`
- `PlanExecutionService`

### 7.1 `onMarketClose` — 入口编排

| ID | TC-7.1.1 |
|---|---|
| 描述 | 非交易日，跳过全部逻辑 |
| Mock | `tushareService.isTradingDay()` → false |
| 验证 | `checkAndExpirePlans`/`evaluateTriggerConditions`/`generatePlanSnapshots`/`generateActualTradeSnapshots` 均不被调用 |

| ID | TC-7.1.2 |
|---|---|
| 描述 | 交易日，按顺序执行全部 4 个步骤 |
| Mock | `isTradingDay()` → true |
| 验证 | 4 个子方法依次被调用 |

### 7.2 `checkAndExpirePlans` — 过期检查

| ID | TC-7.2.1 |
|---|---|
| 描述 | DAILY plan，创建日不等于今天，标记为 EXPIRED |
| 构造 | plan.cycle=DAILY，plan.createdAt=2026-05-20 10:00，今天=2026-05-21 |
| 验证 | `plan.setStatus(EXPIRED)` + `planRepository.save()` |

| ID | TC-7.2.2 |
|---|---|
| 描述 | DAILY plan，创建日等于今天，不过期 |
| 构造 | plan.createdAt=2026-05-21 10:00，今天=2026-05-21 |
| 验证 | 状态保持 PENDING，save 不被调用 |

| ID | TC-7.2.3 |
|---|---|
| 描述 | WEEKLY/MONTHLY plan，validUntil 在今天之后，不过期 |
| 构造 | plan.cycle=MONTHLY，validUntil=2026-05-31，今天=2026-05-21 |
| 验证 | 状态保持 PENDING |

| ID | TC-7.2.4 |
|---|---|
| 描述 | WEEKLY/MONTHLY plan，validUntil 在今天之前，标记为 EXPIRED |
| 构造 | plan.validUntil=2026-05-20，今天=2026-05-21 |
| 验证 | `plan.setStatus(EXPIRED)` + `planRepository.save()` |

| ID | TC-7.2.5 |
|---|---|
| 描述 | validUntil 为 null，WEEKLY/MONTHLY 不自动过期 |
| 构造 | plan.validUntil=null，cycle=MONTHLY |
| 验证 | 状态保持 PENDING |

| ID | TC-7.2.6 |
|---|---|
| 描述 | 非 PENDING plan（HOLDING/CLOSED）不参与过期检查 |
| 构造 | plan.status=HOLDING |
| 验证 | `isOutsideValidityWindow()` 不被调用；状态不变 |

### 7.3 `evaluateTriggerConditions` — 触发判断

| ID | TC-7.3.1 |
|---|---|
| 描述 | PENDING plan + BUY 条件触发：记录执行 + 转为 HOLDING |
| 构造 | plan PENDING，condition BUY active，KLineData 满足条件 |
| Mock | `evaluateCondition` → true |
| 验证 | `executionService.recordExecution(plan, BUY, ...)` + `transitionState(plan, HOLDING)` |

| ID | TC-7.3.2 |
|---|---|
| 描述 | PENDING plan，条件不满足，不触发 |
| Mock | `evaluateCondition` → false |
| 验证 | `recordExecution` 不被调用；状态保持 PENDING |

| ID | TC-7.3.3 |
|---|---|
| 描述 | PENDING plan 触发买入后，不再检查其他买入条件（自然去重）|
| 构造 | plan 有 2 个 BUY 条件，第一个触发 |
| 验证 | `recordExecution` 只被调用 1 次；plan 转为 HOLDING 后循环 break |

| ID | TC-7.3.4 |
|---|---|
| 描述 | HOLDING plan + SELL 条件触发：记录执行 + 转为 CLOSED |
| 构造 | plan HOLDING，condition SELL active，KLineData 满足条件 |
| Mock | `evaluateCondition` → true |
| 验证 | `executionService.recordExecution(plan, SELL, ...)` + `transitionState(plan, CLOSED)` |

| ID | TC-7.3.5 |
|---|---|
| 描述 | HOLDING plan，条件不满足，不触发 |
| 验证 | `recordExecution` 不被调用 |

| ID | TC-7.3.6 |
|---|---|
| 描述 | HOLDING plan 不再检查买入条件（状态守卫）|
| 构造 | plan HOLDING，condition BUY active |
| 验证 | MA 计算不被调用，`evaluateCondition` 不被调用 |

| ID | TC-7.3.7 |
|---|---|
| 描述 | isActive=false 的条件不参与判断 |
| 构造 | condition.isActive=false |
| 验证 | `evaluateCondition` 不被调用 |

| ID | TC-7.3.8 |
|---|---|
| 描述 | Tushare 无 K 线数据，跳过该 plan |
| Mock | `getDailyKLine` → Optional.empty() |
| 验证 | 跳过该 plan，不抛异常，继续处理下一 plan |

| ID | TC-7.3.9 |
|---|---|
| 描述 | MA 类型条件，calculateMA 被调用 |
| 构造 | condition.type=MA，maPeriod=5 |
| Mock | `calculateMA` → BigDecimal.valueOf(10.0) |
| 验证 | `tushareService.calculateMA(stockCode, 5, today)` 被调用 |

| ID | TC-7.3.10 |
|---|---|
| 描述 | PRICE 类型条件，不调用 calculateMA |
| 构造 | condition.type=PRICE |
| 验证 | `calculateMA` 不被调用 |

### 7.4 `generatePlanSnapshots` — 预案快照

| ID | TC-7.4.1 |
|---|---|
| 描述 | PENDING plan 生成快照，planReturn=0 |
| 构造 | plan PENDING |
| 验证 | `snapshotRepository.save()` 调用；snapshot.planReturn=0 |

| ID | TC-7.4.2 |
|---|---|
| 描述 | HOLDING plan 生成快照，planReturn 基于当前价计算 |
| 构造 | plan HOLDING，BUY triggerPrice=10.00，currentPrice=11.00 |
| Mock | Tushare 返回 close=11.00 |
| 验证 | planReturnPercent≈10% |

| ID | TC-7.4.3 |
|---|---|
| 描述 | 快照含日内波动数据（high/low/close）|
| 验证 | snapshot.highPrice、lowPrice、closePrice 均来自 KLineData |

| ID | TC-7.4.4 |
|---|---|
| 描述 | hasActualTrade=true 当且仅当存在同 stockCode 的实盘记录 |
| 构造 | plan.stockCode="000001"，有 1 笔 actualTrade |
| 验证 | snapshot.hasActualTrade=true |

| ID | TC-7.4.5 |
|---|---|
| 描述 | Tushare 无数据时跳过该 plan |
| Mock | `getDailyKLine` → Optional.empty() |
| 验证 | 该 plan 的 snapshot 不生成 |

### 7.5 `generateActualTradeSnapshots` — 实盘快照

| ID | TC-7.5.1 |
|---|---|
| 描述 | 每条 trade 生成一条 snapshot |
| 构造 | 3 笔 trade |
| 验证 | `snapshotRepository.save()` 被调用 3 次 |

| ID | TC-7.5.2 |
|---|---|
| 描述 | snapshot 含 stockCode/stockName 和 K 线数据 |
| 验证 | snapshot.stockCode、stockName、closePrice 等字段正确 |

| ID | TC-7.5.3 |
|---|---|
| 描述 | Tushare 无数据时跳过该 trade |
| Mock | `getDailyKLine` → Optional.empty() |
| 验证 | 该 trade 的 snapshot 不生成 |

---

## 九、SystemConfigServiceTest

### 依赖 mock
- `SystemConfigRepository`

### 9.1 `getBaselineCapital` — 读取基准资金

|| ID | TC-9.1.1 |
|---|---|
| 描述 | SystemConfig 已存在时，返回当前基准资金 |
| 构造 | SystemConfig.baselineCapital=800000 |
| 验证 | 返回 `BigDecimal("800000")` |

|| ID | TC-9.1.2 |
|---|---|
| 描述 | SystemConfig 不存在时，创建默认记录并返回 500000 |
| Mock | `systemConfigRepository.findAll()` → empty list |
| 验证 | `systemConfigRepository.save()` 被调用 1 次；创建记录的 baselineCapital=500000；返回 `BigDecimal("500000")` |

|| ID | TC-9.1.3 |
|---|---|
| 描述 | 默认值 500000 精确到 DECIMAL(18,2) |
| Mock | SystemConfig 不存在 |
| 验证 | 保存时 baselineCapital = `500000.00` |

### 9.2 `setBaselineCapital` — 更新基准资金

|| ID | TC-9.2.1 |
|---|---|
| 描述 | SystemConfig 已存在，正常更新 |
| 构造 | 当前值 500000 → 新值 800000 |
| 验证 | `save()` 被调用；返回记录的 baselineCapital=800000 |

|| ID | TC-9.2.2 |
|---|---|
| 描述 | SystemConfig 不存在时，创建新记录 |
| Mock | `findAll()` → empty |
| 验证 | `save()` 被调用；baselineCapital=新值 |

|| ID | TC-9.2.3 |
|---|---|
| 描述 | 更新后 updatedAt 被刷新 |
| 构造 | 旧 updatedAt=2026-01-01，新值=800000 |
| 验证 | 保存时 updatedAt > 2026-01-01 |

---

## 十、BaselineCapitalAdjustmentTest（SystemConfigService）

### 依赖 mock
- `SystemConfigRepository`
- `PlanAccountRepository`
- `ActualAccountRepository`

> 以下测试覆盖 design.md 决策 8 的联动规则。

### 10.1 `adjustBaselineCapital` — 提高基准

|| ID | TC-10.1.1 |
|---|---|
| 描述 | 提高基准，PlanAccount.cashBalance 和 ActualAccount.cashBalance 均同步充值至新基准 |
| 构造 | baselineCapital: 500000 → 800000；PlanAccount.cashBalance=400000；ActualAccount.cashBalance=400000 |
| Mock | `planAccountRepository.findAll()` → `[planAccount]`；`actualAccountRepository.findAll()` → `[actualAccount]` |
| 验证 | PlanAccount.cashBalance=800000；ActualAccount.cashBalance=800000；SystemConfig.baselineCapital=800000；三者均在同一事务内保存 |

|| ID | TC-10.1.2 |
|---|---|
| 描述 | 提高基准时有虚拟持仓，cashBalance 不受持仓影响 |
| 构造 | baselineCapital: 500000 → 800000；PlanAccount.cashBalance=200000（含持仓占用 300000）；ActualAccount 同 |
| 验证 | PlanAccount.cashBalance=800000（直接赋值，覆盖原值） |

### 10.2 `adjustBaselineCapital` — 降低基准通过检测

|| ID | TC-10.2.1 |
|---|---|
| 描述 | 降低基准通过检测（newBaseline <= cashBalance），cashBalance 回拨 |
| 构造 | baselineCapital: 800000 → 500000；PlanAccount.cashBalance=600000；ActualAccount.cashBalance=600000 |
| Mock | cashBalance 检测通过 |
| 验证 | PlanAccount.cashBalance=500000；ActualAccount.cashBalance=500000；SystemConfig.baselineCapital=500000 |

|| ID | TC-10.2.2 |
|---|---|
| 描述 | 降低基准刚好通过：newBaseline == cashBalance |
| 构造 | baselineCapital: 800000 → 600000；PlanAccount.cashBalance=600000 |
| 验证 | PlanAccount.cashBalance=600000；ActualAccount.cashBalance=600000；操作成功 |

### 10.3 `adjustBaselineCapital` — 降低基准不通过检测

|| ID | TC-10.3.1 |
|---|---|
| 描述 | newBaseline > PlanAccount.cashBalance，拒绝调整，返回 HTTP 400 |
| 构造 | baselineCapital: 800000 → 500000；PlanAccount.cashBalance=300000（持仓占用，无法回拨至此） |
| Mock | `planAccountRepository.findAll()` → `[planAccount]` |
| 验证 | 抛 `BusinessException`，statusCode=400；错误信息含 "500000" 和 "300000"；SystemConfig.baselineCapital 不变 |

|| ID | TC-10.3.2 |
|---|---|
| 描述 | PlanAccount.cashBalance 不足，但 ActualAccount.cashBalance 充足，以 PlanAccount 为准 |
| 构造 | PlanAccount.cashBalance=300000；ActualAccount.cashBalance=800000；newBaseline=500000 |
| Mock | 检测到 300000 < 500000 |
| 验证 | 拒绝调整；错误信息以 PlanAccount.cashBalance 为准 |

|| ID | TC-10.3.3 |
|---|---|
| 描述 | 降低基准不通过时，ActualAccount.cashBalance 不被修改 |
| 构造 | 同 TC-10.3.1 |
| 验证 | ActualAccount.cashBalance 保持原值；`actualAccountRepository.save()` 不被调用 |

### 10.4 `adjustBaselineCapital` — 边界条件

|| ID | TC-10.4.1 |
|---|---|
| 描述 | 提高基准值与当前基准相同（newBaseline == currentBaseline） |
| 构造 | baselineCapital: 500000 → 500000 |
| 验证 | `planAccountRepository.save()` 和 `actualAccountRepository.save()` 仍被调用（幂等）；无异常 |

|| ID | TC-10.4.2 |
|---|---|
| 描述 | 降低基准值与当前基准相同（newBaseline == currentBaseline） |
| 验证 | 操作成功；cashBalance 保持不变 |

|| ID | TC-10.4.3 |
|---|---|
| 描述 | newBaseline = 0（降至零），PlanAccount.cashBalance=0 时可通过 |
| 构造 | baselineCapital: 500000 → 0；PlanAccount.cashBalance=0 |
| 验证 | PlanAccount.cashBalance=0；ActualAccount.cashBalance=0 |

|| ID | TC-10.4.4 |
|---|---|
| 描述 | 降低基准至负数，拒绝 |
| 构造 | newBaseline=-100000 |
| 验证 | 抛 `BusinessException`，statusCode=400 |

---

## 十一、PlanAccountCashSyncTest（PlanExecutionService）

### 依赖 mock
- `PlanAccountRepository`
- `PlanExecutionRepository`
- `PlanRepository`

> 以下测试覆盖 design.md 决策 8 的 PlanAccount.cashBalance 实时同步维护规则。

### 11.1 `recordExecution` — 买入时现金扣减

|| ID | TC-11.1.1 |
|---|---|
| 描述 | PlanExecution 买入触发，PlanAccount.cashBalance 自动扣减交易金额 |
| 构造 | PlanAccount.cashBalance=500000；PlanExecution BUY: triggerPrice=10.00, quantity=10000（交易金额=100000） |
| Mock | `planAccountRepository.findAll()` → `[planAccount]` |
| 验证 | `planAccountRepository.save()` 被调用；planAccount.cashBalance=400000 |

|| ID | TC-11.1.2 |
|---|---|
| 描述 | 买入触发后 plan 状态转为 HOLDING |
| 构造 | 同 TC-11.1.1 |
| 验证 | `plan.setStatus(HOLDING)`；`planRepository.save()` 被调用 |

|| ID | TC-11.1.3 |
|---|---|
| 描述 | 多笔买入，逐笔累扣 |
| 构造 | 第一笔：cashBalance=500000，扣 100000 → 400000；第二笔：cashBalance=400000，扣 80000 → 320000 |
| 验证 | 每笔买入后 cashBalance 正确递减 |

### 11.2 `recordExecution` — 买入资金不足

|| ID | TC-11.2.1 |
|---|---|
| 描述 | 买入交易金额 > PlanAccount.cashBalance，拒绝触发 |
| 构造 | PlanAccount.cashBalance=50000；PlanExecution BUY: triggerPrice=10.00, quantity=10000（交易金额=100000） |
| Mock | `planAccountRepository.findAll()` → `[planAccount]` |
| 验证 | 抛 `BusinessException`，statusCode=400；错误信息含 "资金不足" 和相关金额；`planRepository.save()` 不被调用（状态不变） |

|| ID | TC-11.2.2 |
|---|---|
| 描述 | 买入金额刚好等于 cashBalance（边界） |
| 构造 | PlanAccount.cashBalance=100000；交易金额=100000 |
| 验证 | 扣减后 cashBalance=0；操作成功 |

|| ID | TC-11.2.3 |
|---|---|
| 描述 | PlanAccount.cashBalance = 0，无法买入 |
| 构造 | PlanAccount.cashBalance=0；交易金额=100000 |
| 验证 | 抛 `BusinessException`，statusCode=400 |

### 11.3 `recordExecution` — 卖出时现金回补

|| ID | TC-11.3.1 |
|---|---|
| 描述 | PlanExecution 卖出触发，PlanAccount.cashBalance 自动回补卖出金额 |
| 构造 | PlanAccount.cashBalance=400000（已扣减买入）；PlanExecution SELL: triggerPrice=12.00, quantity=10000（卖出金额=120000） |
| Mock | `planAccountRepository.findAll()` → `[planAccount]` |
| 验证 | `planAccountRepository.save()` 被调用；planAccount.cashBalance=520000 |

|| ID | TC-11.3.2 |
|---|---|
| 描述 | 卖出触发后 plan 状态转为 CLOSED |
| 验证 | `plan.setStatus(CLOSED)`；`planRepository.save()` 被调用 |

|| ID | TC-11.3.3 |
|---|---|
| 描述 | 卖出后 cashBalance 可以超过原始基准（盈利） |
| 构造 | 原始基准=500000；买入扣减 100000（余额 400000）；卖出回补 120000（余额 520000） |
| 验证 | cashBalance=520000 > 500000 |

---

## 十二、ActualAccountCashSyncTest（ActualTradeService）

### 依赖 mock
- `ActualAccountRepository`
- `ActualTradeRepository`

> 以下测试覆盖 design.md 决策 8 的 ActualAccount.cashBalance 实时同步维护规则，以及初始值手动录入。

### 12.1 `create` — 买入录入时现金扣减

|| ID | TC-12.1.1 |
|---|---|
| 描述 | ActualTrade 买入录入，ActualAccount.cashBalance 自动扣减交易金额 |
| 构造 | ActualAccount.cashBalance=600000；ActualTrade BUY: price=10.00, quantity=10000（交易金额=100000） |
| Mock | `actualAccountRepository.findAll()` → `[actualAccount]` |
| 验证 | `actualAccountRepository.save()` 被调用；actualAccount.cashBalance=500000 |

|| ID | TC-12.1.2 |
|---|---|
| 描述 | 多笔买入录入，逐笔累扣 |
| 验证 | 每笔买入后 cashBalance 正确递减 |

### 12.2 `create` — 买入录入资金不足

|| ID | TC-12.2.1 |
|---|---|
| 描述 | 买入交易金额 > ActualAccount.cashBalance，拒绝录入 |
| 构造 | ActualAccount.cashBalance=50000；交易金额=100000 |
| 验证 | 抛 `BusinessException`，statusCode=400；错误信息含 "资金不足"；`actualTradeRepository.save()` 不被调用 |

|| ID | TC-12.2.2 |
|---|---|
| 描述 | 买入金额刚好等于 cashBalance（边界） |
| 构造 | ActualAccount.cashBalance=100000；交易金额=100000 |
| 验证 | 扣减后 cashBalance=0；操作成功 |

### 12.3 `create` — 卖出录入时现金回补

|| ID | TC-12.3.1 |
|---|---|
| 描述 | ActualTrade 卖出录入，ActualAccount.cashBalance 自动回补卖出金额 |
| 构造 | ActualAccount.cashBalance=500000（已扣减买入）；ActualTrade SELL: price=12.00, quantity=10000（卖出金额=120000） |
| Mock | `actualAccountRepository.findAll()` → `[actualAccount]` |
| 验证 | `actualAccountRepository.save()` 被调用；actualAccount.cashBalance=620000 |

|| ID | TC-12.3.2 |
|---|---|
| 描述 | 卖出后 cashBalance 可以超过原始基准（盈利） |
| 验证 | 同 TC-11.3.3 |

### 12.4 `updateCashBalance` — 初始现金手动录入

|| ID | TC-12.4.1 |
|---|---|
| 描述 | 用户手动修改实盘初始现金 |
| 构造 | 当前 cashBalance=500000 → 新值=600000 |
| Mock | `actualAccountRepository.findAll()` → `[actualAccount]` |
| 验证 | `actualAccountRepository.save()` 被调用；cashBalance=600000 |

|| ID | TC-12.4.2 |
|---|---|
| 描述 | ActualAccount 不存在时，自动创建并设置 cashBalance |
| Mock | `actualAccountRepository.findAll()` → empty |
| 验证 | `actualAccountRepository.save()` 被调用 1 次；cashBalance=新值 |

|| ID | TC-12.4.3 |
|---|---|
| 描述 | 手动修改后影响后续收益计算 |
| 构造 | 用户将 cashBalance 从 500000 改为 600000（实盘实际初始资金为 60 万） |
| 验证 | 后续买入扣减基于新的 cashBalance=600000 |

---

## 十三、测试用例汇总

| 测试类 | 用例数 | 核心覆盖 |
|---|---|---|
| TushareServiceTest | 16 | K线获取、MA计算、触发语义B、交易日判断 |
| PlanServiceTest | 15 | CRUD、状态守卫、验证规则、过滤查询 |
| PlanExecutionServiceTest | 8 | 记录创建、状态机转换、收益计算 |
| ActualTradeServiceTest | 19 | FIFO 匹配全场景（6 场景）、CRUD、查询过滤 |
| PeriodSummaryServiceTest | 12 | 周期边界、空数据、聚合计算、gap 计算 |
| HoldingsServiceTest | 13 | 浮盈计算、聚合、gap、边界处理 |
| MarketCloseTaskTest | 18 | 交易日守卫、过期检查、触发编排、快照生成 |
| SystemConfigServiceTest | 5 | 基准资金读写、默认值创建（14.x 新增） |
| BaselineCapitalAdjustmentTest | 8 | 提高/降低基准、可行性检测、边界条件（14.x 新增） |
| PlanAccountCashSyncTest | 6 | 买入扣减、资金不足拒绝、卖出回补（14.x 新增） |
| ActualAccountCashSyncTest | 6 | 买入扣减、资金不足拒绝、卖出回补、初始现金录入（14.x 新增） |
| **合计** | **132** | |
