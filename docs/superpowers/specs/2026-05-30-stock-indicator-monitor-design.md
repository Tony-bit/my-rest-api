# 股票技术指标监控设计

## 目标

为 `my-rest-api` 添加一个股票技术指标每日监控功能。该功能允许用户维护一个数据库存储的 A 股自选股列表，在收盘后从雪球获取每日的 KDJ 和 MACD 指标值，评估状态化的买卖信号，记录可审计的触发历史，并通过微信模板消息通知推送到用户个人微信。

该监控模块独立于现有的交易计划和实际交易模块，不创建计划、不关联交易、不更新持仓、不改变账户余额。

## 范围

### 功能范围

- 从前端配置并存储一个全局的雪球 Cookie
- 从前端配置并存储一个微信测试号配置（AppID、AppSecret、模板ID、OpenID）
- 在数据库中存储和管理监控股票列表
- 第一版仅支持沪、深 A 股 6 位股票代码
- 从雪球获取已启用股票的每日 K 线指标数据
- 每个股票、每个交易日仅存储一条指标快照
- 为监控检查和触发的信号记录可重复的审计日志
- 每个股票、每个信号类型、每个交易日至多发送一条微信模板消息通知
- 提供前端"立即检查"功能，评估并展示结果，但不发通知
- 提供股票监控详情页面，展示折叠的信号历史和审计详情
- 提供手动通知重试功能

### 不在范围内

- 交易下单
- 盘中监控和盘中信号状态变化
- 指标值的 Tushare 回退方案
- 复杂回测或图表
- 用户认证和用户级配置
- 与交易计划、实际交易、持仓、账户或每日组合快照的自动集成
- 第一版不自动清理旧的监控记录
- 北交所、港股、美股、指数和 ETF

## 支持的股票代码

第一版仅接受 6 位沪、深 A 股股票代码：

- `6xxxxx` 映射为雪球代码 `SH6xxxxx`
- `0xxxxx` 映射为雪球代码 `SZ0xxxxx`
- `3xxxxx` 映射为雪球代码 `SZ3xxxxx`

前端和后端都会验证代码格式，无效代码在加入自选股前就会被拒绝。股票名称由用户输入，可选；如果为空，前端显示股票代码。

## 数据来源

监控模块使用雪球的 K 线 JSON 接口获取指标数据，而非本地计算。

请求参数：

- `symbol`：雪球代码，如 `SZ301599` 或 `SH600000`
- `period=day`
- `type=before`
- `count`：请求足够多的近期日线数据用于信号评估和展示，第一版请求至少 60 条
- `indicator=kline,ma,macd,kdj`

使用的响应字段：

- `timestamp`
- `open`
- `high`
- `low`
- `close`
- `dea`
- `dif`
- `macd`
- `kdjk`
- `kdjd`
- `kdjj`

监控模块直接使用雪球返回的原始 `macd` 值，不做乘法、除法、四舍五入或其他任何变换。

雪球 Cookie 由用户从前端提供并存储在后端数据库。第一版接受明文存储，因为当前应用没有认证或密钥管理层。后端响应不能返回完整的 Cookie；只能返回配置状态和掩码预览。日志不能打印完整的 Cookie 值。设置页面必须支持更新和清除 Cookie。

微信测试号配置（AppID、AppSecret、模板ID、OpenID）同样由用户从前端提供并存储在后端数据库。AppSecret 必须安全存储，不能在任何 API 响应中完整返回。日志不能打印完整的 AppSecret。设置页面必须支持更新和清除这些值。

**当前配置的微信测试号参数：**

| 参数 | 值 |
|------|-----|
| AppID | `wx06680d7f6fdcd0da` |
| AppSecret | `0fa34fb71e4d1c198b784f230583b468` |
| 模板ID | `239zHlPaAORIosFh8sOOqVsaRGF3JPQgvgW5WEjSQpI` |
| OpenID | `oIvsX3XhNf21wWPWtF2SyOxmydek` |

## 指标快照

`StockIndicatorSnapshot` 记录监控使用的每日指标值：

- 股票代码
- 交易日期
- 开盘价、最高价、最低价、收盘价
- DIF、DEA、原始 MACD
- K、D、J
- 原始数据时间戳
- 最后检查时间
- 创建时间和更新时间

每个股票、每个交易日只有一条快照。同一天重复检查会更新同一行。最终值应为该交易日的最新收盘后数据。

如果当前日期是交易日，但雪球返回的股票最新 K 线日期早于今天，则该股票视为停牌或当日无交易。跳过该股票，不更新指标快照、不更新信号状态、不写信号日志、不发送通知。保留股票之前的观察状态。

## 信号模型

信号是有状态的。股票先在连续出现 KDJ 极值后进入观察状态，然后在 KDJ 和 MACD 共同确认后发出信号。

状态：

- `NONE`（无）
- `WATCH_SELL`（观察卖出）
- `WATCH_BUY`（观察买入）

