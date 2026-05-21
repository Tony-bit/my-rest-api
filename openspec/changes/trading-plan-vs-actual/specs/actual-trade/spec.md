## ADDED Requirements

### Requirement: ActualTrade is independently managed

ActualTrade records represent real trades entered by the user. They SHALL be independent of Plan logic — the system SHALL NOT automatically create ActualTrade records from Plan executions.

The user MAY enter ActualTrade records at any time (including retroactively, e.g., buying on May 1 and selling on June 1).

#### Scenario: User enters a buy trade

- **WHEN** user sends POST /api/actual-trades with direction `BUY`, stockCode, price, quantity, tradeDate
- **THEN** an ActualTrade record SHALL be created and persisted

#### Scenario: User enters a sell trade with one-month lag

- **GIVEN** user bought A stock on May 1
- **WHEN** user enters the sell trade on June 1 (retroactively or on June 1)
- **THEN** the ActualTrade sell record SHALL be created with tradeDate June 1
- **AND** the system SHALL correctly compute profit/loss from May 1 buy price to June 1 sell price

### Requirement: ActualTrade CRUD operations

The system SHALL provide REST API endpoints for actual trade management:

- `POST /api/actual-trades` — Create a new actual trade record
- `GET /api/actual-trades` — List all actual trades (with optional filters: stockCode, direction, date range)
- `GET /api/actual-trades/{id}` — Get a single actual trade by ID
- `PUT /api/actual-trades/{id}` — Update an actual trade (user can correct mistakes)
- `DELETE /api/actual-trades/{id}` — Delete an actual trade

#### Scenario: List actual trades filtered by stock code

- **WHEN** user sends GET /api/actual-trades?stockCode=600519
- **THEN** the system SHALL return only ActualTrade records for stock 600519

#### Scenario: ActualTrade has no plan lock constraint

- **WHEN** a Plan is in HOLDING or CLOSED state
- **THEN** the user SHALL still be able to create, update, or delete ActualTrade records

### Requirement: ActualTrade profit calculation

The system SHALL compute profit/loss for ActualTrade records based on matched buy/sell pairs:

- Each sell trade SHALL be matched with the earliest unmatched buy trade for the same stock
- `profitLoss = (sellPrice - buyPrice) * quantity`
- `profitLossPercent = (sellPrice - buyPrice) / buyPrice * 100`

#### Scenario: Profit calculated for matched buy/sell

- **GIVEN** a buy trade at 100.00 for 1000 shares of stock A
- **AND** a sell trade at 110.00 for 1000 shares of stock A
- **WHEN** the system computes profit for the sell trade
- **THEN** profitLoss SHALL be 10,000.00
- **AND** profitLossPercent SHALL be 10.0

#### Scenario: Partial sell uses FIFO matching

- **GIVEN** a buy of 2000 shares at 100.00
- **AND** a sell of 1000 shares at 120.00
- **WHEN** the system computes profit for the sell trade
- **THEN** profitLoss SHALL be 20,000.00 (1000 shares * 20.00)
- **AND** the remaining 1000 shares SHALL remain as an open position

### Requirement: ActualTrade open position tracking

The system SHALL track the current open (unmatched) position for each stock in ActualTrade records:

- `openPosition(stock) = SUM(buy quantities) - SUM(sell quantities)` for that stock
- When open position > 0, the user is holding the stock
- The current cost basis SHALL be the volume-weighted average price of unmatched buys

#### Scenario: Open position after partial sell

- **GIVEN** buy 2000 shares at 100.00, sell 800 shares at 110.00
- **WHEN** open position is queried for this stock
- **THEN** open quantity SHALL be 1200 shares
- **AND** average cost SHALL be 100.00
