## 1. Database Migration

- [ ] 1.1 Create Flyway migration script `V3__portfolio.sql`
  - Create `portfolio` table (id, name, description, style, user_id, created_at)
  - Add `portfolio_id` column to `plan` table with foreign key constraint
  - Add `user_id` column to `plan` table
  - Add `user_id` column to `portfolio` table
  - Add `portfolio_id` column to `daily_snapshot` table
  - Insert default portfolio "预案组合A" (id=1)

- [ ] 1.2 Verify migration executes successfully in dev environment

## 2. Backend - Portfolio Entity & Repository

- [ ] 2.1 Create `Portfolio` entity class
  - Fields: id, name, description, style, userId, createdAt
  - JPA annotations for table mapping
  - Index on name for list queries

- [ ] 2.2 Create `PortfolioRepository` interface
  - Extend JpaRepository<Portfolio, Long>
  - Add `findByUserIdIsNull()` for single-user mode
  - Add `existsByName()` for duplicate name validation

## 3. Backend - Portfolio Service

- [ ] 3.1 Create `PortfolioService` class
  - `create()` - create new portfolio
  - `list()` - return all portfolios with computed statistics
  - `getById()` - return portfolio with plans
  - `update()` - update portfolio metadata
  - `delete()` - delete portfolio (only if no plans)

- [ ] 3.2 Add portfolio statistics computation
  - Count plans by portfolioId
  - Count plans by portfolioId and status (HOLDING, CLOSED)
  - Calculate win rate from CLOSED plans only

- [ ] 3.3 Create `getOverview()` method
  - Return portfolio details, summary stats, and return curve data
  - Accept `range` parameter: `7d`, `30d`, `all`
  - Aggregate DailySnapshot data by portfolioId and date

## 4. Backend - Portfolio Controller

- [ ] 4.1 Create `PortfolioController` class
  - `GET /api/portfolios` - list all portfolios with stats
  - `POST /api/portfolios` - create new portfolio
  - `GET /api/portfolios/{id}` - get portfolio detail with plans
  - `PUT /api/portfolios/{id}` - update portfolio
  - `DELETE /api/portfolios/{id}` - delete portfolio (409 if has plans)
  - `GET /api/portfolios/{id}/overview` - get portfolio overview with return curve

## 5. Backend - Plan Entity Update

- [ ] 5.1 Update `Plan` entity
  - Add `portfolioId` field with `@Column(name = "portfolio_id")`
  - Add `@ManyToOne` relationship to Portfolio (nullable=false)
  - Add getter/setter

- [ ] 5.2 Update `PlanRepository`
  - Add `findByPortfolioId()` method
  - Add `countByPortfolioId()` method
  - Add `countByPortfolioIdAndStatus()` method
  - Add `findByPortfolioIdAndStatus()` method

- [ ] 5.3 Update `PlanDTO`
  - Add `portfolioId` field to CreateRequest (required)
  - Add `portfolioId` field to Response classes
  - Add validation: `@NotNull(message = "portfolioId is required")`

## 6. Backend - Plan Service Update

- [ ] 6.1 Update `PlanService.list()` method
  - Accept optional `portfolioId` parameter
  - Filter plans by portfolioId when provided

- [ ] 6.2 Update `PlanService.create()` method
  - Validate portfolioId is provided
  - Set portfolio reference when creating plan

- [ ] 6.3 Update `PlanService.update()` method
  - Ignore portfolioId changes (portfolio is immutable after creation)

- [ ] 6.4 Update `PlanService.createSellPlan()` method
  - Automatically inherit `portfolioId` from the associated BUY plan
  - Remove portfolioId from SELL plan request DTO (not user-settable)

## 7. Backend - DailySnapshot Entity Update

- [ ] 7.1 Update `DailySnapshot` entity
  - Add `portfolioId` field with `@Column(name = "portfolio_id")`
  - Add `@ManyToOne` relationship to Portfolio

- [ ] 7.2 Update `DailySnapshotRepository`
  - Add `findByPortfolioIdAndSnapshotDateBetween()` method for return curve

## 8. Backend - Data Migration (MigrationService)

- [ ] 8.1 Create `MigrationService` for startup data migration
  - `@PostConstruct` annotated method for automatic execution
  - Track migration status to avoid duplicate runs (use SystemConfig or file-based flag)

