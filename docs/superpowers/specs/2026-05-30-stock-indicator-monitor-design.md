# Stock Indicator Monitor Design

## Goal

Add a daily stock indicator monitor to `my-rest-api`. The feature lets the user maintain a database-backed A-share watch list, fetches daily KDJ and MACD values from Xueqiu after market close, evaluates stateful buy/sell signals, writes auditable trigger history, and sends Enterprise WeChat group robot notifications.

The monitor is independent from the existing trading plan and actual-trade modules. It does not create plans, link to trades, update holdings, or change account balances.

## Scope

In scope:

- Store and manage one global Xueqiu cookie and one global Enterprise WeChat webhook from the frontend.
- Store and manage monitored stocks in the database.
- Support only Shanghai/Shenzhen A-share 6-digit stock codes in the first version.
- Fetch daily Xueqiu K-line indicator data for enabled stocks.
- Persist one daily indicator snapshot per stock and trade date.
- Persist repeated signal audit logs for monitor checks and triggered signals.
- Send at most one Enterprise WeChat notification per stock, signal type, and trade date.
- Provide a per-stock frontend "Check now" action that evaluates and displays results without sending Enterprise WeChat.
- Provide a stock monitor detail page for folded signal history and audit details.
- Provide a manual notification retry action for failed or pending notifications.

Out of scope:

- Trading order placement.
- Intraday monitoring and intraday signal state changes.
- Tushare fallback for indicator values.
- Complex backtesting or charting.
- User authentication and per-user configuration.
- Automatic integration with trading plans, actual trades, holdings, accounts, or daily portfolio snapshots.
- Automatic cleanup of old monitor records in the first version.
- Northbound/Beijing exchange stocks, Hong Kong stocks, US stocks, indexes, and ETFs.

## Supported Stock Codes

The first version accepts only 6-digit Shanghai/Shenzhen A-share stock codes:

- `6xxxxx` maps to Xueqiu symbol `SH6xxxxx`.
- `0xxxxx` maps to Xueqiu symbol `SZ0xxxxx`.
- `3xxxxx` maps to Xueqiu symbol `SZ3xxxxx`.

Both frontend and backend validate the code format. Invalid codes are rejected before they are added to the watch list. Stock name is entered by the user and is optional; if it is empty, the frontend displays the stock code.

## Data Source

The monitor uses Xueqiu's K-line JSON endpoint instead of calculating indicators locally.

The request includes:

- `symbol`: Xueqiu symbol such as `SZ301599` or `SH600000`.
- `period=day`
- `type=before`
- `count`: enough recent daily rows for signal evaluation and display. The first implementation should request at least 60 rows.
- `indicator=kline,ma,macd,kdj`

The response fields used by the monitor are:

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

The monitor uses Xueqiu's raw `macd` value directly. It does not multiply, divide, round, or otherwise transform the value before signal comparison.

The Xueqiu cookie is supplied by the user from the frontend and stored in the backend database. The first version accepts plaintext storage because the current app has no authentication or secret-management layer. Backend responses must never return the full cookie or webhook value; they return only configured status and a masked preview. Logs must not print full cookie or webhook values. The settings UI must support updating and clearing both values.

## Indicator Snapshot

`StockIndicatorSnapshot` records the daily indicator values used by the monitor:

- stock code
- trade date
- open, high, low, close
- DIF, DEA, raw MACD
- K, D, J
- raw source timestamp
- last checked time
- created and updated timestamps

There is only one snapshot per stock and trade date. Repeated checks on the same day update the same row. The final expected value is the latest post-close data for that trade date.

If the current date is a trading day but Xueqiu's latest K-line date for a stock is earlier than today, treat the stock as suspended or not traded that day. Skip the stock, do not update its indicator snapshot, do not update signal state, do not write a signal log, and do not send notification. Preserve the stock's previous observation state.

## Signal Model

Signals are stateful. A stock first enters an observation state after repeated KDJ extremes, then later emits a signal when KDJ and MACD confirm together.

