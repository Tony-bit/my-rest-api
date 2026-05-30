# Stock Indicator Monitor Design

## Goal

Add a daily stock indicator monitor to `my-rest-api`. The service lets the user maintain a database-backed watch list, fetches daily KDJ and MACD values from Xueqiu once after market close, evaluates stateful buy/sell signals, and sends Enterprise WeChat group robot notifications.

## Scope

In scope:

- Store and manage Xueqiu cookie and Enterprise WeChat webhook from the frontend.
- Store and manage monitored stocks in the database.
- Fetch daily Xueqiu K-line indicator data for enabled stocks.
- Persist daily indicator snapshots and signal logs.
- Send one notification per stock, signal type, and trade date.
- Provide manual backend endpoints for testing a stock or running the monitor.
- Add frontend screens for settings and watch-list management.

Out of scope:

- Trading order placement.
- Intraday monitoring.
- Tushare fallback for indicator values.
- Complex backtesting or charting.
- User authentication and per-user configuration.

## Data Source

The monitor uses Xueqiu's K-line JSON endpoint instead of calculating indicators locally.

The request includes:

- `symbol`: Xueqiu symbol such as `SZ301599` or `SH600000`.
- `period=day`
- `type=before`
- `count`: enough recent daily rows for signal evaluation and display.
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

The Xueqiu cookie is supplied by the user from the frontend and stored in the backend database. Backend responses should not return the full cookie value; they should return only configured status or a masked preview.

## Signal Model

Signals are stateful. A stock first enters an observation state after repeated KDJ extremes, then later emits a signal when KDJ and MACD confirm together.

States:

- `NONE`
- `WATCH_SELL`
- `WATCH_BUY`

Sell watch entry:

- Any two consecutive daily rows have `J > 90`.
- Once this occurs, the stock enters `WATCH_SELL`.

Sell signal confirmation:

- The stock is in `WATCH_SELL`.
- Today's `J` is lower than yesterday's `J`.
- Today's `MACD` bar value is lower than yesterday's `MACD` bar value.

This covers red-bar shrinkage, red-to-green reversal, and green-bar expansion because all are represented by today's MACD value being lower than yesterday's.

Buy watch entry:

- Any two consecutive daily rows have `J < 10`.
- Once this occurs, the stock enters `WATCH_BUY`.

Buy signal confirmation:

- The stock is in `WATCH_BUY`.
- Today's `J` is higher than yesterday's `J`.
- Today's `MACD` bar value is higher than yesterday's `MACD` bar value.

This covers green-bar shrinkage, green-to-red reversal, and red-bar expansion because all are represented by today's MACD value being higher than yesterday's.

After a buy or sell signal is sent, reset the stock state to `NONE`. This prevents repeated daily notifications from the same observation state. If an opposite watch-entry condition appears before the current state triggers, the newer opposite state may replace the existing state.

Boundary behavior:

- `J == 90` does not enter sell watch.
- `J == 10` does not enter buy watch.
- Missing or insufficient indicator rows produce no signal.
- A signal is not emitted unless both KDJ direction and MACD direction confirm on the same trade date.
- The confirmation can happen several days after watch entry; it is not limited to the third day after the two-day KDJ extreme.

## Backend Design

Add database entities:

- `StockMonitorConfig`
  - singleton row for Xueqiu cookie, Enterprise WeChat webhook, and update timestamps.
- `StockWatch`
  - stock code, stock name, enabled flag, signal state, watch start date, last signal type/date, timestamps.
- `StockIndicatorSnapshot`
  - stock code, trade date, close price, DIF, DEA, MACD, K, D, J, raw source timestamp, timestamps.
- `StockSignalLog`
  - stock code, trade date, signal type, close price, J values, MACD values, notification status, timestamps.

Add services:

- `XueqiuService`
  - normalizes stock codes to Xueqiu symbols.
  - fetches daily indicator rows using the configured cookie.
  - validates error responses and missing fields.
- `StockSignalService`
  - pure signal-state evaluator.
  - no network or database dependencies.
- `WeComNotificationService`
  - sends markdown messages to the configured Enterprise WeChat webhook.
  - supports a test-notification operation.
- `StockMonitorService`
  - loads enabled watches.
  - fetches indicator rows.
  - stores the latest snapshot.
  - updates watch state.
  - writes signal logs and sends notifications.
- `StockMonitorTask`
  - scheduled after market close, for example `17:10` Asia/Shanghai on weekdays.
  - skips when the current date is not a trading day if the existing `TushareService.isTradingDay` is available.

Add controllers:

- `StockMonitorConfigController`
  - get masked config status.
  - update Xueqiu cookie.
  - update Enterprise WeChat webhook.
  - send test notification.
- `StockWatchController`
  - list watches.
  - create watch.
  - update stock name or enabled flag.
  - delete watch.
  - manually run one watch.
- `StockSignalController`
  - list recent signal logs and latest snapshots.

## Frontend Design

Add a new navigation item: `Indicator Monitor`.

The monitor page contains:

- Watch-list table.
- Add stock action.
- Edit stock action.
- Enable or disable toggle.
- Delete action.
- Manual check action.
- Columns for code, name, enabled status, latest trade date, close, J, MACD, signal state, last signal, and last checked time.

Update the existing settings page with a monitor settings section:

- Xueqiu cookie textarea or password-style input.
- Enterprise WeChat webhook input.
- Save buttons.
- Test notification button.
- Masked configured status instead of full secret echoing.

Frontend API additions follow the existing React Query and Axios patterns under `frontend/src/api`, `frontend/src/hooks`, and `frontend/src/types`.

## Error Handling

- Missing Xueqiu cookie: return a clear configuration error and skip scheduled monitoring.
- Xueqiu 4xx or 5xx response: log the stock and response summary, leave the previous state unchanged, and continue other stocks.
- Missing indicator fields: skip the stock for that run.
- Enterprise WeChat failure: persist the signal log with failed notification status so the signal is auditable.
- Duplicate signal for the same stock, trade date, and signal type: do not notify again.

## Testing

Backend tests:

- Signal entry into `WATCH_SELL` after two consecutive `J > 90`.
- Signal entry into `WATCH_BUY` after two consecutive `J < 10`.
- `J == 90` and `J == 10` do not enter watch states.
- Sell confirmation can happen several days after watch entry.
- Buy confirmation can happen several days after watch entry.
- Sell confirmation requires both J decline and MACD bar-value decline.
- Buy confirmation requires both J increase and MACD bar-value increase.
- Insufficient rows do not trigger signals.
- Duplicate signal logs do not produce duplicate notifications.

Frontend checks:

- Settings form can save cookie and webhook.
- Test notification action shows success or error.
- Watch list can create, edit, enable/disable, delete, and manually check a stock.

## Open Decisions

- The first implementation stores one global Xueqiu cookie and one global Enterprise WeChat webhook.
- The scheduled task runs after market close and does not monitor intraday updates.
- Xueqiu remains the only indicator source for this feature.