### 卖出观察进入条件

- 任意两个连续的有效的日线数据的 `J > 90`
- 一旦出现，股票进入 `WATCH_SELL`

### 卖出信号确认条件

- 股票处于 `WATCH_SELL` 状态
- 今日的 `J` 低于昨日的 `J`
- 今日的原始 `MACD` 值低于昨日的原始 `MACD` 值

这涵盖了红柱收缩、红转绿反转和绿柱扩张，因为这些都表现为今日原始 MACD 值低于昨日。

### 买入观察进入条件

- 任意两个连续的有效的日线数据的 `J < 10`
- 一旦出现，股票进入 `WATCH_BUY`

### 买入信号确认条件

- 股票处于 `WATCH_BUY` 状态
- 今日的 `J` 高于昨日的 `J`
- 今日的原始 `MACD` 值高于昨日的原始 `MACD` 值

这涵盖了绿柱收缩、绿转红反转和红柱扩张，因为这些都表现为今日原始 MACD 值高于昨日。

信号确认后，将股票状态重置为 `NONE`。观察状态意味着"正在等待信号"；一旦信号存在并已被审计，观察轮次就完成了。微信通知状态通过信号日志的 notificationStatus 单独跟踪。

如果在对立观察进入条件出现之前已有当前状态，较新的对立状态会替代现有状态。

边界行为：

- `J == 90` 不会进入卖出观察
- `J == 10` 不会进入买入观察
- 比较使用精确的 `BigDecimal` 比较，无容差
- 缺少或不充分的指标行不产生信号
- 除非 KDJ 方向和 MACD 方向在同一天同时确认，否则不发出信号
- 确认可以在观察进入后的多天后发生，不限于两天 KDJ 极值后的第三天
- 业务模型假设一个股票在一天内最多只能有一个信号方向。如果异常数据同时满足买卖信号路径，将其视为数据异常：标记检查失败，不写信号日志，不通知

## 新股票初始化

当股票添加到自选股列表时，后端立即从最近 5 个已完成的交易日初始化其观察状态。

初始化行为：

- 如果当前时间在收盘后状态变更窗口之前，且雪球包含了今天的 K 线，则排除今天的 K 线
- 扫描最近 5 个已完成的交易日，寻找两个连续的 `J > 90` 或两个连续的 `J < 10`
- 如果只出现卖出观察进入，初始化 `signalState = WATCH_SELL`
- 如果只出现买入观察进入，初始化 `signalState = WATCH_BUY`
- 如果两者都出现，使用较晚进入的观察状态
- 如果两者都不出现，初始化 `signalState = NONE`
- 不写历史信号日志
- 不发送历史通知
- 即使 5 天历史已经满足确认条件，也不在创建时发送或审计历史信号。下一轮收盘后检查负责处理未来的确认

## 时间窗口

监控模块有两个收盘后概念：

- `15:30 Asia/Shanghai`：此时间之后允许进行状态变更检查
- `17:10 Asia/Shanghai`：正式的每日监控定时任务运行时间

在 `15:30` 之前，"立即检查"可以获取并展示临时数据，但必须不更新指标快照、不更新观察状态、不写信号日志、不发送通知。

从 `15:30` 开始，"立即检查"可以更新每日快照、更新观察状态、写入信号审计日志，但仍然不发送微信通知。

定时监控任务在工作日 `17:10 Asia/Shanghai` 运行，负责正式的每日监控和待处理通知的发送。

## 定时监控任务行为

`StockMonitorTask` 在工作日收盘后 `17:10 Asia/Shanghai` 运行。周六和周日不触发。

任务启动时：

1. 验证雪球 Cookie 已配置且可用。如果 Cookie 缺失或无效，任务立即失败
2. 判断今天是否是交易日
3. 如果今天不是交易日，结束任务，不处理股票
4. 如果任务级检查或任务级外部调用失败，整个任务重试最多 3 次
5. 3 次失败后，停止任务并记录失败

任务通过任务级检查并开始处理股票列表后，单个股票失败不会重启整个任务。失败的股票被跳过，其最后检查状态被更新，其他股票继续处理。

单个股票行为：

- 从雪球获取最新每日指标行
- 如果雪球最新交易日期早于今天，标记该股票当天停牌或无数据，跳过，不改变状态
- 如果必需的指标字段缺失或行数不足，跳过该股票，不改变状态
- 持久化或更新今天的指标快照
- 评估观察进入和信号确认
- 如果确认信号，写入信号审计日志并将股票状态重置为 `NONE`
- 如果没有相同股票、交易日、信号类型的成功通知记录，则发送微信通知
- 重试符合相同通知键规则的待处理手动检查和失败的通知

## 立即检查行为

前端在监控列表和详情页提供每个股票的"立即检查"操作。

### 15:30 之前