States:

- `NONE`
- `WATCH_SELL`
- `WATCH_BUY`

Sell watch entry:

- Any two consecutive valid daily rows have `J > 90`.
- Once this occurs, the stock enters `WATCH_SELL`.

Sell signal confirmation:

- The stock is in `WATCH_SELL`.
- Today's `J` is lower than yesterday's `J`.
- Today's raw `MACD` value is lower than yesterday's raw `MACD` value.

This covers red-bar shrinkage, red-to-green reversal, and green-bar expansion because all are represented by today's raw MACD value being lower than yesterday's.

Buy watch entry:

- Any two consecutive valid daily rows have `J < 10`.
- Once this occurs, the stock enters `WATCH_BUY`.

Buy signal confirmation:

- The stock is in `WATCH_BUY`.
- Today's `J` is higher than yesterday's `J`.
- Today's raw `MACD` value is higher than yesterday's raw `MACD` value.

This covers green-bar shrinkage, green-to-red reversal, and red-bar expansion because all are represented by today's raw MACD value being higher than yesterday's.

After a buy or sell signal is identified, reset the stock state to `NONE`. The observation state means "a signal is being watched for"; once the signal exists and has been audited, the observation round is complete. Enterprise WeChat delivery is tracked separately through notification status on the signal logs.

If an opposite watch-entry condition appears before the current state triggers, the newer opposite state replaces the existing state.

Boundary behavior:

- `J == 90` does not enter sell watch.
- `J == 10` does not enter buy watch.
- Comparisons are exact `BigDecimal` comparisons with no tolerance.
- Missing or insufficient indicator rows produce no signal.
- A signal is not emitted unless both KDJ direction and MACD direction confirm on the same trade date.
- Confirmation can happen several days after watch entry; it is not limited to the third day after the two-day KDJ extreme.
- The business model assumes one stock can have at most one signal direction on a trade date. If abnormal data appears to satisfy both buy and sell signal paths, treat it as ambiguous data: mark the check failed, do not write a signal log, and do not notify.

## New Stock Initialization

When a stock is added to the watch list, the backend immediately initializes its observation state from the most recent 5 completed valid trading days.

Initialization behavior:

- If current time is before the post-close state-changing window and Xueqiu includes today's row, exclude today's row.
- Scan the latest 5 completed valid trading days for two consecutive `J > 90` or two consecutive `J < 10`.
- If only sell watch entry appears, initialize `signalState = WATCH_SELL`.
- If only buy watch entry appears, initialize `signalState = WATCH_BUY`.
- If both appear, use the observation state whose entry happened later.
- If neither appears, initialize `signalState = NONE`.
- Do not write historical signal logs.
- Do not send historical notifications.
- Even if the 5-day history would already satisfy confirmation, do not send or audit a historical signal at creation time. The next post-close check handles future confirmation.

## Time Windows

The monitor has two post-close concepts:

- `15:30 Asia/Shanghai`: state-changing checks are allowed after this time.
- `17:10 Asia/Shanghai`: the scheduled official daily monitor runs.

Before `15:30`, "Check now" may fetch and display temporary data, but it must not update indicator snapshots, update observation state, write signal logs, or send notifications.

From `15:30` onward, "Check now" may update the daily snapshot, update observation state, and write signal audit logs, but it still does not send Enterprise WeChat notifications.

The scheduled monitor runs at `17:10 Asia/Shanghai` on weekdays and is responsible for official daily monitoring and pending notification delivery.

## Scheduled Monitor Behavior

`StockMonitorTask` runs after market close at `17:10 Asia/Shanghai` from Monday to Friday. Saturday and Sunday never trigger.

When the task starts:

1. Validate that the Xueqiu cookie is configured and usable. If the cookie is missing or invalid, the task fails immediately.
2. Determine whether today is a trading day.
3. If today is not a trading day, end the task without processing stocks.
4. If task-level checks or task-level external calls fail, retry the whole task attempt up to 3 total attempts.
5. After 3 failed attempts, stop the task and log the failure.

