# Spec: Manual Trigger

## ADDED Requirements

### Requirement: Manual trigger endpoint

The system SHALL provide a manual trigger endpoint at `POST /api/trigger` that accepts a `targetDate` parameter and evaluates all pending plans with matching `triggerDate`.

#### Scenario: Trigger plans for specified date
- **WHEN** user sends `POST /api/trigger` with `targetDate: "2026-04-22"`
- **THEN** system SHALL fetch all plans where `triggerDate == "2026-04-22"` and `status == PENDING` and no executed PlanExecution exists

#### Scenario: Trigger with empty result
- **WHEN** user sends `POST /api/trigger` with `targetDate` that has no matching plans
- **THEN** system SHALL return `{ "totalPlans": 0, "triggered": 0, "skipped": 0, "details": [] }`

### Requirement: Historical K-line data fetch

The system SHALL fetch historical K-line data from Tushare for the specified target date using the same service as the scheduled task.

#### Scenario: Fetch historical data for trading day
- **WHEN** manual trigger evaluates a plan for target date "2026-04-22" (a trading day)
- **THEN** system SHALL fetch close/high/low/volume from Tushare for that date

#### Scenario: Fetch historical data for non-trading day
- **WHEN** manual trigger evaluates a plan for target date that has no trading data
- **THEN** system SHALL skip the plan and mark it as `data_unavailable` in the response

### Requirement: Condition evaluation

The system SHALL evaluate trigger conditions using the historical K-line data, the same logic as the scheduled task.

#### Scenario: Condition met
- **WHEN** historical close price meets the trigger condition (price >= targetPrice or price touches MA)
- **THEN** system SHALL execute the plan (create PlanExecution with executed=true, update plan status to HOLDING)

#### Scenario: Condition not met
- **WHEN** historical close price does NOT meet the trigger condition
- **THEN** system SHALL skip the plan and mark it as `condition_not_met` in the response

### Requirement: Idempotency protection

The system SHALL prevent duplicate execution of the same plan for the same target date.

#### Scenario: Plan already executed
- **WHEN** a PlanExecution record already exists for the plan with `executed == true`
- **THEN** system SHALL skip the plan and mark it as `already_executed` in the response

### Requirement: Trigger response format

The system SHALL return a structured response after manual trigger execution.

#### Scenario: Successful trigger response
- **WHEN** manual trigger completes
- **THEN** system SHALL return JSON with:
  - `targetDate`: the date that was triggered
  - `totalPlans`: count of plans evaluated
  - `triggered`: count of plans that were executed
  - `skipped`: count of plans that were skipped
  - `details`: array of `{ planId, stockName, status }` for each plan

### Requirement: Share logic with scheduled task

The system SHALL reuse the core trigger evaluation logic from `MarketCloseTask` for manual trigger to avoid code duplication.

#### Scenario: Consistent behavior
- **WHEN** manual trigger evaluates a plan
- **THEN** the evaluation logic SHALL be identical to the scheduled task
