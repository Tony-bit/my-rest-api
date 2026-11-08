# 生益科技服务器每日复盘实现计划

> 致智能体工作者：本轮在当前会话内联执行，按任务勾选验收。用户已明确批准实现方案并要求加入my-rest-api。

目标：把已固定的策略运行在服务器，复用微信，每日发送操作或持仓提示。

架构：独立Tushare读取、纯指标计算、每日编排与通知；与旧监控隔离。技术栈：Java17、Spring Boot、JPA、JUnit5、Mockito。

### 任务1：隔离与基线

|任务|status|
|---|---|
|隔离与基线|pass|

- [x] 使用原生create_worktree从HEAD创建独立工作区；不复制未提交Docker改动。
- [x] 运行mvn test -q确认现有基线；无在线服务启动。

### 任务2：策略、数据与通知

|任务|status|
|---|---|
|策略、数据与通知|pass|

- [x] 新增ShengyiReviewService，按日期动态解析daily、adj_factor、trade_cal；有效数据不足则抛出受控错误。
- [x] 新增纯函数ShengyiTrendStrategy，EMA首值为第一根收盘价，K/D初值为首个有效RSV；递推式与Python一致，最高最低使用9日极值。
- [x] 为WeChatNotificationService新增每日复盘通知方法；first显示动作，keyword1股票，keyword2执行计划，keyword3信号日期，remark指标与依据。
- [x] 使用缓存历史CSV建立Java回归夹具，核对124日全部信号及最后一日J/MACD/MA10/量比。

### 任务3：调度、投递去重和预览

|任务|status|
|---|---|
|调度、投递去重和预览|pass|

- [x] 新增ReviewDelivery及Repository，唯一键(stock_code,execution_date,rule_version)，占位后发消息，记录SENT/FAILED，不对不确定结果自动重发。
- [x] 新增ShengyiReviewTask，cron为北京时间工作日09:00，enabled默认false。
- [x] 新增GET /api/stock-monitor/shengyi-review/preview，纯预览，无通知副作用。
- [x] Mock微信及数据读取，验证休市、行情未就绪、重复执行和投递失败。

### 任务4：验证与部署材料

|任务|status|
|---|---|
|验证与部署材料|append|

- [x] 运行mvn clean compile -q以及新增与现有股票监控相关测试。
- [x] 将设计、计划和测试验收写入docs/superpowers/plans及部署说明，配置只引用环境变量，不加入凭据。
- [ ] 检查Git差异、提交至专用分支，生成用户可审阅的更新说明。推送或服务器部署按本次授权范围单独处理。

验证记录：干净编译成功；本次15项新增测试与相关测试合计52项通过，含124日历史逐日信号一致。旧StockMonitorServiceTest.suspendedStock在原始HEAD同样失败，未修改该旧逻辑。未真实发送微信、未部署服务器。

发布阻塞：Git公共元数据HEAD.lock/index.lock写入被当前环境拒绝，尚未创建分支、提交或推送。源码和测试结果已准备，等待用户在本机终端运行发布脚本。