Once the task has passed task-level checks and starts processing the stock list, a single-stock failure must not restart the whole task. The failed stock is skipped, its last check status is updated, and processing continues for other stocks.

Per-stock behavior:

- Fetch the latest daily indicator rows from Xueqiu.
- If Xueqiu latest trade date is before today, mark the stock as suspended or no-data for today and skip without state changes.
- If required indicator fields are missing or rows are insufficient, skip the stock without state changes.
- Persist or update today's indicator snapshot.
- Evaluate watch entry and signal confirmation.
- If a signal is identified, write a signal audit log and reset the stock state to `NONE`.
- Send Enterprise WeChat notification if no prior notification has succeeded for the same stock, trade date, and signal type.
- Retry pending manual-check and failed notifications that match the same notification key rules.

## Check Now Behavior

The frontend provides a per-stock "Check now" action on the monitor list and detail pages.

Before `15:30 Asia/Shanghai`, the action:

- Fetches the latest available indicator rows.
- Evaluates what the result would be for display.
- Does not update indicator snapshots.
- Does not update observation state.
- Does not write signal logs.
- Does not send Enterprise WeChat.
- Returns a result message such as "Before post-close window; monitor state was not updated."

At or after `15:30 Asia/Shanghai`, the action:

- Fetches latest indicator rows.
- Updates today's indicator snapshot if data is valid.
- Evaluates observation state and signal confirmation.
- Updates observation state.
- If a signal is identified, writes a signal audit log and resets observation state to `NONE`.
- Does not send Enterprise WeChat.
- Returns structured check results to the frontend.

The check result should include:

- latest trade date
- close price
- today's and yesterday's J values
- today's and yesterday's raw MACD values
- current signal state after the check
- whether buy/sell signal criteria were met
- reason when no signal is triggered
- last check status and message

If "Check now" identifies a signal, the resulting log has `notificationStatus = NOT_SENT_MANUAL_CHECK`. A later scheduled task or manual retry can send the Enterprise WeChat notification for that signal.

## Notification Behavior

Enterprise WeChat notifications are sent only by:

- the scheduled monitor task, or
- an explicit retry notification action from the detail page.

"Check now" never sends Enterprise WeChat.

The system sends at most one successful notification per `stockCode + tradeDate + signalType`.

Because `StockSignalLog` is also used for audit history and duplicate manual checks are allowed, signal logs are not unique by stock/date/type. Notification de-duplication is handled by querying logs with the same notification key:

- If any matching log already has `notificationStatus = SENT`, do not send again.
- If no matching log has `SENT`, a scheduled task may send for matching `NOT_SENT_MANUAL_CHECK`, `FAILED`, or newly scheduled signal logs.
- On successful send, update all matching non-sent logs to `SENT` with the same `notificationSentAt`.
- On failed send, mark the logs involved in the send attempt as `FAILED`, store failure reason, and increment attempt count.

The WeCom message content does not distinguish whether the signal was first found by scheduled monitoring or "Check now". Source is only shown in the audit UI.

## Signal Audit and Retention

`StockSignalLog` records each state-changing signal detection. Repeated checks on the same stock, trade date, and signal type are allowed and should produce repeated audit records.

Fields include:

- stock code
- trade date
- signal type
- trigger source: `SCHEDULED` or `MANUAL_CHECK`
- close price
- today's and yesterday's J values
- today's and yesterday's raw MACD values
- signal reason or evaluation summary
- notification status
- notification attempt count
- notification sent time
- notification failure reason
- created timestamp

Signal logs and indicator snapshots are retained for one year in the first version. The first version does not implement automatic aging or deletion. Add indexes on trade date and created time so a future cleanup task can be added without schema rework.

## Backend Design

Add database entities:

- `StockMonitorConfig`
  - singleton row for Xueqiu cookie, Enterprise WeChat webhook, and update timestamps.
