# Frontend Real API Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace all remaining frontend mock-backed behavior with real backend APIs while preserving the current page layout and interaction model.

**Architecture:** Converge backend DTOs and frontend shared types onto one transport contract, add a dedicated dashboard aggregation endpoint, then switch each route from mock-era assumptions to direct real API usage. Prefer resource endpoints for CRUD pages and a dedicated query endpoint for the dashboard composite page.

**Tech Stack:** Spring Boot 3, Hibernate/JPA, React, TypeScript, React Query, Axios, ECharts, Vite

---

## File Structure

### Backend files to modify

- `src/main/java/com/example/myapi/dto/PlanDTO.java`
- `src/main/java/com/example/myapi/dto/ActualTradeDTO.java`
- `src/main/java/com/example/myapi/dto/ViewDTO.java`
- `src/main/java/com/example/myapi/dto/SnapshotDTO.java`
- `src/main/java/com/example/myapi/controller/PlanController.java`
- `src/main/java/com/example/myapi/controller/ActualTradeController.java`
- `src/main/java/com/example/myapi/controller/SnapshotController.java`
- `src/main/java/com/example/myapi/service/HoldingsService.java`
- `src/main/java/com/example/myapi/service/PeriodSummaryService.java`

### Backend files to create

- `src/main/java/com/example/myapi/dto/DashboardDTO.java`
- `src/main/java/com/example/myapi/controller/DashboardController.java`
- `src/main/java/com/example/myapi/service/DashboardService.java`
- `src/test/java/com/example/myapi/service/DashboardServiceTest.java`

### Frontend files to modify

- `frontend/src/types/plan.ts`
- `frontend/src/types/actualTrade.ts`
- `frontend/src/types/view.ts`
- `frontend/src/api/plan.ts`
- `frontend/src/api/actualTrade.ts`
- `frontend/src/api/views.ts`
- `frontend/src/hooks/useActualTrades.ts`
- `frontend/src/hooks/useViews.ts`
- `frontend/src/components/views/TrendChart.tsx`
- `frontend/src/components/views/BaselineCapitalInput.tsx`
- `frontend/src/pages/Dashboard.tsx`
- `frontend/src/pages/PlanDetail.tsx`
- `frontend/src/pages/PlanEdit.tsx`
- `frontend/src/pages/TradeEdit.tsx`
- `frontend/src/pages/SnapshotList.tsx`
- `frontend/src/pages/Settings.tsx`

### Frontend files to create

- `frontend/src/types/dashboard.ts`
- `frontend/src/api/dashboard.ts`
- `frontend/src/hooks/useDashboard.ts`

### Cleanup files

- `frontend/src/mock/data.ts`
- `frontend/src/api/index.ts`
- `frontend/src/hooks/index.ts`

---

### Task 1: Unify backend DTO contracts for plans, trades, snapshots, and holdings

**Files:**
- Modify: `src/main/java/com/example/myapi/dto/PlanDTO.java`
- Modify: `src/main/java/com/example/myapi/dto/ActualTradeDTO.java`
- Modify: `src/main/java/com/example/myapi/dto/ViewDTO.java`
- Modify: `src/main/java/com/example/myapi/dto/SnapshotDTO.java`
- Modify: `src/main/java/com/example/myapi/controller/SnapshotController.java`
- Test: `src/test/java/com/example/myapi/service/ActualTradeServiceTest.java`
- Test: `src/test/java/com/example/myapi/service/HoldingsServiceTest.java`

- [ ] **Step 1: Write failing backend contract assertions for renamed fields**

```java
@Test
void listTrades_shouldExposeMatchedAndProfitLossAmountFields() {
    ActualTradeDTO.Response response = actualTradeService.getById(1L);

    assertThat(response.getProfitLossAmount()).isNotNull();
    assertThat(response.getMatched()).isNotNull();
}
```

```java
@Test
void holdings_shouldExposePlanNameAndQuantityFields() {
    ViewDTO.HoldingsResponse response = holdingsService.getHoldings(false);

    ViewDTO.PlanHoldingDTO holding = response.getPlanHoldings().getFirst();
    assertThat(holding.getPlanName()).isNotBlank();
    assertThat(holding.getQuantity()).isNotNull();
}
```

- [ ] **Step 2: Run the focused backend tests to verify contract failures**

Run:

```bash
mvn -Dtest=ActualTradeServiceTest,HoldingsServiceTest test
```