- [ ] 8.2 Implement migration steps
  - Ensure default portfolio "预案组合A" exists (id=1)
  - Migrate all existing plans to default portfolio: `portfolio_id = 1`
  - Populate DailySnapshot portfolioId by joining with Plan
  - Set userId = null for all migrated records

- [ ] 8.3 Verify migration completes without errors
  - Log migration progress and results
  - Handle edge cases (already migrated, partial migration)

## 9. Frontend - Portfolio Types & API

- [ ] 9.1 Add Portfolio types
  - `Portfolio`, `PortfolioListItem`, `PortfolioForm`
  - `PortfolioSummary` (planCount, holdingCount, totalReturn, totalProfit, totalLoss, profitCount, lossCount, winRate)
  - `ReturnCurvePoint` (date, value)

- [ ] 9.2 Add Portfolio API methods
  - `getPortfolios()`, `getPortfolio(id)`, `getPortfolioOverview(id, range)`
  - `createPortfolio()`, `updatePortfolio()`, `deletePortfolio()`

- [ ] 9.3 Update Plan types
  - Add `portfolioId` to plan types

- [ ] 9.4 Update Plan API
  - Add `portfolioId` parameter to list method

## 10. Frontend - Portfolio Pages

- [ ] 10.1 Create `PortfolioList` page component
  - List all portfolios with statistics cards
  - Display: name, description, style, planCount, totalReturn, winRate
  - Buttons: Create, Edit, View Details

- [ ] 10.2 Create `PortfolioCreate` page component
  - Form fields: name (required), description, style
  - Validation and error handling

- [ ] 10.3 Create `PortfolioDetail` page component
  - Display portfolio metadata
  - Display summary statistics (totalReturn, totalProfit, totalLoss, winRate, planCount)
  - Display return curve chart (with 7d/30d/all range selector)
  - Display plan list (all statuses)

- [ ] 10.4 Create `PortfolioEdit` page component
  - Edit name, description, style
  - Display statistics (read-only)

- [ ] 10.5 Add routes for portfolio pages
  - `/portfolios` - list
  - `/portfolios/new` - create
  - `/portfolios/:id` - detail (with overview)
  - `/portfolios/:id/edit` - edit

## 11. Frontend - Plan Pages Update

- [ ] 11.1 Update `PlanList` page
  - Add portfolio filter dropdown
  - Display portfolio name column
  - Default to show all portfolios

- [ ] 11.2 Update `PlanCreate` page
  - Add portfolio selector dropdown (required)
  - Show available portfolios from API
  - Hide portfolio selector when creating SELL plan

- [ ] 11.3 Update `PlanEdit` page
  - Display portfolio name (read-only)
  - Cannot change portfolio association

- [ ] 11.4 Add "按组合筛选" filter to PlanList
  - Filter actual trades list by associated plan's portfolio
  - Note: ActualTrade does not have portfolioId, filter via Plan relationship

## 12. Frontend - Return Curve Chart Component

- [ ] 12.1 Create `ReturnCurveChart` component
  - Line chart showing portfolio return over time
  - X-axis: dates, Y-axis: return percentage
  - Support different time ranges (7d, 30d, all)
  - Interactive tooltip showing date and value

- [ ] 12.2 Integrate chart in `PortfolioDetail` page
  - Time range selector buttons
  - Fetch data from `/api/portfolios/{id}/overview?range=xxx`

## 13. Frontend - App Layout & Navigation

- [ ] 13.1 Add "预案组合" link to navigation menu
  - Link to `/portfolios`

- [ ] 13.2 Add "按组合筛选" dropdown in Plan management section
  - Quick filter for plans by portfolio
  - Available in PlanList page

## 14. Testing

### 14.1 PortfolioService Unit Tests

#### 14.1.1 Portfolio CRUD Operations

| Test ID | TC-PORTFOLIO-001 | Priority | P0 |
|---------|------------------|----------|-----|
| **Scenario** | Create valid Portfolio | | |
| **Given** | No existing portfolio | | |
| **When** | `POST /api/portfolios` with name="趋势策略", description="趋势跟踪", style="TREND" | | |
| **Then** | HTTP 201, portfolio created with id and createdAt | | |

| Test ID | TC-PORTFOLIO-002 | Priority | P0 |
|---------|------------------|----------|-----|
| **Scenario** | Create Portfolio without description | | |
| **Given** | No existing portfolio | | |
| **When** | `POST /api/portfolios` with name="短线策略", style="SWING" | | |
| **Then** | HTTP 201, description is null | | |

