# 生益科技每日复盘：服务器更新说明

新逻辑在服务器运行，手机通过现有微信通知通道接收，不依赖个人电脑或手机Codex持续在线。

## 新增行为

北京时间工作日09:00，根据交易日历确认今日开市并获取上一交易日数据。用固定MA10、标准KDJ、MACD与量比规则，发送买入1/3、卖出1/3或继续持仓/观望。行情、复权或日历异常时停止，不发送买卖建议。沿用数据库已有微信配置和Tushare环境配置；不增加Python依赖，不修改旧股票观察状态或原17:10任务。

## 发布前检查

1. 确认服务器Git工作区没有需要保留的未提交改动，备份数据库。
2. 取得本次专用分支或合并后的提交。只拉取代码不足以更新正在运行的Docker容器，还要重建并重启后端。
3. 确认Tushare的daily、adj_factor、trade_cal权限以及现有微信配置均可用，不把凭据提交到Git。
4. 新表`shengyi_review_delivery`由当前项目已有的Hibernate `ddl-auto=update`创建。若生产改为validate/none，应由维护者根据实体准备正式迁移。

## 部署方式

新任务默认关闭。先使用线上原有Compose命令更新代码和重建后端，保持未启用，然后验证只读接口。不要换掉原有环境文件、数据库卷或部署配置。

如果线上服务名为`app`，启用时给原来的Compose文件追加`docker-compose.shengyi-review.yml`：

```bash
# 以线上原本就使用docker-compose.prod.yml为例
SHENGYI_REVIEW_ENABLED=true docker compose -f docker-compose.prod.yml -f docker-compose.shengyi-review.yml up -d --build app
```

如果线上使用`docker-compose-app.yml`，将上面的基础文件替换为它；该文件是用户现有部署配置，不包含在本次分支中。

如果线上使用all-in-one且服务名为`backend`，使用对应覆盖文件：

```bash
SHENGYI_REVIEW_ENABLED=true docker compose -f docker-compose.all-in-one.yml -f docker-compose.shengyi-review.backend.yml up -d --build backend
```

无Compose时，给现有Java/Docker进程增加环境变量`SHENGYI_REVIEW_ENABLED=true`并使用原部署方式重启。Java属性`-Dshengyi.review.enabled=true`也可启用。

## 预览与验收

```bash
# 适用于后端8091已映射到本机的部署；不发微信
curl --fail http://127.0.0.1:8091/api/stock-monitor/shengyi-review/preview
```

all-in-one默认不映射后端端口，可经已有Nginx反向代理访问同一路由；代理前缀按线上配置确定。预览的`data.status=OK`且`signalDate`为上一交易日才算行情校验成功；休市返回`MARKET_CLOSED`。核对动作和指标后再启用任务。

开市日09:00后检查服务日志`Shengyi daily review result=SENT`及微信实际到达。数据库同一执行日、股票、规则版本只允许一次投递尝试，重启或多实例不会直接重复推送。发送失败或结果不确定会记录FAILED并停止，不盲目重发；需要核对微信后人工处理。没有发送失败时补发的自动承诺。

## 回滚

将`SHENGYI_REVIEW_ENABLED`设为false，按原命令重新创建后端容器即可停用新任务。不要删除旧监控配置或数据。确认服务器任务稳定后，再停用电脑端同名Codex定时任务，避免两套提醒同时运行。

## 已知边界

任务不追踪真实仓位，也不自动下单。微信模板仍需服务器可用且当前账号可发送，按已有配置投递。行情规则来自半年回看研究，未证实未来收益优势。原17:10监控仍保留，若生益科技也在原自选列表，可能额外收到旧策略的信号提醒；是否停用该项由用户决定。
