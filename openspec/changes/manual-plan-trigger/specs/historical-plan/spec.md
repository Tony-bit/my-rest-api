# Spec: Historical Plan

## ADDED Requirements

### Requirement: Plan triggerDate field

The system SHALL support a `triggerDate` field on Plan entity that specifies when the plan should be triggered.

#### Scenario: Create plan with default trigger date
- **WHEN** user creates a plan without specifying `triggerDate`
- **THEN** system SHALL set `triggerDate` to the current date

#### Scenario: Create plan with historical trigger date
- **WHEN** user creates a plan with `triggerDate` within the last 3 months
- **THEN** system SHALL accept the historical date and store it

#### Scenario: Create plan with future trigger date
- **WHEN** user creates a plan with `triggerDate` in the future
- **THEN** system SHALL accept the future date and store it

### Requirement: Trigger date validation

The system SHALL validate that `triggerDate` is within 3 months from today.

#### Scenario: Date too old
- **WHEN** user creates a plan with `triggerDate` older than 3 months
- **THEN** system SHALL return HTTP 400 with error message "triggerDate cannot be older than 3 months"

#### Scenario: Invalid date format
- **WHEN** user creates a plan with invalid `triggerDate` format
- **THEN** system SHALL return HTTP 400 with validation error

### Requirement: Query plans by trigger date

The system SHALL support filtering plans by `triggerDate` in the list endpoint.

#### Scenario: List plans with specific trigger date
- **WHEN** user requests `GET /api/plans?triggerDate=2026-04-22`
- **THEN** system SHALL return only plans where `triggerDate == "2026-04-22"`

### Requirement: Trigger date display

The system SHALL display `triggerDate` in plan list and detail views.

#### Scenario: Display trigger date in list
- **WHEN** user views the plan list
- **THEN** each plan SHALL show its `triggerDate`

#### Scenario: Display trigger date in detail
- **WHEN** user views plan detail
- **THEN** the detail SHALL show `triggerDate` prominently