- `StockWatch`
  - stock code, stock name, enabled flag, signal state, watch start date, last signal type/date, last checked time, last check status/message, timestamps.
- `StockIndicatorSnapshot`
  - stock code, trade date, open/high/low/close, DIF, DEA, raw MACD, K, D, J, raw source timestamp, last checked time, timestamps.
- `StockSignalLog`
  - stock code, trade date, signal type, trigger source, close price, J values, raw MACD values, evaluation summary, notification status, notification attempts, notification sent time, notification failure reason, timestamps.

Important constraints and indexes:

- Unique `StockWatch.stockCode`.
- Unique `StockIndicatorSnapshot(stockCode, tradeDate)`.
- Non-unique index `StockSignalLog(stockCode, tradeDate, signalType)` for folded display and notification de-duplication.
- Index `StockSignalLog(createdAt)` and `StockIndicatorSnapshot(tradeDate)` for future one-year cleanup.

Add services:

- `XueqiuService`
  - normalizes supported A-share stock codes to Xueqiu symbols.
  - fetches daily indicator rows using the configured cookie.
  - validates cookie failure, error responses, response order, missing fields, and latest trade date.
- `StockSignalService`
  - pure signal-state evaluator.
  - no network or database dependencies.
  - returns state transition, signal result, and human-readable no-signal reason.
- `WeComNotificationService`
  - sends markdown messages to the configured Enterprise WeChat webhook.
  - supports a test-notification operation.
  - masks webhook in logs and responses.
- `StockMonitorService`
  - creates and initializes watches.
  - loads enabled watches.
  - executes scheduled monitoring.
  - executes per-stock "Check now".
  - stores or updates latest snapshots.
  - updates watch state and last check status.
  - writes signal audit logs.
  - coordinates notification de-duplication and retry.
- `StockMonitorTask`
  - scheduled at `17:10 Asia/Shanghai` on weekdays.
  - performs task-level retry up to 3 attempts.
  - skips non-trading days.

Add controllers:

- `StockMonitorConfigController`
  - get masked config status.
  - update or clear Xueqiu cookie.
  - update or clear Enterprise WeChat webhook.
  - send test notification.
- `StockWatchController`
  - list watches.
  - create watch and initialize observation state from the latest 5 completed valid trading days.
  - update stock name or enabled flag.
  - delete watch.
  - check one watch now.
- `StockSignalController`
  - list folded recent signal groups.
  - get signal group audit details.
  - retry notification for a signal group.
- `StockIndicatorController`
  - get latest snapshots for list display.
  - get recent snapshots for one stock detail page.

## Frontend Design

Add a new navigation item: `Indicator Monitor`.

The monitor page contains:

- Watch-list table.
- Add stock action.
- Edit stock name action.
- Enable or disable toggle.
- Delete action.
- Per-stock "Check now" action.
- Link to stock monitor detail page.
- Columns for code, name, enabled status, latest trade date, close, J, raw MACD, signal state, last signal, last checked time, last check status, and last check message.
- Top-level config warning when Xueqiu cookie or WeCom webhook is missing or invalid.

The "Check now" result is shown in the frontend after the action completes. It includes the latest indicator values, state result, signal result, and no-signal or skipped reason.

Add a stock monitor detail page:

- Stock identity and current monitor state.
- Latest indicator snapshot.
- Recent folded signal groups by `stockCode + tradeDate + signalType`.
- Folded group fields:
  - signal type
  - trade date
  - first trigger time
  - latest check time
  - trigger count
  - notification status
- Expandable audit details:
  - each check time
  - trigger source
  - close price
  - J and raw MACD values
  - evaluation summary
  - notification status/failure reason
- Retry notification action for groups with `FAILED` or `NOT_SENT_MANUAL_CHECK`.

Update the existing settings page with a monitor settings section:

- Xueqiu cookie textarea or password-style input.
- Enterprise WeChat webhook input.
- Save and clear buttons.
- Test notification button.
- Masked configured status instead of full secret echoing.

