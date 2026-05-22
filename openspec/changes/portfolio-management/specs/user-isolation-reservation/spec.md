## ADDED Requirements

### Requirement: userId field reserved on core entities

The following entities SHALL include a userId field for future multi-user data isolation:

- Portfolio: `userId` (BIGINT, nullable)
- Plan: `userId` (BIGINT, nullable)

#### Scenario: userId field exists but is not enforced

- **WHEN** in single-user mode (no authentication)
- **THEN** all userId fields SHALL be NULL
- **AND** all queries SHALL return all records regardless of userId

### Requirement: userId ready for future authentication integration

The entities SHALL store userId such that future authentication integration can filter data by user.

#### Scenario: userId can be set during entity creation

- **WHEN** authentication is integrated
- **AND** a user creates a Portfolio or Plan
- **THEN** the system SHALL automatically populate userId from the authenticated user's context

### Requirement: ActualTrade does not require portfolioId

ActualTrade records SHALL NOT be associated with Portfolio. ActualTrade and Portfolio are independent systems.

#### Scenario: ActualTrade is independent of Portfolio

- **WHEN** a user queries ActualTrade records
- **THEN** the results SHALL NOT be filtered by portfolioId
- **AND** ActualTrade records do not have a portfolioId field

### Requirement: DailySnapshot has portfolioId for return curve

DailySnapshot records SHALL include portfolioId for the purpose of generating portfolio return curves.

#### Scenario: DailySnapshot includes portfolioId

- **WHEN** a DailySnapshot is created
- **THEN** it SHALL include portfolioId for return curve data aggregation