- 获取最新的可用指标行
- 评估展示结果
- 不更新指标快照
- 不更新观察状态
- 不写信号日志
- 不发送通知
- 返回消息如："收盘前窗口，监控状态未更新"

### 15:30 及之后

- 获取最新指标行
- 如果数据有效，更新今天的指标快照
- 评估观察状态和信号确认
- 更新观察状态
- 如果确认信号，写入信号审计日志并将观察状态重置为 `NONE`
- 不发送通知
- 返回结构化的检查结果

检查结果应包括：

- 最新交易日期
- 收盘价
- 今日和昨日的 J 值
- 今日和昨日的原始 MACD 值
- 检查后的当前信号状态
- 是否满足买卖信号条件
- 未触发信号的原因
- 最后检查状态和消息

如果"立即检查"确认了信号，生成的日志有 `notificationStatus = NOT_SENT_MANUAL_CHECK`。后续定时任务或手动重试可以为该信号发送微信通知。

## 通知行为

### 微信模板消息 API 集成

系统使用微信公众平台测试号模板消息 API 发送通知到用户个人微信。

**API 端点：**

```
POST https://api.weixin.qq.com/cgi-bin/message/template/send?access_token={access_token}
```

**两步认证流程：**

1. **获取 Access Token：**
   - 端点：`GET https://api.weixin.qq.com/cgi-bin/token`
   - 参数：`grant_type=client_credential&appid={AppID}&secret={AppSecret}`
   - 响应：`{"access_token": "...", "expires_in": 7200}`
   - 注意：Access Token 有效期 2 小时，应缓存并重复使用直到过期

2. **发送模板消息：**
   - 端点：`POST https://api.weixin.qq.com/cgi-bin/message/template/send`
   - 必需的 JSON 请求体：
     ```json
     {
       "touser": "{OpenID}",
       "template_id": "{模板ID}",
       "data": {
         "first": { "value": "标题", "color": "#173177" },
         "keyword1": { "value": "值1" },
         "keyword2": { "value": "值2" },
         "keyword3": { "value": "值3" },
         "remark": { "value": "备注" }
       }
     }
     ```
   - 成功响应：`{"errcode": 0, "errmsg": "ok", "msgid": 123456}`

**安全要求：**

- AppSecret 不得记录日志或在任何 API 响应中返回
- 服务应在任何错误消息中对 AppSecret 进行掩码处理
- 前端设置页面只显示"已配置"状态，不显示实际值
- HTTP 客户端应设置适当的超时（连接 5 秒，读取 10 秒）

**限流：**

- 微信限制每个账号每天的模板消息数量
- 对临时失败实现指数退避重试
- 记录所有通知尝试，使用掩码后的凭证

**通知触发条件：**

微信通知仅由以下方式触发：

- 定时监控任务，或
- 详情页的显式通知重试操作

"立即检查"永不发送通知。

系统对每个股票、每个交易日、每个信号类型至多发送一条成功通知。

由于 `StockSignalLog` 也用于审计历史，允许重复的手动检查，信号日志不按股票/日期/类型唯一。通知去重通过查询具有相同通知键的日志来处理：

- 如果任何匹配的日志已有 `notificationStatus = SENT`，不再发送
- 如果没有匹配的 `SENT` 日志，定时任务可为匹配的 `NOT_SENT_MANUAL_CHECK`、`FAILED` 或新信号日志发送
- 发送成功后，将所有匹配的非已发送日志更新为 `SENT`，并设置相同的 `notificationSentAt`
- 发送失败后，将涉及的日志标记为 `FAILED`，存储失败原因，并增加尝试次数

**消息内容：**

模板消息内容应包括：

- 信号类型（买入信号/卖出信号）
- 股票代码
- 股票名称（如有）
- 收盘价
- J 值
- MACD 值
- 信号原因/摘要
- 触发时间

示例模板数据：
```
first: "📈 买入信号触发"
keyword1: "600000"
keyword2: "买入"
keyword3: "2026-05-30 17:10"
remark: "J值: 8.5, MACD: 金叉"
```

## 信号审计与保留

`StockSignalLog` 记录每次状态变更信号检测。同一个股票、同一个交易日、同一个信号类型的重复检查是被允许的，应产生重复的审计记录。

字段包括：

- 股票代码
- 交易日期
- 信号类型
- 触发来源：`SCHEDULED` 或 `MANUAL_CHECK`
- 收盘价
- 今日和昨日的 J 值
- 今日和昨日的原始 MACD 值
- 信号原因或评估摘要
- 通知状态
- 通知尝试次数
- 通知发送时间
- 通知失败原因
- 创建时间戳

信号日志和指标快照第一版保留一年。第一版不实现自动老化和删除。在交易日期和创建时间上添加索引，以便未来添加清理任务时无需修改表结构。

## 后端设计

### 数据库实体

新增实体：

- `StockMonitorConfig`
  - 单例行，存储雪球 Cookie、微信 AppID、AppSecret、模板ID、OpenID 和更新时间戳