Expected:

- compile failure or assertion failure on renamed fields such as `getProfitLossAmount`, `getMatched`, `getPlanName`, or `getQuantity`

- [ ] **Step 3: Rename DTO fields to match the final frontend contract**

```java
// ActualTradeDTO.Response
private BigDecimal profitLossAmount;
private BigDecimal profitLossPercent;
private Boolean matched;
private Long matchedBuyId;
```

```java
// ViewDTO.PlanHoldingDTO
private String planName;
private BigDecimal quantity;
private BigDecimal costPrice;
private BigDecimal currentPrice;
private BigDecimal unrealizedPLAmount;
private BigDecimal unrealizedPLPercent;
```

```java
// ViewDTO.ActualHoldingDTO
private BigDecimal quantity;
private BigDecimal avgCostPrice;
private BigDecimal unrealizedPLAmount;
private BigDecimal unrealizedPLPercent;
```

```java
// SnapshotDTO.Response
private BigDecimal avgCostPrice;
private BigDecimal planReturnPercent;
private BigDecimal actualReturnPercent;
```

- [ ] **Step 4: Align snapshot controller request and mapping names**

```java
@GetMapping
public ApiResponse<List<SnapshotDTO.Response>> list(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam(required = false) Long planId) {
```

```java
.avgCostPrice(s.getAvgCostBasis())
.planReturnPercent(s.getPlanReturnPercent())
.actualReturnPercent(s.getActualReturnPercent())
```

- [ ] **Step 5: Re-run focused backend tests**

Run:

```bash
mvn -Dtest=ActualTradeServiceTest,HoldingsServiceTest test
```

Expected:

- PASS

- [ ] **Step 6: Commit DTO contract alignment**

```bash
git add src/main/java/com/example/myapi/dto/PlanDTO.java src/main/java/com/example/myapi/dto/ActualTradeDTO.java src/main/java/com/example/myapi/dto/ViewDTO.java src/main/java/com/example/myapi/dto/SnapshotDTO.java src/main/java/com/example/myapi/controller/SnapshotController.java src/test/java/com/example/myapi/service/ActualTradeServiceTest.java src/test/java/com/example/myapi/service/HoldingsServiceTest.java
git commit -m "refactor: unify api dto contracts for frontend"
```

### Task 2: Add a dedicated dashboard aggregation endpoint

**Files:**
- Create: `src/main/java/com/example/myapi/dto/DashboardDTO.java`
- Create: `src/main/java/com/example/myapi/service/DashboardService.java`
- Create: `src/main/java/com/example/myapi/controller/DashboardController.java`
- Modify: `src/main/java/com/example/myapi/service/HoldingsService.java`
- Modify: `src/main/java/com/example/myapi/service/PeriodSummaryService.java`
- Test: `src/test/java/com/example/myapi/service/DashboardServiceTest.java`

- [ ] **Step 1: Write a failing dashboard service test**

```java
@Test
void getDashboard_shouldReturnTrendKpisHoldingsAndExecutionLog() {
    DashboardDTO.Response response = dashboardService.getDashboard();

    assertThat(response.getKpis()).isNotNull();
    assertThat(response.getTrend()).isNotEmpty();
    assertThat(response.getPlanHoldings()).isNotNull();
    assertThat(response.getActualHoldings()).isNotNull();
    assertThat(response.getExecutionLog()).isNotNull();
}
```

- [ ] **Step 2: Run the dashboard service test to verify it fails**

Run:

```bash
mvn -Dtest=DashboardServiceTest test
```

Expected:

- FAIL because `DashboardDTO` or `DashboardService` does not exist yet

- [ ] **Step 3: Create the dashboard DTO and service skeleton**

```java
public class DashboardDTO {
    @Data
    @Builder
    public static class Response {
        private BigDecimal baselineCapital;
        private Kpis kpis;
        private List<TrendPoint> trend;
        private List<PlanHoldingItem> planHoldings;
        private List<ActualHoldingItem> actualHoldings;
        private List<ExecutionLogItem> executionLog;
    }
}
```

```java
@Service
@RequiredArgsConstructor
public class DashboardService {
    private final HoldingsService holdingsService;
    private final DailySnapshotRepository snapshotRepository;
    private final PlanExecutionRepository planExecutionRepository;
    private final ActualTradeRepository actualTradeRepository;

    public DashboardDTO.Response getDashboard() {
        // aggregate holdings, cumulative trend, and recent execution timeline
    }
}
```

