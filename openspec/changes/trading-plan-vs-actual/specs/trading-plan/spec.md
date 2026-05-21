## ADDED Requirements

### Requirement: Plan lifecycle states

A trading plan SHALL exist in exactly one of the following states:

- `PENDING`: Plan has been created and is waiting for buy trigger. This is the initial state.
- `HOLDING`: A buy trigger has been fired and the plan is holding stock. Locked from further edits.
- `CLOSED`: Both buy and sell triggers have been fired. Plan execution is complete.
- `EXPIRED`: The plan's validity window has passed without any trigger. Plan is closed.

State transitions SHALL follow this machine:

```
PENDING --buy triggered--> HOLDING --sell triggered--> CLOSED
   |
   +--validity window ended without trigger--> EXPIRED
```

#### Scenario: Plan starts in PENDING state

- **WHEN** a user creates a new plan
- **THEN** the plan's state SHALL be `PENDING`

#### Scenario: Plan transitions to HOLDING on buy trigger

- **WHEN** the buy condition is satisfied on a trading day
- **THEN** the plan's state SHALL transition to `HOLDING` and the plan SHALL be locked (isLocked = true)

#### Scenario: Plan transitions to CLOSED on sell trigger

- **WHEN** the sell condition is satisfied while in HOLDING state
- **THEN** the plan's state SHALL transition to `CLOSED`

#### Scenario: Plan expires when validity window passes without trigger

- **WHEN** the last day of the validity window passes without any trigger
- **THEN** the plan's state SHALL transition to `EXPIRED`

### Requirement: Plan editability before execution

A plan in `PENDING` state SHALL be editable. A plan in any other state SHALL NOT be editable.

#### Scenario: User can edit a PENDING plan

- **WHEN** a user sends a PUT/PATCH request to modify a plan with state `PENDING`
- **THEN** the plan SHALL be updated with the new values

#### Scenario: User cannot edit a HOLDING plan

- **WHEN** a user sends a PUT/PATCH request to modify a plan with state `HOLDING`
- **THEN** the system SHALL return HTTP 409 Conflict with an error message

#### Scenario: User cannot edit a CLOSED plan

- **WHEN** a user sends a PUT/PATCH request to modify a plan with state `CLOSED`
- **THEN** the system SHALL return HTTP 409 Conflict with an error message

### Requirement: Plan validity window by cycle

The plan's cycle parameter SHALL define its validity window:

- `DAILY`: Valid only on the creation date. Checked once on that day. Expires at end of day if no trigger.
- `WEEKLY`: Valid for the entire current natural week (Monday through Friday). Checked every trading day until trigger or Friday close.
- `MONTHLY`: Valid for the entire current natural month. Checked every trading day until trigger or month end.

#### Scenario: DAILY plan expires on creation day

- **WHEN** a DAILY plan is created and no trigger occurs on that day
- **THEN** at end of day the plan SHALL transition to `EXPIRED`

#### Scenario: WEEKLY plan expires if no trigger by Friday

- **WHEN** a WEEKLY plan has no trigger by Friday market close
- **THEN** the plan SHALL transition to `EXPIRED`

#### Scenario: MONTHLY plan expires if no trigger by month end

- **WHEN** a MONTHLY plan has no trigger by the last trading day of the month
- **THEN** the plan SHALL transition to `EXPIRED`

### Requirement: Plan CRUD operations

The system SHALL provide REST API endpoints for plan management:

- `POST /api/plans` — Create a new plan
- `GET /api/plans` — List all plans (with optional filters: status, cycle, stockCode)
- `GET /api/plans/{id}` — Get a single plan by ID
- `PUT /api/plans/{id}` — Update a PENDING plan
- `DELETE /api/plans/{id}` — Delete a plan (PENDING or EXPIRED only)

#### Scenario: Create a new plan

- **WHEN** user sends POST /api/plans with valid plan data including at least one PlanCondition
- **THEN** the system SHALL create the plan in `PENDING` state and return HTTP 201 with the plan entity

#### Scenario: List plans filtered by status

- **WHEN** user sends GET /api/plans?status=HOLDING
- **THEN** the system SHALL return only plans in `HOLDING` state

#### Scenario: Delete a HOLDING plan is forbidden

- **WHEN** user sends DELETE /api/plans/{id} for a plan in `HOLDING` or `CLOSED` state
- **THEN** the system SHALL return HTTP 409 Conflict