| Test ID | TC-PORTFOLIO-003 | Priority | P0 |
|---------|------------------|----------|-----|
| **Scenario** | Create Portfolio with empty name | | |
| **When** | `POST /api/portfolios` with name="" | | |
| **Then** | HTTP 400, error: "name is required" | | |

| Test ID | TC-PORTFOLIO-004 | Priority | P1 |
|---------|------------------|----------|-----|
| **Scenario** | Create Portfolio with name exceeding 100 chars | | |
| **When** | `POST /api/portfolios` with name=101+ chars | | |
| **Then** | HTTP 400, validation error | | |

| Test ID | TC-PORTFOLIO-005 | Priority | P1 |
|---------|------------------|----------|-----|
| **Scenario** | Create Portfolio with duplicate name | | |
| **Given** | Portfolio "测试组合" exists | | |
| **When** | `POST /api/portfolios` with name="测试组合" | | |
| **Then** | HTTP 409, error: "portfolio name already exists" | | |

| Test ID | TC-PORTFOLIO-006 | Priority | P0 |
|---------|------------------|----------|-----|
| **Scenario** | List all portfolios | | |
| **Given** | Portfolios A, B, C exist | | |
| **When** | `GET /api/portfolios` | | |
| **Then** | Return all 3 portfolios with statistics | | |

| Test ID | TC-PORTFOLIO-007 | Priority | P0 |
|---------|------------------|----------|-----|
| **Scenario** | Get single portfolio by ID | | |
| **Given** | Portfolio id=1 exists | | |
| **When** | `GET /api/portfolios/1` | | |
| **Then** | Return portfolio with associated plans | | |

| Test ID | TC-PORTFOLIO-008 | Priority | P0 |
|---------|------------------|----------|-----|
| **Scenario** | Get non-existent portfolio | | |
| **When** | `GET /api/portfolios/999` | | |
| **Then** | HTTP 404, error: "portfolio not found" | | |

| Test ID | TC-PORTFOLIO-009 | Priority | P0 |
|---------|------------------|----------|-----|
| **Scenario** | Update portfolio metadata | | |
| **Given** | Portfolio id=1 exists | | |
| **When** | `PUT /api/portfolios/1` with name="新名称" | | |
| **Then** | HTTP 200, name updated | | |

| Test ID | TC-PORTFOLIO-010 | Priority | P0 |
|---------|------------------|----------|-----|
| **Scenario** | Delete empty portfolio | | |
| **Given** | Portfolio id=2 has no plans | | |
| **When** | `DELETE /api/portfolios/2` | | |
| **Then** | HTTP 204, portfolio deleted | | |

| Test ID | TC-PORTFOLIO-011 | Priority | P0 |
|---------|------------------|----------|-----|
| **Scenario** | Delete portfolio with plans - forbidden | | |
| **Given** | Portfolio id=1 has 2 associated plans | | |
| **When** | `DELETE /api/portfolios/1` | | |
| **Then** | HTTP 409, error: "portfolio has associated plans" | | |

#### 14.1.2 Portfolio Statistics Computation

| Test ID | TC-STATS-001 | Priority | P0 |
|---------|--------------|----------|-----|
| **Scenario** | Calculate total return | | |
| **Given** | Portfolio with 3 plans: marketValue=11500, cashBalance=500, baselineCapital=10000 | | |
| **When** | `GET /api/portfolios` | | |
| **Then** | totalReturn = (11500 + 500 - 10000) / 10000 × 100% = 20% | | |

| Test ID | TC-STATS-002 | Priority | P0 |
|---------|--------------|----------|-----|
| **Scenario** | Calculate profit and loss | | |
| **Given** | Portfolio with: 2 profitable closed plans (+1000, +500), 1 losing closed plan (-300) | | |
| **When** | `GET /api/portfolios` | | |
| **Then** | totalProfit=1500, totalLoss=-300, profitCount=2, lossCount=1 | | |

| Test ID | TC-STATS-003 | Priority | P0 |
|---------|--------------|----------|-----|
| **Scenario** | Calculate win rate | | |
| **Given** | Portfolio with: 3 profitable, 1 losing closed plans | | |
| **When** | `GET /api/portfolios` | | |
| **Then** | winRate = 3 / (3 + 1) × 100% = 75% | | |

