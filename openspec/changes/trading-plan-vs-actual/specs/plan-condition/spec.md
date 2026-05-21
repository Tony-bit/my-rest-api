## ADDED Requirements

### Requirement: PlanCondition structure

A PlanCondition SHALL represent a single trigger condition for a Plan. Each condition SHALL have:

- `conditionType`: Fixed value `MA_TRIGGER` (MA触碰)
- `direction`: Either `BUY` or `SELL`
- `maPeriod`: An integer indicating the MA period (e.g., 5, 10, 20, 30, 60)
- `threshold`: A decimal percentage tolerance for determining触碰 (default: 0.3%)

A Plan MAY have one or more conditions. A Plan SHALL always have at least one condition.

#### Scenario: Plan with buy condition only

- **WHEN** a plan is created with a single PlanCondition of direction `BUY`
- **THEN** the condition SHALL be stored and linked to the plan

#### Scenario: Plan with both buy and sell conditions

- **WHEN** a plan is created with two PlanConditions (one BUY, one SELL)
- **THEN** both conditions SHALL be stored and the plan SHALL track both triggers independently

### Requirement: MA触碰 condition evaluation

A MA触碰 condition is satisfied when:

```
|close_price - MA_n| / close_price <= threshold
```

Where `MA_n` is the N-day moving average calculated from Tushare daily K-line data.

- Buy condition: Only evaluated when plan is in `PENDING` state (has_bought = false)
- Sell condition: Evaluated when plan is in `HOLDING` state

#### Scenario: Buy condition triggers when price touches MA5

- **GIVEN** a plan with `MA_TRIGGER`, `BUY`, `maPeriod=5`, `threshold=0.3%`
- **AND** the plan is in `PENDING` state
- **WHEN** Tushare data for that day satisfies `|close - MA5| / close <= 0.3%`
- **THEN** a PlanExecution record SHALL be created with `triggered=true` and `direction=BUY`
- **AND** the plan's state SHALL transition to `HOLDING`

#### Scenario: Buy condition does not fire if already holding

- **GIVEN** a plan is in `HOLDING` state
- **WHEN** a new PlanExecution with direction `BUY` would be created
- **THEN** the system SHALL skip the buy trigger (no duplicate execution)

#### Scenario: Sell condition triggers when price touches MA10

- **GIVEN** a plan with `MA_TRIGGER`, `SELL`, `maPeriod=10`, `threshold=0.3%`
- **AND** the plan is in `HOLDING` state
- **WHEN** Tushare data satisfies the触碰 condition
- **THEN** a PlanExecution record SHALL be created with `triggered=true` and `direction=SELL`
- **AND** the plan's state SHALL transition to `CLOSED`

#### Scenario: Condition does not trigger outside validity window

- **GIVEN** a WEEKLY plan whose validity window has ended (Friday market close)
- **WHEN** a condition evaluation would fire
- **THEN** no PlanExecution SHALL be created
- **AND** the plan SHALL transition to `EXPIRED`