- `StockWatch`
  - 股票代码、股票名称、启用标志、信号状态、观察开始日期、最后信号类型/日期、最后检查时间、最后检查状态/消息、时间戳
- `StockIndicatorSnapshot`
  - 股票代码、交易日期、开盘价/最高价/最低价/收盘价、DIF、DEA、原始 MACD、K、D、J、原始数据时间戳、最后检查时间、时间戳
- `StockSignalLog`
  - 股票代码、交易日期、信号类型、触发来源、收盘价、J 值、原始 MACD 值、评估摘要、通知状态、通知尝试次数、通知发送时间、通知失败原因、时间戳

重要约束和索引：

- `StockWatch.stockCode` 唯一
- `StockIndicatorSnapshot(stockCode, tradeDate)` 唯一
- `StockSignalLog(stockCode, tradeDate, signalType)` 非唯一索引，用于折叠展示和通知去重
- `StockSignalLog(createdAt)` 和 `StockIndicatorSnapshot(tradeDate)` 索引，用于未来一年清理

### 服务层

新增服务：

- `XueqiuService`
  - 将支持的 A 股股票代码标准化为雪球代码
  - 使用配置的 Cookie 获取每日指标行
  - 验证 Cookie 失败、错误响应、响应顺序、缺失字段和最新交易日期
- `StockSignalService`
  - 纯信号状态评估器
  - 无网络或数据库依赖
  - 返回状态转换、信号结果和人类可读的无信号原因
- `WeChatNotificationService`
  - 使用 AppID 和 AppSecret 获取 Access Token（缓存 2 小时）
  - 使用模板ID发送模板消息到配置的 OpenID
  - 支持测试通知操作
  - 在日志和响应中对 AppSecret 进行掩码处理
  - 优雅处理微信 API 错误
- `StockMonitorService`
  - 创建和初始化监控
  - 加载已启用的监控项
  - 执行定时监控
  - 执行单个股票"立即检查"
  - 存储或更新最新快照
  - 更新监控状态和最后检查状态
  - 写入信号审计日志
  - 协调通知去重和重试
- `StockMonitorTask`
  - 工作日 17:10 Asia/Shanghai 定时任务
  - 任务级重试最多 3 次
  - 跳过非交易日

### 控制器

新增控制器：

- `StockMonitorConfigController`
  - 获取掩码后的配置状态
  - 更新或清除雪球 Cookie
  - 更新或清除微信配置（AppID、AppSecret、模板ID、OpenID）
  - 发送测试通知
- `StockWatchController`
  - 列出监控项
  - 创建监控并从最近 5 个已完成的交易日初始化观察状态
  - 更新股票名称或启用标志
  - 删除监控
  - 立即检查一个监控项
- `StockSignalController`
  - 列出折叠的最近信号组
  - 获取信号组审计详情
  - 重试信号组的通知
- `StockIndicatorController`
  - 获取用于列表展示的最新快照
  - 获取单个股票详情页的近期快照

## 前端设计

### 导航

新增导航项：`技术指标监控`

### 监控页面

监控页面包含：

- 自选股列表表格
- 添加股票操作
- 编辑股票名称操作
- 启用/禁用开关
- 删除操作
- 单个股票"立即检查"操作
- 跳转到股票监控详情页链接
- 列表列：代码、名称、启用状态、最新交易日、收盘价、J 值、原始 MACD、信号状态、最后信号、最后检查时间、最后检查状态、最后检查消息
- 当雪球 Cookie 或微信配置缺失或无效时，显示顶层配置警告

"立即检查"结果在操作完成后在前端展示，包括最新指标值、状态结果、信号结果和无信号或跳过原因。

### 股票监控详情页

详情页包含：

- 股票标识和当前监控状态
- 最新指标快照
- 按 `stockCode + tradeDate + signalType` 折叠的近期信号组
- 折叠组字段：
  - 信号类型
  - 交易日期
  - 首次触发时间
  - 最近检查时间
  - 触发次数
  - 通知状态
- 可展开的审计详情：
  - 每次检查时间
  - 触发来源
  - 收盘价
  - J 值和原始 MACD 值
  - 评估摘要
  - 通知状态/失败原因
- 为 `FAILED` 或 `NOT_SENT_MANUAL_CHECK` 的信号组提供重试通知操作

### 设置页面

在现有设置页面新增监控设置区域：

- 雪球 Cookie 文本框或密码样式输入
- 微信 AppID 输入
- 微信 AppSecret 输入（密码样式，永不回显）
- 微信模板ID 输入
- 微信 OpenID 输入
- 保存和清除按钮
- 测试通知按钮
- 显示掩码后的配置状态而非完整密钥

前端 API 按现有 React Query 和 Axios 模式添加，位于 `frontend/src/api`、`frontend/src/hooks` 和 `frontend/src/types`。

