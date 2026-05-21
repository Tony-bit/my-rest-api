## ADDED Requirements

### Requirement: Period summary view (View B)

The system SHALL provide a period summary API that aggregates plan returns and actual returns over a specified time period:

- `GET /api/views/period-summary?period=WEEK` — Current week summary
- `GET /api/views/period-summary?period=MONTH` — Current month summary
- `GET /api/views/period-summary?period=YEAR` — Current year summary

The response SHALL include:

- `period`: The time period (WEEK / MONTH / YEAR)
- `periodStart`, `periodEnd`: Start and end dates of the period
- `planSummary`:
  - `totalTrades`: Number of plans that completed (CLOSED) in this period
  - `totalReturn(%)`: Sum of planReturn for all CLOSED plans in this period
  - `avgReturn(%)`: Average return across all CLOSED plans
  - `pendingCount`: Number of plans still in PENDING or HOLDING state
- `actualSummary`:
  - `totalTrades`: Number of ActualTrade sell executions in this period
  - `totalReturn(%)`: Sum of profitLossPercent for all sells in this period
  - `avgReturn(%)`: Average return across all sells
- `gap(%)`: `planSummary.avgReturn - actualSummary.avgReturn` (知行差)
- `planList`: Array of plan snapshots in this period with their returns
- `actualList`: Array of actual trade executions in this period with their returns

#### Scenario: Weekly summary shows all completed plans

- **GIVEN** 3 plans CLOSED in the current week with returns of 5%, -2%, 8%
- **AND** 2 actual sells in the current week with returns of 4%, 6%
- **WHEN** user requests GET /api/views/period-summary?period=WEEK
- **THEN** planSummary.totalTrades SHALL be 3
- **AND** planSummary.avgReturn SHALL be 3.67% ((5-2+8)/3)
- **AND** actualSummary.avgReturn SHALL be 5.0% ((4+6)/2)
- **AND** gap SHALL be -1.33% (3.67 - 5.0)

#### Scenario: Empty period shows zero returns

- **GIVEN** no plans were closed and no actual trades occurred in the current month
- **WHEN** user requests GET /api/views/period-summary?period=MONTH
- **THEN** all return values SHALL be 0.0
- **AND** planList and actualList SHALL be empty arrays