Frontend API additions follow the existing React Query and Axios patterns under `frontend/src/api`, `frontend/src/hooks`, and `frontend/src/types`.

## Error Handling

- Missing or invalid Xueqiu cookie: fail the scheduled task immediately, do not process stocks, and show a top-level monitor configuration error in the frontend.
- Missing Enterprise WeChat webhook: allow checks and signal logs, but notification attempts fail with a clear configuration error.
- Trading-day check failure: retry the whole scheduled task attempt up to 3 total attempts. If all attempts fail, stop the scheduled task.
- Non-trading day: stop the scheduled task without stock processing.
- Single-stock Xueqiu failure: mark that stock's latest check as failed and continue other stocks.
- Xueqiu latest date before today on a trading day: treat as suspended or no data; skip the stock and preserve existing observation state.
- Missing indicator fields or insufficient rows: skip the stock for that run and preserve existing observation state.
- Enterprise WeChat failure: persist or update signal logs with failed notification status so the signal is auditable and retryable.
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

## Testing

Backend tests:

- Stock code validation accepts only `6xxxxx`, `0xxxxx`, and `3xxxxx`.
- New watch initialization scans the latest 5 completed valid trading days.
- New watch initialization excludes today's row before `15:30`.
- New watch initialization does not write historical signal logs or send notifications.
- Signal entry into `WATCH_SELL` after two consecutive `J > 90`.
- Signal entry into `WATCH_BUY` after two consecutive `J < 10`.
- `J == 90` and `J == 10` do not enter watch states.
- Sell confirmation can happen several days after watch entry.
- Buy confirmation can happen several days after watch entry.
- Sell confirmation requires both J decline and raw MACD decline.
- Buy confirmation requires both J increase and raw MACD increase.
- Signal confirmation resets observation state to `NONE`.
- Insufficient rows do not trigger signals and preserve previous state.
- Before `15:30`, "Check now" returns a result but does not persist snapshot, state, or signal log.
- After `15:30`, "Check now" persists snapshot and signal log but does not send WeCom.
- Scheduled task sends pending `NOT_SENT_MANUAL_CHECK` notification once.
- Repeated manual checks can create repeated signal logs.
- Existing `SENT` signal group prevents duplicate Enterprise WeChat notification.
- Enterprise WeChat failure is persisted and can be retried.
- Cookie missing or invalid fails the scheduled task before stock processing.
- Single-stock Xueqiu failure skips only that stock.
- Xueqiu latest date before today is treated as suspended/no-data and preserves state.
- Ambiguous same-day buy/sell data fails the stock check without logging or notifying.

Frontend checks:

- Settings form can save and clear cookie and webhook.
- Settings page shows masked configured status.
- Test notification action shows success or error.
- Watch list can create, edit, enable/disable, delete, and check a stock now.
- Watch list displays latest check status and message.
- "Check now" displays indicator and signal evaluation result.
- Detail page shows folded signal groups and expandable audit details.
- Retry notification action is available only for retryable notification statuses.

## Confirmed Decisions

- First version stores one global Xueqiu cookie and one global Enterprise WeChat webhook.
- Cookie and webhook are accepted as plaintext database values in the first version.
- The scheduled task runs at `17:10 Asia/Shanghai` on weekdays and does not monitor intraday updates.
- State-changing checks are allowed after `15:30 Asia/Shanghai`.
- Xueqiu remains the only indicator source for this feature.
- First version only supports Shanghai/Shenzhen A-share 6-digit stock codes.
- New stocks initialize observation state from the latest 5 completed valid trading days.
- "Check now" never sends Enterprise WeChat.
- Manual checks can write repeated audit logs.
- Enterprise WeChat is sent at most once per stock, signal type, and trade date.
- Signal logs and indicator snapshots are retained for one year, with no automatic cleanup in the first version.
- The monitor does not integrate with trading plans or actual trades.