## Error Handling

- Missing or invalid Xueqiu cookie: fail the scheduled task immediately, do not process stocks, and show a top-level monitor configuration error in the frontend.
- Missing WeChat configuration: allow checks and signal logs, but notification attempts fail with a clear configuration error.
- Trading-day check failure: retry the whole scheduled task attempt up to 3 total attempts. If all attempts fail, stop the scheduled task.
- Non-trading day: stop the scheduled task without stock processing.
- Single-stock Xueqiu failure: mark that stock's latest check as failed and continue other stocks.
- Xueqiu latest date before today on a trading day: treat as suspended or no data; skip the stock and preserve existing observation state.
- Missing indicator fields or insufficient rows: skip the stock for that run and preserve existing observation state.
- WeChat API failure (network, timeout, rate limit): persist or update signal logs with failed notification status so the signal is auditable and retryable. Retry with exponential backoff.
- WeChat API error response (invalid AppID/Secret, invalid template): throw specific exception with clear error message, mark notification as FAILED.
- Duplicate notification key with existing `SENT` status: do not notify again.
- Ambiguous same-day buy/sell signal: mark stock check failed, do not write signal log, and do not notify.

Representative `lastCheckStatus` values:

- `SUCCESS`
- `FAILED`
- `FAILED_AMBIGUOUS_SIGNAL`
- `SKIPPED_SUSPENDED_OR_NO_DATA`
- `SKIPPED_INSUFFICIENT_DATA`
- `SKIPPED_CONFIG_MISSING`
- `SKIPPED_BEFORE_POST_CLOSE_WINDOW`

## 测试

本节描述股票技术指标监控功能的测试套件。测试使用 JUnit 5 和 Mockito，遵循 `ActualTradeServiceTest.java` 和 `TestFixtures.java` 中的现有模式。

### 测试夹具

在 `src/test/java/com/example/myapi/service/` 添加 `StockMonitorFixtures.java`，包含监控特定测试数据的构建器：

```java
public class StockMonitorFixtures {
    public static SnapshotBuilder snapshotBuilder() { return new SnapshotBuilder(); }
    public static SignalLogBuilder signalLogBuilder() { return new SignalLogBuilder(); }
    public static WatchBuilder watchBuilder() { return new WatchBuilder(); }
    public static KLineRowBuilder kLineRow() { return new KLineRowBuilder(); }
    public static XueqiuResponseBuilder xueqiuResponse() { return new XueqiuResponseBuilder(); }
}
```

`SnapshotBuilder` 构建器方法：

| 方法 | 默认值 | 描述 |
|------|--------|------|
| `stockCode(String)` | `"600000"` | 6 位 A 股代码 |
| `tradeDate(LocalDate)` | 今天 | 交易日期 |
| `close(BigDecimal)` | `"10.00"` | 收盘价 |
| `kdjj(BigDecimal)` | `"50.0"` | J 值 |
| `macd(BigDecimal)` | `"0.05"` | 原始 MACD 值 |

`WatchBuilder` 构建器方法：

| 方法 | 默认值 | 描述 |
|------|--------|------|
| `stockCode(String)` | `"600000"` | 6 位 A 股代码 |
| `signalState(SignalState)` | `NONE` | 观察状态 |
| `enabled(boolean)` | `true` | 启用标志 |

`KLineRowBuilder` 构建器方法（表示雪球响应中的一行）：

| 方法 | 默认值 | 描述 |
|------|--------|------|
| `timestamp(long epochMs)` | 今天中午 | 行时间戳 |
| `kdjj(double)` | `50.0` | J 值 |
| `macd(double)` | `0.02` | 原始 MACD 值 |

---

### StockSignalService 测试

`StockSignalService` 是一个纯函数——无 Spring 上下文、无数据库、无网络。所有测试直接使用输入输出，无需模拟。

测试类：`src/test/java/com/example/myapi/service/StockSignalServiceTest.java`

#### 观察进入测试

| ID | 描述 | 输入（J 系列，5 行） | 预期状态 |
|----|------|---------------------|----------|
| SWE-01 | 连续两天 J > 90 进入 WATCH_SELL | [85, 88, **91**, **95**, 88] | WATCH_SELL |
| SWE-02 | J > 90 但不相邻不进入 | [85, **91**, 88, **95**, 88] | NONE |
| SWE-03 | J == 90 不进入卖出观察 | [85, **90**, 95, 88, 85] | NONE |
| SWE-04 | 仅有 1 天 J > 90 不进入 | [**91**, 85, 88, 88, 85] | NONE |
| SWE-05 | 连续两天 J < 10 进入 WATCH_BUY | [15, 12, **8**, **5**, 12] | WATCH_BUY |
| SWE-06 | J == 10 不进入买入观察 | [15, **10**, 5, 12, 15] | NONE |
| SWE-07 | 连续三天 J > 90 进入 WATCH_SELL | [**91**, **95**, **98**, 88, 85] | WATCH_SELL |
| SWE-08 | 连续三天 J < 10 进入 WATCH_BUY | [**8**, **5**, **3**, 12, 15] | WATCH_BUY |