| Test ID | TC-STATS-004 | Priority | P0 |
|---------|--------------|----------|-----|
| **Scenario** | Profit/loss only includes CLOSED plans | | |
| **Given** | Portfolio with: PENDING plan (+1000), HOLDING plan (+500), CLOSED profit (+300), CLOSED loss (-100) | | |
| **When** | `GET /api/portfolios` | | |
| **Then** | totalProfit=300, totalLoss=-100 (PENDING/HOLDING excluded) | | |

| Test ID | TC-STATS-005 | Priority | P0 |
|---------|--------------|----------|-----|
| **Scenario** | planCount includes all statuses | | |
| **Given** | Portfolio with: 2 PENDING, 3 HOLDING, 5 CLOSED, 1 EXPIRED | | |
| **When** | `GET /api/portfolios` | | |
| **Then** | planCount = 11 | | |

| Test ID | TC-STATS-006 | Priority | P1 |
|---------|--------------|----------|-----|
| **Scenario** | Empty portfolio statistics | | |
| **Given** | Portfolio with no plans | | |
| **When** | `GET /api/portfolios` | | |
| **Then** | totalReturn=0, totalProfit=0, totalLoss=0, winRate=0%, planCount=0, holdingCount=0 | | |

| Test ID | TC-STATS-007 | Priority | P1 |
|---------|--------------|----------|-----|
| **Scenario** | All winning portfolio | | |
| **Given** | Portfolio with 5 profitable closed plans | | |
| **When** | `GET /api/portfolios` | | |
| **Then** | winRate=100%, lossCount=0 | | |

| Test ID | TC-STATS-008 | Priority | P1 |
|---------|--------------|----------|-----|
| **Scenario** | All losing portfolio | | |
| **Given** | Portfolio with 3 losing closed plans | | |
| **When** | `GET /api/portfolios` | | |
| **Then** | winRate=0%, profitCount=0 | | |

#### 14.1.3 Portfolio Overview & Return Curve

| Test ID | TC-OVERVIEW-001 | Priority | P0 |
|---------|-----------------|----------|-----|
| **Scenario** | Get portfolio overview | | |
| **Given** | Portfolio has DailySnapshots | | |
| **When** | `GET /api/portfolios/1/overview` | | |
| **Then** | Return { details, summary, returnCurve } | | |

| Test ID | TC-OVERVIEW-002 | Priority | P0 |
|---------|-----------------|----------|-----|
| **Scenario** | Return curve with 7d range | | |
| **Given** | Portfolio has 30 days of snapshots | | |
| **When** | `GET /api/portfolios/1/overview?range=7d` | | |
| **Then** | Return only last 7 days of data points | | |

| Test ID | TC-OVERVIEW-003 | Priority | P0 |
|---------|-----------------|----------|-----|
| **Scenario** | Return curve with 30d range | | |
| **Given** | Portfolio has 60 days of snapshots | | |
| **When** | `GET /api/portfolios/1/overview?range=30d` | | |
| **Then** | Return last 30 days of data points | | |

| Test ID | TC-OVERVIEW-004 | Priority | P0 |
|---------|-----------------|----------|-----|
| **Scenario** | Return curve with all range | | |
| **When** | `GET /api/portfolios/1/overview?range=all` | | |
| **Then** | Return all historical data points | | |

| Test ID | TC-OVERVIEW-005 | Priority | P1 |
|---------|-----------------|----------|-----|
| **Scenario** | Invalid range parameter | | |
| **When** | `GET /api/portfolios/1/overview?range=invalid` | | |
| **Then** | HTTP 400, error: "invalid range parameter" | | |

| Test ID | TC-OVERVIEW-006 | Priority | P1 |
|---------|-----------------|----------|-----|
| **Scenario** | Return curve data sorted by date | | |
| **Given** | Portfolio has DailySnapshots | | |
| **When** | `GET /api/portfolios/1/overview` | | |
| **Then** | Data points sorted ascending by date | | |

### 14.2 PlanService Unit Tests

| Test ID | TC-PLAN-001 | Priority | P0 |
|---------|-------------|----------|-----|
| **Scenario** | Create plan with portfolioId | | |
| **Given** | Portfolio id=1 exists | | |
| **When** | `POST /api/plans` with portfolioId=1 and valid plan data | | |
| **Then** | HTTP 201, plan created with portfolioId=1 | | |

