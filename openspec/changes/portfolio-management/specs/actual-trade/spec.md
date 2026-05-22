## MODIFIED Requirements

### Requirement: DailySnapshot has portfolioId field

DailySnapshot records SHALL include a portfolioId field for grouping snapshot data by portfolio and generating return curves.

#### Scenario: DailySnapshot includes portfolioId

- **WHEN** a DailySnapshot is created
- **THEN** it SHALL include portfolioId (populated from the associated Plan's portfolioId)

### Requirement: DailySnapshot data migration

The system SHALL migrate existing DailySnapshot records to populate their portfolioId field.

#### Scenario: Migrate historical DailySnapshot records

- **WHEN** MigrationService runs on application startup
- **THEN** all existing DailySnapshot records SHALL have their portfolioId populated by:
  - Finding the associated Plan
  - Setting portfolioId to the Plan's portfolioId