- [ ] **Step 4: Expose the endpoint from a controller**

```java
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService dashboardService;

    @GetMapping
    public ApiResponse<DashboardDTO.Response> getDashboard() {
        return ApiResponse.ok(dashboardService.getDashboard());
    }
}
```

- [ ] **Step 5: Implement the minimal aggregation logic to satisfy the test**

```java
ViewDTO.HoldingsResponse holdings = holdingsService.getHoldings(false);
List<DailySnapshot> snapshots = snapshotRepository.findAll();

Map<LocalDate, List<DailySnapshot>> byDate = snapshots.stream()
        .collect(Collectors.groupingBy(DailySnapshot::getSnapshotDate, TreeMap::new, Collectors.toList()));

List<DashboardDTO.TrendPoint> trend = buildTrend(byDate);
List<DashboardDTO.ExecutionLogItem> executionLog = buildExecutionLog();

return DashboardDTO.Response.builder()
        .baselineCapital(holdings.getBaselineCapital())
        .kpis(toKpis(holdings.getSummary()))
        .trend(trend)
        .planHoldings(toPlanHoldingItems(holdings.getPlanHoldings()))
        .actualHoldings(toActualHoldingItems(holdings.getActualHoldings()))
        .executionLog(executionLog)
        .build();
```

- [ ] **Step 6: Run the dashboard service test again**

Run:

```bash
mvn -Dtest=DashboardServiceTest test
```

Expected:

- PASS

- [ ] **Step 7: Commit dashboard endpoint support**

```bash
git add src/main/java/com/example/myapi/dto/DashboardDTO.java src/main/java/com/example/myapi/service/DashboardService.java src/main/java/com/example/myapi/controller/DashboardController.java src/test/java/com/example/myapi/service/DashboardServiceTest.java
git commit -m "feat: add dashboard aggregation endpoint"
```

### Task 3: Align frontend shared types and API clients with the unified contract

**Files:**
- Create: `frontend/src/types/dashboard.ts`
- Create: `frontend/src/api/dashboard.ts`
- Create: `frontend/src/hooks/useDashboard.ts`
- Modify: `frontend/src/types/plan.ts`
- Modify: `frontend/src/types/actualTrade.ts`
- Modify: `frontend/src/types/view.ts`
- Modify: `frontend/src/api/actualTrade.ts`
- Modify: `frontend/src/api/views.ts`
- Modify: `frontend/src/api/index.ts`
- Modify: `frontend/src/hooks/useActualTrades.ts`
- Modify: `frontend/src/hooks/useViews.ts`
- Modify: `frontend/src/hooks/index.ts`
- Test: `frontend/src/pages/Dashboard.tsx`

- [ ] **Step 1: Write a failing TypeScript build against the new contract**

Run:

```bash
npm --prefix frontend run build
```

Expected:

- FAIL with missing exports or property mismatches such as `profitLossAmount`, `avgCostPrice`, or `planName`

- [ ] **Step 2: Add the new dashboard types and client**

```ts
// frontend/src/types/dashboard.ts
export interface DashboardResponse {
  baselineCapital: number
  kpis: {
    planReturnPercent: number
    actualReturnPercent: number
    gapPercent: number
    planTotalValue: number
    actualTotalValue: number
  }
  trend: Array<{
    date: string
    planCumulativeReturnPercent: number
    actualCumulativeReturnPercent: number
  }>
  planHoldings: Array<{
    planId: number
    planName: string
    stockCode: string
    stockName: string
    quantity: number
    costPrice: number
    currentPrice: number
    unrealizedPLAmount: number
    unrealizedPLPercent: number
  }>
}
```

```ts
// frontend/src/api/dashboard.ts
import client from './client'
import type { ApiResponse } from '@/types'
import type { DashboardResponse } from '@/types/dashboard'

export const dashboardApi = {
  get: () =>
    client.get<ApiResponse<DashboardResponse>>('/dashboard').then((r) => r.data.data),
}
```

- [ ] **Step 3: Rename the shared trade and holdings types**

```ts
// frontend/src/types/actualTrade.ts
export interface ActualTrade {
  id: number
  stockCode: string
  stockName: string
  direction: Direction
  price: number
  quantity: number
  tradeDate: string
  profitLossAmount?: number
  profitLossPercent?: number
  matched?: boolean
  matchedBuyId?: number | null
  createdAt: string
  updatedAt?: string
}
```

