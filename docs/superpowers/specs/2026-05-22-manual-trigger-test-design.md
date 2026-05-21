# Test Design: Manual Trigger Service

## Context

Based on `openspec/changes/manual-plan-trigger/` specs, design unit tests for the manual trigger feature.

## Specs

- `specs/historical-plan/spec.md` — triggerDate field behavior
- `specs/manual-trigger/spec.md` — manual trigger API behavior

## Test Scope

| Test Class | Coverage | Tasks |
|------------|----------|-------|
| `ManualTriggerServiceTest` | Service logic | 6.1, 6.3 |
| `PlanServiceTriggerDateTest` | triggerDate validation | 6.2 |

## Test Cases

### ManualTriggerServiceTest

**Group 1: Normal Execution**

| # | Method | Scenario |
|---|--------|----------|
| 1 | `triggerPlans_normalExecution_triggersPlan` | Condition met, plan transitions to HOLDING |
| 2 | `triggerPlans_emptyResult_returnsZeroCounts` | No matching plans, all counts = 0 |
| 3 | `triggerPlans_multiplePlans_mixedResults` | Mixed triggered/skipped results |
| 4 | `triggerPlans_updatesPlanStatusToHolding` | Plan status updated correctly |
| 5 | `triggerPlans_createsPlanExecution` | PlanExecution record created |
| 6 | `triggerPlans_fetchesKLineForTargetDate` | TushareService called with correct date |

**Group 2: Idempotency (Task 6.3)**

| # | Method | Scenario |
|---|--------|----------|
| 7 | `triggerPlans_alreadyExecuted_skipsPlan` | Already executed plan skipped, status=already_executed |
| 8 | `triggerPlans_executedFalse_canTrigger` | Plan with executed=false can be triggered |
| 9 | `triggerPlans_duplicateTrigger_callTwice` | Second call skips already-triggered plan |

**Group 3: Data Unavailable**

| # | Method | Scenario |
|---|--------|----------|
| 10 | `triggerPlans_noKLineData_marksDataUnavailable` | Missing K-line data, status=data_unavailable |
| 11 | `triggerPlans_noKLineData_doesNotCreateExecution` | No execution created when data unavailable |

**Group 4: Condition Evaluation**

| # | Method | Scenario |
|---|--------|----------|
| 12 | `triggerPlans_conditionNotMet_skipsPlan` | Condition not met, status=condition_not_met |
| 13 | `triggerPlans_multipleConditions_allMet_triggers` | All conditions must be met to trigger |
| 14 | `triggerPlans_pendingStatusOnly_considered` | Only PENDING plans are evaluated |

### PlanServiceTriggerDateTest

| # | Method | Scenario |
|---|--------|----------|
| 15 | `create_triggerDateWithin3Months_succeeds` | Within 90 days allowed |
| 16 | `create_triggerDateToday_succeeds` | Today allowed |
| 17 | `create_triggerDateFuture_succeeds` | Future date within 3 months allowed |
| 18 | `create_triggerDateExactly3MonthsAgo_succeeds` | Exactly 3 months ago allowed |
| 19 | `create_triggerDateOlderThan3Months_throws400` | >3 months returns HTTP 400 |
| 20 | `create_triggerDateNull_defaultsToToday` | Null defaults to today |

## Test Data

### TriggerResult Structure
```java
record TriggerResult(
    LocalDate targetDate,
    int totalPlans,
    int triggered,
    int skipped,
    List<TriggerDetail> details
)

record TriggerDetail(
    Long planId,
    String stockName,
    String status  // triggered / already_executed / condition_not_met / data_unavailable
)
```

### Mock Dependencies
- `PlanRepository` — findByTriggerDateAndStatus()
- `PlanExecutionRepository` — existsByPlanIdAndExecuted()
- `TushareService` — getDailyKLine()
- `PlanExecutionService` — recordExecution()
- `SystemConfigService` — getPlanAccount()

## Implementation Notes

- Extend `TestFixtures.java` with `triggerDate` field support
- Follow existing naming convention: `methodName_scenario_expectedResult()`
- Use `@ExtendWith(MockitoExtension.class)` consistent with project style
- Test dates relative to `LocalDate.now()` for maintainability
