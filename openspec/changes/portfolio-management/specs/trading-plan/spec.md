## MODIFIED Requirements

### Requirement: Plan CRUD operations

The system SHALL provide REST API endpoints for plan management:

- `POST /api/plans` — Create a new plan (requires portfolioId)
- `GET /api/plans` — List all plans (with optional filters: status, cycle, stockCode, **portfolioId**)
- `GET /api/plans/{id}` — Get a single plan by ID
- `PUT /api/plans/{id}` — Update a PENDING plan (portfolioId is immutable)
- `DELETE /api/plans/{id}` — Delete a plan (PENDING or EXPIRED only)

#### Scenario: Create a new plan with portfolio association

- **WHEN** user sends POST /api/plans with valid plan data including portfolioId
- **THEN** the system SHALL create the plan in `PENDING` state associated with the specified portfolio
- **AND** return HTTP 201 with the plan entity including portfolioId

#### Scenario: Create a plan without portfolioId fails

- **WHEN** user sends POST /api/plans without portfolioId
- **THEN** the system SHALL return HTTP 400 Bad Request with validation error "portfolioId is required"

#### Scenario: List plans filtered by portfolio

- **WHEN** user sends GET /api/plans?portfolioId=1
- **THEN** the system SHALL return only plans belonging to portfolio 1

#### Scenario: List plans filtered by status

- **WHEN** user sends GET /api/plans?status=HOLDING
- **THEN** the system SHALL return only plans in `HOLDING` state

#### Scenario: Delete a HOLDING plan is forbidden

- **WHEN** user sends DELETE /api/plans/{id} for a plan in `HOLDING` or `CLOSED` state
- **THEN** the system SHALL return HTTP 409 Conflict

#### Scenario: Create a SELL plan with inherited portfolioId

- **WHEN** user sends POST /api/plans/sell with buyPlanId
- **THEN** the system SHALL create the SELL plan with portfolioId inherited from the BUY plan
- **AND** the SELL plan's portfolioId SHALL NOT be settable by the user