#### 信号确认测试

| ID | 描述 | 初始状态 | 今日 J | 昨日 J | 今日 MACD | 昨日 MACD | 预期 |
|----|------|---------|--------|--------|-----------|-----------|------|
| SC-01 | 卖出：J 下降 + MACD 下降 | WATCH_SELL | 88 | 92 | 0.01 | 0.05 | SELL, 状态 → NONE |
| SC-02 | 卖出：J 下降，MACD 持平 | WATCH_SELL | 88 | 92 | 0.05 | 0.05 | 无信号 |
| SC-03 | 卖出：MACD 下降，J 持平 | WATCH_SELL | 92 | 92 | 0.01 | 0.05 | 无信号 |
| SC-04 | 卖出：MACD 绿转红 | WATCH_SELL | 88 | 92 | -0.02 | 0.05 | SELL（MACD 降低） |
| SC-05 | 买入：J 上升 + MACD 上升 | WATCH_BUY | 12 | 8 | 0.08 | 0.05 | BUY, 状态 → NONE |
| SC-06 | 买入：J 上升，MACD 持平 | WATCH_BUY | 12 | 8 | 0.05 | 0.05 | 无信号 |
| SC-07 | 买入：MACD 红转绿 | WATCH_BUY | 12 | 8 | 0.05 | -0.02 | BUY（MACD 升高） |

#### 边界测试

| ID | 描述 | 预期 |
|----|------|------|
| BEC-01 | J = 90.0（BigDecimal 边界） | 不进入观察 |
| BEC-02 | J = 10.0（BigDecimal 边界） | 不进入观察 |
| BEC-03 | J = 90.001（刚超过） | 进入 WATCH_SELL |
| BEC-04 | J = 9.999（刚低于） | 进入 WATCH_BUY |
| BEC-05 | 少于 2 行 | NONE |
| BEC-06 | J 字段缺失 | 跳过该行，使用其余 |
| BEC-07 | 买卖条件同时满足 | 返回模糊，无信号 |

#### 延迟确认与覆盖测试

| ID | 描述 | 预期 |
|----|------|------|
| DCO-01 | 观察进入 3 天后确认 | 第 3 天发出信号 |
| DCO-02 | 对立极值替换 WATCH_SELL | 状态 → WATCH_BUY |
| DCO-03 | 信号发出后状态重置为 NONE | 后续检查使用 NONE |

---

### StockMonitorService 测试

测试类：`src/test/java/com/example/myapi/service/StockMonitorServiceTest.java`

使用 `@ExtendWith(MockitoExtension.class)`，模拟 `XueqiuService`、`WeChatNotificationService`、仓库和注入的 `Clock`。

#### 新监控初始化

| ID | 描述 | 验证 |
|----|------|------|
| NWI-01 | 从 5 日历史进入 WATCH_SELL | 状态 = WATCH_SELL，未创建快照，未写信号日志 |
| NWI-02 | 从 5 日历史进入 WATCH_BUY | 状态 = WATCH_BUY，未创建快照，未写信号日志 |
| NWI-03 | 无极值 → NONE | 状态 = NONE |
| NWI-04 | 两者都有：较晚进入者胜出 | 状态 = 较晚进入者的方向 |
| NWI-05 | 15:30 之前：排除今天的行 | 仅扫描 5 个已完成交易日 |
| NWI-06 | 可用行少于 5 行 | 扫描可用行 |
| NWI-07 | 无可用数据 | 状态 = NONE，记录警告 |

#### 立即检查 — 15:30 之前

| ID | 验证 |
|----|------|
| CBN-01 | 返回带有指标值的预览结果 |
| CBN-02 | 不持久化快照 |
| CBN-03 | 不更新监控状态 |
| CBN-04 | 不写信号日志 |
| CBN-05 | 不发送微信 |
| CBN-06 | 返回 `SKIPPED_BEFORE_POST_CLOSE_WINDOW` 状态 |

#### 立即检查 — 15:30 及之后

| ID | 验证 |
|----|------|
| CAN-01 | 为今天持久化快照 |
| CAN-02 | 更新监控状态 |
| CAN-03 | 有确认信号时写入信号日志，来源 = MANUAL_CHECK |
| CAN-04 | 不发送微信 |
| CAN-05 | 同一股票+日期的现有快照进行 upsert |
| CAN-06 | 数据不足 → `SKIPPED_INSUFFICIENT_DATA` |

#### 定时监控