```ts
// frontend/src/types/view.ts
export interface PlanHolding {
  planId: number
  planName: string
  stockCode: string
  stockName: string
  quantity: number
  costPrice: number
  currentPrice: number
  unrealizedPLAmount: number
  unrealizedPLPercent: number
}
```

- [ ] **Step 4: Export and hook the new dashboard query**

```ts
// frontend/src/hooks/useDashboard.ts
import { useQuery } from '@tanstack/react-query'
import { dashboardApi } from '@/api/dashboard'

export const DASHBOARD_KEY = ['dashboard'] as const

export function useDashboard() {
  return useQuery({
    queryKey: DASHBOARD_KEY,
    queryFn: () => dashboardApi.get(),
  })
}
```

- [ ] **Step 5: Re-run the frontend build**

Run:

```bash
npm --prefix frontend run build
```

Expected:

- build still fails in route components, but shared type and import errors are reduced to page-level consumers

- [ ] **Step 6: Commit shared frontend contract alignment**

```bash
git add frontend/src/types/dashboard.ts frontend/src/api/dashboard.ts frontend/src/hooks/useDashboard.ts frontend/src/types/plan.ts frontend/src/types/actualTrade.ts frontend/src/types/view.ts frontend/src/api/actualTrade.ts frontend/src/api/views.ts frontend/src/api/index.ts frontend/src/hooks/useActualTrades.ts frontend/src/hooks/useViews.ts frontend/src/hooks/index.ts
git commit -m "refactor: align frontend types with api contracts"
```

### Task 4: Migrate the dashboard page to the dedicated endpoint

**Files:**
- Modify: `frontend/src/pages/Dashboard.tsx`
- Modify: `frontend/src/components/views/TrendChart.tsx`
- Modify: `frontend/src/components/views/BaselineCapitalInput.tsx`
- Test: `frontend/src/pages/Dashboard.tsx`

- [ ] **Step 1: Write the failing dashboard page integration build**

Run:

```bash
npm --prefix frontend run build
```

Expected:

- FAIL because `Dashboard.tsx` still uses `useHoldings`, `useSnapshots`, and old trend point field names

- [ ] **Step 2: Switch the page to `useDashboard()`**

```tsx
const { data: dashboard, isLoading, error } = useDashboard()

const baseline = dashboard?.baselineCapital ?? 0
const planReturnPct = dashboard?.kpis.planReturnPercent ?? 0
const actualReturnPct = dashboard?.kpis.actualReturnPercent ?? 0
const gap = dashboard?.kpis.gapPercent ?? 0
```

```tsx
<TrendChart
  data={dashboard?.trend ?? []}
  baselineCapital={baseline}
/>
```

- [ ] **Step 3: Update `TrendChart` to consume the new field names**

```ts
interface TrendPoint {
  date: string
  planCumulativeReturnPercent: number
  actualCumulativeReturnPercent: number
}
```

```ts
const planSeries = data.map((d) => [d.date, d.planCumulativeReturnPercent])
const actualSeries = data.map((d) => [d.date, d.actualCumulativeReturnPercent])
```

- [ ] **Step 4: Render holdings and execution log from dashboard payload**

```tsx
<HoldingPanel
  title="当前持仓（预案）"
  holdings={dashboard?.planHoldings ?? []}
  loading={isLoading}
  linkTo="/plans"
/>
```

```tsx
{dashboard?.executionLog.slice(0, 6).map((item) => (
  <tr key={`${item.source}-${item.tradeDate}-${item.stockCode}`}>
    <td>{item.tradeDate}</td>
    <td>{item.source === 'PLAN' ? '预案' : '实盘'}{item.direction === 'BUY' ? '买入' : '卖出'}</td>
    <td>{item.stockCode} {item.stockName}</td>
    <td>{item.returnPercent == null ? '—' : `${item.returnPercent.toFixed(2)}%`}</td>
    <td>{item.triggerOrTradePrice.toFixed(2)}</td>
  </tr>
))}
```

- [ ] **Step 5: Re-run the frontend build**

Run:

```bash
npm --prefix frontend run build
```

Expected:

- dashboard-related type errors removed

- [ ] **Step 6: Commit dashboard frontend migration**

```bash
git add frontend/src/pages/Dashboard.tsx frontend/src/components/views/TrendChart.tsx frontend/src/components/views/BaselineCapitalInput.tsx
git commit -m "feat: migrate dashboard to real api"
```