| Test ID | TC-PLAN-002 | Priority | P0 |
|---------|-------------|----------|-----|
| **Scenario** | Create plan without portfolioId - fail | | |
| **When** | `POST /api/plans` without portfolioId | | |
| **Then** | HTTP 400, error: "portfolioId is required" | | |

| Test ID | TC-PLAN-003 | Priority | P0 |
|---------|-------------|----------|-----|
| **Scenario** | Create plan with invalid portfolioId | | |
| **When** | `POST /api/plans` with portfolioId=999 | | |
| **Then** | HTTP 400, error: "portfolio not found" | | |

| Test ID | TC-PLAN-004 | Priority | P0 |
|---------|-------------|----------|-----|
| **Scenario** | List plans filtered by portfolio | | |
| **Given** | Portfolio 1 has 2 plans, Portfolio 2 has 3 plans | | |
| **When** | `GET /api/plans?portfolioId=1` | | |
| **Then** | Return only 2 plans from Portfolio 1 | | |

| Test ID | TC-PLAN-005 | Priority | P0 |
|---------|-------------|----------|-----|
| **Scenario** | List plans without portfolio filter | | |
| **Given** | Multiple portfolios with plans | | |
| **When** | `GET /api/plans` | | |
| **Then** | Return all plans from all portfolios | | |

| Test ID | TC-PLAN-006 | Priority | P0 |
|---------|-------------|----------|-----|
| **Scenario** | PortfolioId immutable after creation | | |
| **Given** | Plan belongs to Portfolio 1 | | |
| **When** | `PUT /api/plans/{id}` with portfolioId=2 | | |
| **Then** | HTTP 200, portfolioId remains 1 | | |

| Test ID | TC-PLAN-007 | Priority | P0 |
|---------|-------------|----------|-----|
| **Scenario** | Delete PENDING plan | | |
| **Given** | Plan status is PENDING | | |
| **When** | `DELETE /api/plans/{id}` | | |
| **Then** | HTTP 204, plan deleted | | |

| Test ID | TC-PLAN-008 | Priority | P0 |
|---------|-------------|----------|-----|
| **Scenario** | Delete HOLDING plan - forbidden | | |
| **Given** | Plan status is HOLDING | | |
| **When** | `DELETE /api/plans/{id}` | | |
| **Then** | HTTP 409, error: "cannot delete HOLDING plan" | | |

| Test ID | TC-PLAN-009 | Priority | P0 |
|---------|-------------|----------|-----|
| **Scenario** | Delete CLOSED plan - forbidden | | |
| **Given** | Plan status is CLOSED | | |
| **When** | `DELETE /api/plans/{id}` | | |
| **Then** | HTTP 409, error: "cannot delete CLOSED plan" | | |

### 14.3 SELL Plan Portfolio Inheritance Tests

| Test ID | TC-SELL-001 | Priority | P0 |
|---------|-------------|----------|-----|
| **Scenario** | SELL plan inherits portfolioId | | |
| **Given** | BUY plan belongs to Portfolio 2 | | |
| **When** | `POST /api/plans/sell` with buyPlanId | | |
| **Then** | SELL plan portfolioId = 2 (inherited) | | |

| Test ID | TC-SELL-002 | Priority | P0 |
|---------|-------------|----------|-----|
| **Scenario** | SELL and BUY plan in same portfolio | | |
| **Given** | BUY plan in Portfolio 1 | | |
| **When** | `POST /api/plans/sell` with buyPlanId | | |
| **Then** | Both plans portfolioId = 1 | | |

| Test ID | TC-SELL-003 | Priority | P1 |
|---------|-------------|----------|-----|
| **Scenario** | SELL plan DTO has no portfolioId field | | |
| **When** | Check SellPlanRequest DTO | | |
| **Then** | portfolioId field does not exist | | |

### 14.4 MigrationService Tests

| Test ID | TC-MIGRATE-001 | Priority | P0 |
|---------|---------------|----------|-----|
| **Scenario** | Migration is idempotent | | |
| **Given** | Migration already completed | | |
| **When** | Restart application, MigrationService runs again | | |
| **Then** | No errors, no duplicate operations | | |

| Test ID | TC-MIGRATE-002 | Priority | P0 |
|---------|---------------|----------|-----|
| **Scenario** | Existing plans migrated to default portfolio | | |
| **Given** | 5 existing plans without portfolioId | | |
| **When** | MigrationService executes | | |
| **Then** | All 5 plans have portfolio_id = 1 | | |