| ID | 描述 | 验证 |
|----|------|------|
| SMT-01 | Cookie 缺失 | 任务在处理股票前失败 |
| SMT-02 | 非交易日 | 任务结束，不处理 |
| SMT-03 | 一个股票失败 | 其他股票继续处理 |
| SMT-04 | 停牌股票 | `SKIPPED_SUSPENDED_OR_NO_DATA`，状态保留 |
| SMT-05 | 信号确认 | 日志已写，来源 = SCHEDULED |
| SMT-06 | 重试待处理 NOT_SENT_MANUAL_CHECK | 定时任务发送通知 |
| SMT-07 | 任务级重试失败 | 最多 3 次尝试 |
| SMT-08 | 3 次全部失败 | 任务停止 |

#### 通知去重

| ID | 描述 | 验证 |
|----|------|------|
| ND-01 | 已有 SENT 日志 | 无重复微信调用 |
| ND-02 | 无 SENT 日志 | 调用微信 |
| ND-03 | 同一键的多个日志 | 成功后全部更新为 SENT |
| ND-04 | FAILED 日志 | 重新尝试发送 |

#### 错误处理

| ID | 描述 | 验证 |
|----|------|------|
| EHT-01 | 雪球返回行数不足 | `SKIPPED_INSUFFICIENT_DATA` |
| EHT-02 | 停牌：最新日期早于今天 | `SKIPPED_SUSPENDED_OR_NO_DATA`，状态保留 |
| EHT-03 | 微信配置缺失 | 信号已记录，微信调用失败时优雅处理 |
| EHT-04 | 模糊信号 | `FAILED_AMBIGUOUS_SIGNAL`，不写日志 |

---

### XueqiuService 测试

测试类：`src/test/java/com/example/myapi/service/XueqiuServiceTest.java`

#### 股票代码验证

| ID | 代码 | 预期 |
|----|------|------|
| XSV-01 | `"600000"` | 有效 → `SH600000` |
| XSV-02 | `"000001"` | 有效 → `SZ000001` |
| XSV-03 | `"300001"` | 有效 → `SZ300001` |
| XSV-04 | `"60000"`（5 位） | 无效 |
| XSV-05 | `"700000"`（无效前缀） | 无效 |
| XSV-06 | `"ABCDEF"`（字母） | 无效 |
| XSV-07 | `""`（空） | 无效 |
| XSV-08 | null | 无效 |

#### API 响应

| ID | 描述 | 预期 |
|----|------|------|
| XRP-01 | 有效响应 60 行 | 成功解析 |
| XRP-02 | 缺失可选字段 | 跳过 null，解析其他 |
| XRP-03 | 空数据数组 | 返回空列表 |
| XRP-04 | Cookie 无效（403） | 抛出 `XueqiuApiException` |
| XRP-05 | 行按升序排列 | 反转为降序 |
| XRP-06 | 停牌：最新日期早于今天 | 返回停牌标志 |

---

### WeChatNotificationService 测试

测试类：`src/test/java/com/example/myapi/service/WeChatNotificationServiceTest.java`

| ID | 描述 | 预期 |
|----|------|------|
| WWH-01 | 有效配置发送测试通知 | 成功发送（HTTP 200） |
| WWH-02 | 有效配置发送信号通知 | 消息包含股票代码、类型、价格、J 值、MACD |
| WWH-03 | AppSecret 无效 | 抛出异常，带微信错误响应 |
| WWH-04 | AppID/AppSecret 缺失 | 抛出 `WeChatNotConfiguredException` |
| WWH-05 | 日志不包含完整 AppSecret | 掩码后记录 |
| WWH-06 | 响应不返回完整 AppSecret | 只返回状态 |
| WWH-07 | Access token 获取失败 | 抛出异常，带微信错误响应 |
| WWH-08 | Access token 缓存重用 | 2 小时内不重复获取 |

---

### 集成测试

测试类：`src/test/java/com/example/myapi/service/StockMonitorServiceIntegrationTest.java`

使用 `@SpringBootTest` 和 H2 内存数据库，模拟外部服务（WireMock 或 `MockRestServiceServer`）。

#### 端到端流程

| ID | 描述 |
|----|------|
| E2E-01 | 创建监控 → 立即检查 → 信号确认 → 日志写入 → 通知发送 |
| E2E-02 | 创建监控 → 多次立即检查 → 延迟确认 |
| E2E-03 | 两个股票监控 → 一个触发 → 只有一条通知 |
| E2E-04 | 15:30 之前立即检查 → 无状态变更 → 15:30 及之后 → 状态变更 |
| E2E-05 | 非交易日的定时任务 → 优雅结束 |

#### 数据库约束

| ID | 描述 | 验证 |
|----|------|------|
| DCT-01 | StockWatch.stockCode 唯一约束 | 第二次插入失败 |
| DCT-02 | StockIndicatorSnapshot 唯一约束 | Upsert 更新现有行 |
| DCT-03 | 信号日志允许重复 | 重复信号创建新行 |

#### 外部 API 集成测试

遵循 `TushareServiceIntegrationTest.java` 的模式：从 `System.getenv()` 读取凭据，有硬编码回退，实际调用外部 API 验证连接。

