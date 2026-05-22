## ADDED Requirements

### Requirement: Portfolio entity

A Portfolio represents a named group of trading plans. It contains metadata for organizing plans by strategy style.

#### Scenario: Portfolio has required fields

- **WHEN** a Portfolio is created
- **THEN** it SHALL have: id, name (required, max 100 chars), description (optional, max 500 chars), style (optional, max 50 chars), userId (optional, reserved for future multi-user isolation), createdAt (auto-set)

### Requirement: Portfolio CRUD operations

The system SHALL provide REST API endpoints for portfolio management:

- `GET /api/portfolios` — List all portfolios (with optional filters)
- `POST /api/portfolios` — Create a new portfolio
- `GET /api/portfolios/{id}` — Get a single portfolio by ID (with associated plans)
- `PUT /api/portfolios/{id}` — Update portfolio metadata
- `DELETE /api/portfolios/{id}` — Delete a portfolio (only allowed if no plans associated)

#### Scenario: Create a new portfolio

- **WHEN** user sends POST /api/portfolios with name, description, and style
- **THEN** the system SHALL create the portfolio and return HTTP 201 with the portfolio entity

#### Scenario: Delete portfolio with associated plans is forbidden

- **WHEN** user sends DELETE /api/portfolios/{id} where the portfolio has associated plans
- **THEN** the system SHALL return HTTP 409 Conflict with an error message

### Requirement: Portfolio statistics aggregation

The system SHALL compute aggregate statistics for each portfolio based on its associated plans.

#### Scenario: Portfolio statistics include total return

- **WHEN** user fetches portfolio list
- **THEN** totalReturn SHALL be calculated as (marketValue + cashBalance - baselineCapital) / baselineCapital × 100%

#### Scenario: Portfolio statistics include profit and loss amounts

- **WHEN** user fetches portfolio list
- **THEN** the system SHALL return:
  - totalProfit: sum of all profitable closed plan returns
  - totalLoss: sum of all loss-making closed plan returns
  - profitCount: count of profitable closed plans
  - lossCount: count of loss-making closed plans
  - winRate: profitCount / (profitCount + lossCount) × 100%

#### Scenario: Portfolio statistics only include CLOSED plans for profit/loss calculation

- **WHEN** calculating portfolio profit/loss statistics
- **THEN** only plans with status CLOSED SHALL be included
- **AND** plans with status PENDING, HOLDING, or EXPIRED SHALL NOT be included

#### Scenario: Portfolio plan count includes all statuses

- **WHEN** fetching portfolio summary
- **THEN** planCount SHALL include all plans regardless of status
- **AND** holdingCount SHALL include only plans with status HOLDING

### Requirement: Portfolio overview with return curve

The system SHALL provide an endpoint to fetch portfolio overview with return curve data.

#### Scenario: Fetch portfolio overview

- **WHEN** user sends GET /api/portfolios/{id}/overview
- **THEN** the system SHALL return portfolio details, summary statistics, and return curve data

#### Scenario: Return curve supports time range filter

- **WHEN** user sends GET /api/portfolios/{id}/overview with range parameter
- **THEN** the system SHALL return return curve data for the specified time range
- **AND** supported ranges are: `7d` (7 days), `30d` (30 days), `all` (all time)

#### Scenario: Return curve data format

- **WHEN** user fetches return curve
- **THEN** each data point SHALL contain date and portfolio total return value
- **AND** data points SHALL be sorted by date in ascending order

### Requirement: Portfolio plan listing

The system SHALL display all plans within a portfolio regardless of their status.

#### Scenario: List all plans in portfolio

- **WHEN** user fetches portfolio details or plan list filtered by portfolioId
- **THEN** the system SHALL return plans with status PENDING, HOLDING, CLOSED, and EXPIRED
- **AND** each plan SHALL include its return percentage (if calculable)