### Task 5: Migrate plan pages to the unified real API contract

**Files:**
- Modify: `frontend/src/pages/PlanList.tsx`
- Modify: `frontend/src/pages/PlanDetail.tsx`
- Modify: `frontend/src/pages/PlanCreate.tsx`
- Modify: `frontend/src/pages/PlanEdit.tsx`
- Modify: `frontend/src/api/plan.ts`
- Modify: `frontend/src/hooks/usePlans.ts`
- Test: `frontend/src/pages/PlanDetail.tsx`

- [ ] **Step 1: Write the failing frontend build for plan route consumers**

Run:

```bash
npm --prefix frontend run build
```

Expected:

- FAIL on plan field mismatches such as `executionQuantity`, `conditions`, or renamed holding fields

- [ ] **Step 2: Keep create and update requests aligned with backend DTOs**

```ts
export interface CreatePlanRequest {
  name: string
  stockCode: string
  stockName: string
  cycle: string
  validUntil: string
  executionQuantity: number
  conditions: CreateConditionRequest[]
}
```

```ts
export interface UpdatePlanRequest {
  name: string
  stockName: string
  cycle: string
  validUntil: string
  executionQuantity: number
}
```

- [ ] **Step 3: Fix `PlanDetail.tsx` to use only detail and execution endpoints**

```tsx
<DetailRow label="股数" value={`${plan.executionQuantity} 股`} />
```

```tsx
const holdingExecution = executions?.find((e) => e.direction === 'BUY')
```

Ensure the page no longer assumes old field names or mock-only labels.

- [ ] **Step 4: Keep `PlanEdit.tsx` form state aligned with backend update contract**

```tsx
setForm({
  name: plan.name,
  stockCode: plan.stockCode,
  stockName: plan.stockName,
  cycle: plan.cycle,
  validUntil: plan.validUntil,
  executionQuantity: plan.executionQuantity,
})
```

```tsx
await updateMutation.mutateAsync({
  id: planId,
  data: {
    name: form.name,
    stockName: form.stockName,
    cycle: form.cycle,
    validUntil: form.validUntil,
    executionQuantity: form.executionQuantity,
  },
})
```

- [ ] **Step 5: Re-run the frontend build**

Run:

```bash
npm --prefix frontend run build
```

Expected:

- plan route build errors cleared

- [ ] **Step 6: Commit plan page migration**

```bash
git add frontend/src/pages/PlanList.tsx frontend/src/pages/PlanDetail.tsx frontend/src/pages/PlanCreate.tsx frontend/src/pages/PlanEdit.tsx frontend/src/api/plan.ts frontend/src/hooks/usePlans.ts
git commit -m "feat: migrate plan pages to real api"
```

### Task 6: Migrate actual trade pages to dedicated detail and list endpoints

**Files:**
- Modify: `frontend/src/api/actualTrade.ts`
- Modify: `frontend/src/hooks/useActualTrades.ts`
- Modify: `frontend/src/pages/TradeList.tsx`
- Modify: `frontend/src/pages/TradeCreate.tsx`
- Modify: `frontend/src/pages/TradeEdit.tsx`
- Test: `frontend/src/pages/TradeEdit.tsx`

- [ ] **Step 1: Write the failing build for actual trade pages**

Run:

```bash
npm --prefix frontend run build
```

Expected:

- FAIL because `TradeEdit.tsx` still pulls detail data from `useActualTrades()`

- [ ] **Step 2: Add a dedicated trade detail hook**

```ts
export const TRADE_KEYS = {
  all: ['actual-trades'] as const,
  list: (filters?: Record<string, string>) => ['actual-trades', 'list', filters ?? {}] as const,
  detail: (id: number) => ['actual-trades', 'detail', id] as const,
}

export function useActualTrade(id: number) {
  return useQuery({
    queryKey: TRADE_KEYS.detail(id),
    queryFn: () => actualTradeApi.get(id),
    enabled: id > 0,
  })
}
```

- [ ] **Step 3: Switch `TradeEdit.tsx` to `useActualTrade(planId)`**

```tsx
const { data: trade, isLoading } = useActualTrade(tradeId)
```

```tsx
useEffect(() => {
  if (!trade) return
  setForm({
    stockCode: trade.stockCode,
    stockName: trade.stockName,
    direction: trade.direction,
    price: String(trade.price),
    quantity: String(trade.quantity),
    tradeDate: trade.tradeDate,
  })
}, [trade])
```