##### 雪球 API 集成测试

测试类：`src/test/java/com/example/myapi/service/XueqiuServiceIntegrationTest.java`

| ID | 描述 | 预期 |
|----|------|------|
| XI-01 | 使用有效 Cookie 获取 K 线 | 返回 60+ 行，包含所有指标字段 |
| XI-02 | 获取沪市股票（6xxxxx） | 代码映射到 `SH6xxxxx`，返回数据 |
| XI-03 | 获取深市股票（0xxxxx, 3xxxxx） | 代码映射到 `SZ0xxxxx`/`SZ3xxxxx`，返回数据 |
| XI-04 | 使用无效/过期 Cookie | 抛出 `XueqiuApiException`，消息清晰 |
| XI-05 | 获取停牌/退市股票 | 返回最新可用日期的数据（早于今天） |
| XI-06 | 响应包含所有必需指标字段 | `kdjk`、`kdjd`、`kdjj`、`dif`、`dea`、`macd` 都存在 |
| XI-07 | 顺序获取多个股票 | 每个独立返回有效数据 |

```java
// 模式：从环境变量读取 Cookie，回退到硬编码测试值
String cookie = System.getenv("XUEQIU_COOKIE");
if (cookie == null || cookie.isBlank()) {
    cookie = "your-test-cookie-here";
}
```

##### 微信通知集成测试

测试类：`src/test/java/com/example/myapi/service/WeChatNotificationServiceIntegrationTest.java`

| ID | 描述 | 预期 |
|----|------|------|
| WI-01 | 使用有效配置发送测试通知 | 返回成功（HTTP 200） |
| WI-02 | 发送带有所有字段的信号通知 | 消息包含股票代码、类型、价格、J、MACD |
| WI-03 | 使用无效 AppID/Secret | 抛出异常，带微信错误响应 |
| WI-04 | 使用过期/无效凭据 | 抛出异常，带微信错误消息 |
| WI-05 | 模板格式正确渲染 | 微信显示格式化的消息 |
| WI-06 | 发送中文内容通知 | 股票名称和原因正确显示 |

```java
// 模式：从环境变量读取配置，回退到硬编码测试值
String appId = System.getenv("WECHAT_APPID");
String appSecret = System.getenv("WECHAT_APPSECRET");
String templateId = System.getenv("WECHAT_TEMPLATE_ID");
String openId = System.getenv("WECHAT_OPEN_ID");
```

> **注意**：将真实凭据存储在环境变量中供 CI/CD 使用。集成测试在正常 CI 运行中跳过（使用 `@EnabledIf` 或单独的 Maven profile），以避免限流或凭据未配置时失败。

---

### 测试执行

#### 运行测试

```bash
# StockSignalService 单元测试（纯函数，无需模拟）
./mvnw test -Dtest=StockSignalServiceTest

# 所有监控服务测试（带模拟）
./mvnw test -Dtest="StockMonitorServiceTest,XueqiuServiceTest,WeChatNotificationServiceTest"

# 集成测试
./mvnw test -Dtest=StockMonitorServiceIntegrationTest

# 所有监控相关测试
./mvnw test -Dtest="*StockMonitor*,*Xueqiu*,*WeChat*"
```

#### 时间窗口测试的 Clock 注入

在 `StockMonitorService` 中注入 `Clock` bean：

```java
// 生产配置
@Bean
public Clock clock() {
    return Clock.system(ZoneId.of("Asia/Shanghai"));
}

// 测试配置
@Bean
public Clock fixedClock() {
    return Clock.fixed(Instant.parse("2026-05-29T14:00:00+08:00"), ZoneId.of("Asia/Shanghai"));
}
```

通过注入不同的固定 Clock 测试 `14:00`（窗口前）和 `16:00`（窗口后）。

#### 集成测试设置

```yaml
# src/test/resources/application-test.yml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1
  jpa:
    hibernate:
      ddl-auto: create-drop
```

对集成测试使用 `@Transactional`，每个测试后回滚。

## 已确认的决策

- 第一版存储一个全局雪球 Cookie 和一个微信测试号配置
- Cookie 和 AppSecret 接受明文数据库存储作为第一版方案
- 定时任务在工作日 17:10 Asia/Shanghai 运行，不监控盘中更新
- 15:30 Asia/Shanghai 之后允许进行状态变更检查
- 雪球是该功能唯一的指标来源
- 第一版仅支持沪、深 A 股 6 位股票代码
- 新股票从最近 5 个已完成的交易日初始化观察状态
- "立即检查"永不发送微信通知
- 手动检查可写入重复的审计日志
- 每个股票、每个信号类型、每个交易日微信通知至多发送一次
- 信号日志和指标快照保留一年，第一版不自动清理
- 监控模块不与交易计划或实际交易集成
- 通知使用微信公众平台测试号模板消息 API
