## ADDED Requirements

### Requirement: DailySnapshot generation at market close

The system SHALL generate DailySnapshot records every trading day at 17:00 (UTC+8) via a scheduled task.

For each Plan in `PENDING` or `HOLDING` state, the system SHALL create a snapshot capturing:

- `planId`, `snapshotDate`
- `stockCode`, `stockName`
- `planStatus`: Current plan state
- `planReturn(%)`: Percentage gain/loss from the plan's perspective
  - If `PENDING`: 0 (not yet holding)
  - If `HOLDING`: `(currentClosePrice - triggerPrice) / triggerPrice * 100`
  - If `CLOSED`: `(sellTriggerPrice - buyTriggerPrice) / buyTriggerPrice * 100`
- `hasActualTrade`: Boolean indicating whether the user has entered an ActualTrade for this stock

#### Scenario: Snapshot for PENDING plan shows zero return

- **GIVEN** a plan in `PENDING` state for stock A
- **WHEN** a DailySnapshot is generated
- **THEN** planReturn SHALL be 0.0

#### Scenario: Snapshot for HOLDING plan shows current return

- **GIVEN** a plan in `HOLDING` state with triggerPrice 100.00
- **AND** today's close price is 105.00
- **WHEN** a DailySnapshot is generated
- **THEN** planReturn SHALL be 5.0

#### Scenario: Snapshot for CLOSED plan shows realized return

- **GIVEN** a plan in `CLOSED` state with buyPrice 100.00 and sellPrice 108.00
- **WHEN** a DailySnapshot is generated
- **THEN** planReturn SHALL be 8.0

### Requirement: DailySnapshot for ActualTrade open positions

For each stock with an open ActualTrade position, the system SHALL create a snapshot capturing:

- `stockCode`, `snapshotDate`
- `actualReturn(%)`: `(currentClosePrice - avgCostBasis) / avgCostBasis * 100`
- `openQuantity`: Current number of shares held
- `avgCostBasis`: Volume-weighted average price of unmatched buys

#### Scenario: ActualTrade snapshot shows unrealized gain

- **GIVEN** an open position with avgCostBasis 100.00 and current close 112.00
- **WHEN** a DailySnapshot is generated
- **THEN** actualReturn SHALL be 12.0

### Requirement: DailySnapshot API

The system SHALL provide an endpoint to query snapshots:

- `GET /api/snapshots` — List snapshots (filters: dateFrom, dateTo, planId, stockCode)
- `GET /api/snapshots/{planId}/{date}` — Get snapshot for a specific plan on a specific date

#### Scenario: Query snapshots by date range

- **WHEN** user sends GET /api/snapshots?dateFrom=2026-05-01&dateTo=2026-05-31
- **THEN** the system SHALL return all snapshots within that date range

### Requirement: Snapshot auto-cleanup

Snapshots older than 3 months SHALL be eligible for cleanup. Retention period: 3 months.

#### Scenario: Snapshots older than 3 months can be deleted

- **GIVEN** retention period is 3 months
- **WHEN** a cleanup job runs
- **THEN** snapshots older than 3 months SHALL be deleted
