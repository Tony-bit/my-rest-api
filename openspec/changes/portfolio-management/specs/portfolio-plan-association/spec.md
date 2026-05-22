## MODIFIED Requirements

### Requirement: Plan belongs to one portfolio

Each trading plan SHALL belong to exactly one portfolio. A plan without a portfolio SHALL NOT exist in the system.

#### Scenario: New plan requires portfolioId

- **WHEN** user sends POST /api/plans without portfolioId
- **THEN** the system SHALL return HTTP 400 Bad Request with validation error

#### Scenario: New plan with valid portfolioId

- **WHEN** user sends POST /api/plans with a valid portfolioId
- **THEN** the plan SHALL be created and associated with the specified portfolio

### Requirement: Plan queries filterable by portfolio

The system SHALL support filtering plans by portfolio when listing or querying.

#### Scenario: List plans by portfolio

- **WHEN** user sends GET /api/plans?portfolioId=1
- **THEN** the system SHALL return only plans belonging to portfolio 1

#### Scenario: List plans without portfolio filter

- **WHEN** user sends GET /api/plans without portfolioId parameter
- **THEN** the system SHALL return all plans across all portfolios

### Requirement: Plan cannot change portfolio after creation

A plan's portfolio association SHALL be immutable after creation.

#### Scenario: Attempt to change plan's portfolio

- **WHEN** user sends PUT /api/plans/{id} with a different portfolioId
- **THEN** the system SHALL ignore the portfolioId change and return the updated plan with its original portfolioId

### Requirement: SELL plan inherits portfolioId from BUY plan

When creating a SELL plan, the system SHALL automatically inherit the portfolioId from the associated BUY plan.

#### Scenario: SELL plan automatically inherits portfolioId

- **WHEN** user sends POST /api/plans/sell with a buyPlanId
- **THEN** the system SHALL automatically set the SELL plan's portfolioId to match the BUY plan's portfolioId
- **AND** the frontend SHALL NOT display a portfolio selector when creating SELL plans

#### Scenario: SELL plan and BUY plan are in the same portfolio

- **WHEN** a SELL plan is created for a BUY plan
- **THEN** both plans SHALL belong to the same portfolio
- **AND** the trade pair (entry/exit) is always within the same portfolio