| Test ID | TC-MIGRATE-003 | Priority | P0 |
|---------|---------------|----------|-----|
| **Scenario** | DailySnapshot portfolioId populated | | |
| **Given** | DailySnapshots without portfolioId, linked to Plans | | |
| **When** | MigrationService executes | | |
| **Then** | Each snapshot has portfolioId matching its Plan | | |

| Test ID | TC-MIGRATE-004 | Priority | P1 |
|---------|---------------|----------|-----|
| **Scenario** | Default portfolio created once | | |
| **Given** | Portfolio "预案组合A" already exists | | |
| **When** | MigrationService executes | | |
| **Then** | No duplicate portfolio created | | |

| Test ID | TC-MIGRATE-005 | Priority | P1 |
|---------|---------------|----------|-----|
| **Scenario** | Migrated records have userId = null | | |
| **Given** | Migration complete | | |
| **When** | Query migrated records | | |
| **Then** | userId is null for all records | | |

### 14.5 userId Reservation Tests

| Test ID | TC-USER-001 | Priority | P0 |
|---------|------------|----------|-----|
| **Scenario** | New portfolio userId is null | | |
| **When** | `POST /api/portfolios` | | |
| **Then** | userId = null | | |

| Test ID | TC-USER-002 | Priority | P0 |
|---------|------------|----------|-----|
| **Scenario** | New plan userId is null | | |
| **When** | `POST /api/plans` | | |
| **Then** | userId = null | | |

| Test ID | TC-USER-003 | Priority | P1 |
|---------|------------|----------|-----|
| **Scenario** | Queries return all records regardless of userId | | |
| **Given** | Multiple records with null userId | | |
| **When** | `GET /api/portfolios` | | |
| **Then** | Return all records | | |

| Test ID | TC-USER-004 | Priority | P1 |
|---------|------------|----------|-----|
| **Scenario** | ActualTrade not filtered by portfolio | | |
| **Given** | Portfolio exists | | |
| **When** | Query ActualTrade records | | |
| **Then** | Results not filtered by portfolioId | | |

### 14.6 End-to-End Workflow Tests

| Test ID | TC-E2E-001 | Priority | P0 |
|---------|------------|----------|-----|
| **Scenario** | Complete Portfolio lifecycle | | |
| **Steps** | | | |
| | 1. Create Portfolio "趋势策略" | | |
| | 2. Create Plan A → Portfolio "趋势策略" | | |
| | 3. Create Plan B → Portfolio "趋势策略" | | |
| | 4. Get Portfolio Overview | | |
| | 5. Verify return calculation | | |
| **Expected** | All steps succeed, statistics calculated correctly | | |

| Test ID | TC-E2E-002 | Priority | P0 |
|---------|------------|----------|-----|
| **Scenario** | BUY/SELL plan same portfolio | | |
| **Steps** | | | |
| | 1. Create Portfolio "短线策略" | | |
| | 2. Create BUY Plan → Portfolio | | |
| | 3. Create SELL Plan from BUY | | |
| | 4. Verify both plans in same portfolio | | |
| **Expected** | SELL inherits portfolioId, both in same portfolio | | |

| Test ID | TC-E2E-003 | Priority | P1 |
|---------|------------|----------|-----|
| **Scenario** | Delete portfolio with plans - protected | | |
| **Steps** | | | |
| | 1. Create Portfolio | | |
| | 2. Create Plan → Portfolio | | |
| | 3. Attempt DELETE Portfolio | | |
| **Expected** | HTTP 409, portfolio preserved | | |

### 14.7 Run All Tests

- [ ] 14.7.1 Run PortfolioService unit tests - verify all pass
- [ ] 14.7.2 Run PlanService tests - verify all pass
- [ ] 14.7.3 Run MigrationService tests - verify all pass
- [ ] 14.7.4 Run integration tests - verify all pass
- [ ] 14.7.5 Generate test coverage report (target: >80%)

## 15. Integration Testing

- [ ] 15.1 End-to-end workflow test
  - Create portfolio → Create plan → View portfolio overview → Verify return calculation

- [ ] 15.2 Migration verification test
  - Verify existing plans are migrated to default portfolio
  - Verify DailySnapshot records have portfolioId populated