- [ ] **Step 4: Rename trade list profit fields to the new contract**

```tsx
{trade.profitLossAmount !== undefined ? (
  <span className={trade.profitLossAmount >= 0 ? 'text-green-400' : 'text-red-400'}>
    {fmtPL(trade.profitLossAmount)}
  </span>
) : (
  <span className="text-gray-600">已匹配</span>
)}
```

- [ ] **Step 5: Re-run the frontend build**

Run:

```bash
npm --prefix frontend run build
```

Expected:

- trade create, list, and edit pages compile against the dedicated real API contract

- [ ] **Step 6: Commit actual trade page migration**

```bash
git add frontend/src/api/actualTrade.ts frontend/src/hooks/useActualTrades.ts frontend/src/pages/TradeList.tsx frontend/src/pages/TradeCreate.tsx frontend/src/pages/TradeEdit.tsx
git commit -m "feat: migrate actual trade pages to real api"
```

### Task 7: Migrate snapshot and settings pages and remove remaining mock assumptions

**Files:**
- Modify: `frontend/src/pages/SnapshotList.tsx`
- Modify: `frontend/src/pages/Settings.tsx`
- Modify: `frontend/src/api/views.ts`
- Modify: `frontend/src/hooks/useViews.ts`
- Modify: `frontend/src/mock/data.ts`

- [ ] **Step 1: Write the failing frontend build for snapshot and settings consumers**

Run:

```bash
npm --prefix frontend run build
```

Expected:

- FAIL on `useSnapshots` parameter names or holdings/settings field names

- [ ] **Step 2: Align snapshot query parameters with backend controller contract**

```ts
export function useSnapshots(filters?: { date?: string; planId?: number }) {
  return useQuery({
    queryKey: SNAPSHOTS_KEY(filters ?? {}),
    queryFn: () => viewApi.getSnapshots(filters),
  })
}
```

```ts
getSnapshots: (params?: { date?: string; planId?: number }) =>
  client.get<ApiResponse<Snapshot[]>>('/snapshots', { params }).then((r) => r.data.data),
```

- [ ] **Step 3: Update settings computations to use renamed holdings fields**

```tsx
const planMarketValue = holdings
  ? holdings.planHoldings.reduce((sum, h) => sum + h.currentPrice * h.quantity, 0)
  : 0
```

```tsx
楼{(holdings?.summary.actualTotalValue ?? 0).toLocaleString('zh-CN')}
```

- [ ] **Step 4: Remove mock-only exports and references**

```ts
// delete imports of mockApi and remove any remaining references to frontend/src/mock/data.ts
```

- [ ] **Step 5: Run the frontend build to verify all routes compile**

Run:

```bash
npm --prefix frontend run build
```

Expected:

- PASS

- [ ] **Step 6: Commit final route migration and mock cleanup**

```bash
git add frontend/src/pages/SnapshotList.tsx frontend/src/pages/Settings.tsx frontend/src/api/views.ts frontend/src/hooks/useViews.ts frontend/src/mock/data.ts
git commit -m "chore: remove remaining frontend mock assumptions"
```

### Task 8: Run full verification and document any residual contract issues

**Files:**
- Modify: `frontend/src/api/index.ts`
- Modify: `frontend/src/hooks/index.ts`
- Test: `pom.xml`
- Test: `frontend/package.json`

- [ ] **Step 1: Run backend test suite for changed areas**

Run:

```bash
mvn -Dtest=DashboardServiceTest,ActualTradeServiceTest,HoldingsServiceTest,PeriodSummaryServiceTest,PlanExecutionServiceTest test
```

Expected:

- PASS

- [ ] **Step 2: Run frontend production build**

Run:

```bash
npm --prefix frontend run build
```

Expected:

- PASS

- [ ] **Step 3: Run a manual local smoke check**

Run:

```bash
mvn -q -DskipTests spring-boot:run
```

```bash
npm --prefix frontend run dev
```

Expected:

- dashboard loads from `/api/dashboard`
- plan detail and edit pages load real data
- trade edit page loads dedicated detail data
- snapshot filters use `date` and `planId`
- settings page updates baseline capital through the live API

- [ ] **Step 4: Commit verification-only index cleanup if needed**

```bash
git add frontend/src/api/index.ts frontend/src/hooks/index.ts
git commit -m "chore: finalize api and hook exports"
```
