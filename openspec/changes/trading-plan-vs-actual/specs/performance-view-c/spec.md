## ADDED Requirements

### Requirement: Current holdings overview (View C)

The system SHALL provide a current holdings overview that shows all active positions from both the plan side and the actual trade side:

- `GET /api/views/holdings` — Get current holdings summary

The response SHALL include:

- `generatedAt`: Timestamp when this view was generated
- `planHoldings`: Array of plans currently in `HOLDING` state
  - `planId`, `stockCode`, `stockName`
  - `buyTriggerPrice`: The price at which plan triggered buy
  - `currentPrice`: Latest close price from Tushare
  - `quantity`: Assumed quantity (user-configured, default 100 shares per plan)
  - `unrealizedPL`: `(currentPrice - buyTriggerPrice) * quantity`
  - `unrealizedPL(%)`: `(currentPrice - buyTriggerPrice) / buyTriggerPrice * 100`
  - `holdDays`: Number of days since buy trigger
- `actualHoldings`: Array of open ActualTrade positions
  - `stockCode`, `stockName`
  - `avgCostBasis`: Volume-weighted average price of unmatched buys
  - `currentPrice`: Latest close price from Tushare
  - `openQuantity`: Number of shares still held
  - `unrealizedPL`: `(currentPrice - avgCostBasis) * openQuantity`
  - `unrealizedPL(%)`: `(currentPrice - avgCostBasis) / avgCostBasis * 100`
- `summary`:
  - `totalPlanUnrealizedPL(%)`: Sum of unrealizedPL(%) for all plan holdings
  - `totalActualUnrealizedPL(%)`: Sum of unrealizedPL(%) for all actual holdings
  - `holdingGap(%)`: `totalPlanUnrealizedPL(%) - totalActualUnrealizedPL(%)`

#### Scenario: View C shows plan holding with unrealized gain

- **GIVEN** a plan in HOLDING state: bought at 80.00, current close is 88.00
- **WHEN** user requests GET /api/views/holdings
- **THEN** the plan holding SHALL show unrealizedPL = 8.00 and unrealizedPL(%) = 10.0%

#### Scenario: View C shows actual holding with open position

- **GIVEN** an actual trade position: avgCostBasis 75.00, 1500 shares, current close 81.00
- **WHEN** user requests GET /api/views/holdings
- **THEN** the actual holding SHALL show unrealizedPL = 9,000.00 and unrealizedPL(%) = 8.0%

#### Scenario: Holdings with no open positions

- **GIVEN** no plans in HOLDING state and no open ActualTrade positions
- **WHEN** user requests GET /api/views/holdings
- **THEN** planHoldings and actualHoldings SHALL be empty arrays
- **AND** all summary values SHALL be 0.0

### Requirement: Holdings refresh with latest prices

- `GET /api/views/holdings?refresh=true` — Force refresh by querying Tushare for latest prices

#### Scenario: Refresh fetches latest prices from Tushare

- **WHEN** user requests GET /api/views/holdings?refresh=true
- **THEN** the system SHALL query Tushare for current prices for all held stocks
- **AND** the returned currentPrice values SHALL be the latest available
