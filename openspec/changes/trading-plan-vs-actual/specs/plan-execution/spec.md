## ADDED Requirements

### Requirement: PlanExecution record creation

A PlanExecution record SHALL be created each time a trigger condition is evaluated and fires. Each record SHALL capture:

- `planId`: Reference to the parent Plan
- `tradeDate`: The date when the trigger was evaluated
- `direction`: `BUY` or `SELL`
- `triggered`: Boolean, always `true` when a record is created
- `triggerPrice`: The closing price of that day (from Tushare)
- `closePrice`: Alias for triggerPrice (closing price of evaluation day)
- `maValue`: The MA value used in the calculation
- `conditionId`: Reference to the PlanCondition that fired
- `executed`: Boolean indicating whether the system considers this executed

#### Scenario: PlanExecution created on buy trigger

- **WHEN** a buy condition is satisfied and the plan transitions from PENDING to HOLDING
- **THEN** a PlanExecution record SHALL be created with `direction=BUY`, `triggered=true`

#### Scenario: PlanExecution created on sell trigger

- **WHEN** a sell condition is satisfied and the plan transitions from HOLDING to CLOSED
- **THEN** a PlanExecution record SHALL be created with `direction=SELL`, `triggered=true`

#### Scenario: Multiple executions tracked for same plan

- **GIVEN** a plan has both buy and sell conditions
- **WHEN** buy triggers on day D1 and sell triggers on day D5
- **THEN** two PlanExecution records SHALL exist: one for D1 (BUY) and one for D5 (SELL)

### Requirement: PlanExecution retrieval

The system SHALL provide an endpoint to retrieve execution history for a plan:

- `GET /api/plans/{planId}/executions` — List all PlanExecution records for a plan

#### Scenario: Retrieve all executions for a plan

- **WHEN** user sends GET /api/plans/{planId}/executions
- **THEN** the system SHALL return all PlanExecution records ordered by tradeDate ascending

#### Scenario: Empty execution list for new plan

- **WHEN** a newly created plan has not yet triggered
- **THEN** GET /api/plans/{planId}/executions SHALL return an empty array
