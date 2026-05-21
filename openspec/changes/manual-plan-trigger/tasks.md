# Implementation Tasks

## 1. Database & Entity Changes

- [ ] 1.1 Add `triggerDate` field to Plan entity (DATE, nullable, default current date)
- [ ] 1.2 Update PlanRepository with query method: `findByTriggerDateAndStatusAndNotExecuted()`
- [ ] 1.3 Add migration script or configure JPA auto-DDL for `trigger_date` column

## 2. Backend API Changes

- [ ] 2.1 Update PlanCreateDTO and PlanResponseDTO to include `triggerDate` field
- [ ] 2.2 Update PlanService.create() to accept and validate `triggerDate` (must be within 3 months)
- [ ] 2.3 Add validation: reject `triggerDate` older than 3 months with HTTP 400

## 3. Manual Trigger Service

- [ ] 3.1 Create `ManualTriggerService` with `triggerPlans(targetDate)` method
- [ ] 3.2 Implement logic: query all plans where `triggerDate == targetDate` and `status == PENDING`
- [ ] 3.3 Check `PlanExecution.executed` to skip already executed plans
- [ ] 3.4 Fetch historical K-line data from TushareService for target date
- [ ] 3.5 Evaluate trigger conditions using existing logic
- [ ] 3.6 Execute plan: create PlanExecution, update plan status to HOLDING
- [ ] 3.7 Return trigger result with details array

## 4. Manual Trigger Controller

- [ ] 4.1 Implement `POST /api/trigger` endpoint with request body `{ targetDate }`
- [ ] 4.2 Validate targetDate format (ISO date string)
- [ ] 4.3 Return structured response with total/triggered/skipped counts
- [ ] 4.4 Add error handling for invalid dates

## 5. Code Reuse (MarketCloseTask Refactor)

- [ ] 5.1 Extract trigger evaluation logic from `MarketCloseTask` into a shared service/method
- [ ] 5.2 Update `MarketCloseTask` to use the shared logic
- [ ] 5.3 Update `ManualTriggerService` to use the same shared logic

## 6. Backend Tests

- [ ] 6.1 Write unit tests for `ManualTriggerService.triggerPlans()`
- [ ] 6.2 Write unit tests for trigger date validation (within 3 months)
- [ ] 6.3 Write unit tests for idempotency protection

## 7. Frontend Type Definitions

- [ ] 7.1 Add `triggerDate` field to Plan type (`frontend/src/types/plan.ts`)
- [ ] 7.2 Add `triggerDate` to `PlanCreateDTO` interface
- [ ] 7.3 Add `TriggerResult` type for trigger response

## 8. Frontend: PlanCreate Page

- [ ] 8.1 Add date picker component for `triggerDate` field
- [ ] 8.2 Set default value to current date
- [ ] 8.3 Limit date range to last 3 months
- [ ] 8.4 Pass `triggerDate` in create plan API call

## 9. Frontend: PlanList Page

- [ ] 9.1 Display `triggerDate` in plan list table
- [ ] 9.2 Add "手动触发" button to page header
- [ ] 9.3 Add date picker for target date selection (default: today)
- [ ] 9.4 Call `POST /api/trigger` when button clicked
- [ ] 9.5 Show Toast notification with trigger result
- [ ] 9.6 Refresh plan list after trigger

## 10. Frontend: API Layer

- [ ] 10.1 Implement `api/trigger.ts` with `triggerPlans(targetDate)` function
- [ ] 10.2 Add `useManualTrigger` hook for React Query integration

---

## Task Dependencies

```
1.1 Plan entity → 2.1 DTOs → 2.3 Validation → 3.1-3.7 ManualTriggerService
                                                              ↓
                                                          4.1-4.4 Controller
                                                              ↓
                                                          5.1-5.3 Code reuse

1.1 Plan entity → 2.1 DTOs → 7.1-7.3 Types
                                      ↓
                              8.1-8.4 PlanCreate
                              9.1-9.6 PlanList
                              10.1-10.2 API Layer

6.1-6.3 Tests can be written after 3.1-3.7
```
