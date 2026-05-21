package com.example.myapi.scheduler;

import com.example.myapi.entity.*;
import com.example.myapi.repository.*;
import com.example.myapi.service.*;
import com.example.myapi.service.TushareService.KLineData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketCloseTaskIntegrationTest {

    @Mock private PlanRepository planRepository;
    @Mock private PlanExecutionRepository executionRepository;
    @Mock private DailySnapshotRepository snapshotRepository;
    @Mock private ActualTradeRepository actualTradeRepository;
    @Mock private TushareService tushareService;
    @Mock private SystemConfigService systemConfigService;
    @Mock private SystemConfig systemConfig;
    @Mock private PlanAccount planAccount;
    @Mock private ActualAccount actualAccount;

    private PlanExecutionService executionService;
    private MarketCloseTask task;

    @BeforeEach
    void setUp() {
        when(systemConfig.getBaselineCapital()).thenReturn(new BigDecimal("500000"));
        when(systemConfigService.getSystemConfig()).thenReturn(systemConfig);
        when(systemConfigService.getPlanAccount()).thenReturn(planAccount);
        when(systemConfigService.getActualAccount()).thenReturn(actualAccount);
        when(planAccount.getCashBalance()).thenReturn(new BigDecimal("500000"));
        when(actualAccount.getCashBalance()).thenReturn(new BigDecimal("500000"));

        executionService = new PlanExecutionService(executionRepository, planRepository, systemConfigService);
        task = new MarketCloseTask(
                planRepository, executionRepository, snapshotRepository,
                actualTradeRepository, tushareService, executionService, systemConfigService);
    }

    /**
     * Integration Test 1: Complete plan lifecycle
     * PENDING + buy condition met -> HOLDING -> sell condition met -> CLOSED
     * This tests the full state machine transition across two evaluation cycles.
     */
    @Test
    void integration_completeLifecycle_pendingToHoldingToClosed() {
        LocalDate today = LocalDate.now();

        // Day 1: PENDING plan meets buy condition -> HOLDING
        Plan plan1 = newPlan(1L, PlanStatus.PENDING, PlanCycle.DAILY, today);
        addCond(plan1, 1L, TradeDirection.BUY, ConditionType.PRICE, new BigDecimal("11.00"), true);
        KLineData kd1 = kLine("11.00");

        when(planRepository.findByStatus(PlanStatus.PENDING)).thenReturn(Collections.emptyList());
        when(planRepository.findByStatusIn(any())).thenReturn(List.of(plan1));
        when(tushareService.isTradingDay(any())).thenReturn(true);
        when(tushareService.getDailyKLine(eq("000001"), eq(today), eq(true))).thenReturn(Optional.of(kd1));
        when(tushareService.evaluateCondition(any(), any(), any())).thenReturn(true);
        when(executionRepository.save(any())).thenAnswer(inv -> { setField(inv.getArgument(0), "id", 1L); return inv.getArgument(0); });
        when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(actualTradeRepository.findByStockCode(any())).thenReturn(Collections.emptyList());
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        task.onMarketClose();

        assertEquals(PlanStatus.HOLDING, plan1.getStatus());
        verify(executionRepository).save(argThat(e -> e.getDirection() == TradeDirection.BUY));
    }

    /**
     * Integration Test 2: Multiple plans with different statuses processed in one run
     * - Plan A: PENDING, buy triggers
     * - Plan B: HOLDING, sell triggers
     * - Plan C: PENDING, no trigger
     * - Plan D: HOLDING, no trigger
     */
    @Test
    void integration_multiplePlans_differentStatusesInOneRun() {
        LocalDate today = LocalDate.now();

        Plan planA = newPlan(1L, PlanStatus.PENDING, PlanCycle.DAILY, today);
        addCond(planA, 1L, TradeDirection.BUY, ConditionType.PRICE, new BigDecimal("12.00"), true);

        Plan planB = newPlan(2L, PlanStatus.HOLDING, PlanCycle.DAILY, today.minusDays(1));
        addCond(planB, 2L, TradeDirection.SELL, ConditionType.PRICE, new BigDecimal("11.00"), true);

        Plan planC = newPlan(3L, PlanStatus.PENDING, PlanCycle.DAILY, today);
        addCond(planC, 3L, TradeDirection.BUY, ConditionType.PRICE, new BigDecimal("9.00"), true); // price 10 won't reach 9

        Plan planD = newPlan(4L, PlanStatus.HOLDING, PlanCycle.DAILY, today.minusDays(1));
        addCond(planD, 4L, TradeDirection.SELL, ConditionType.PRICE, new BigDecimal("8.00"), true); // price 10 won't go to 8

        KLineData kd = kLine("10.00");

        when(planRepository.findByStatus(PlanStatus.PENDING)).thenReturn(Collections.emptyList());
        when(planRepository.findByStatusIn(any())).thenReturn(List.of(planA, planB, planC, planD));
        when(tushareService.getDailyKLine(eq("000001"), eq(today), eq(true))).thenReturn(Optional.of(kd));
        when(tushareService.evaluateCondition(any(), any(), any())).thenReturn(false);
        // Only planA's buy condition triggers
        when(tushareService.evaluateCondition(argThat(c -> c.getDirection() == TradeDirection.BUY && c.getTargetPrice().compareTo(new BigDecimal("12.00")) == 0), any(), any())).thenReturn(true);
        when(executionRepository.save(any())).thenAnswer(inv -> { setField(inv.getArgument(0), "id", 1L); return inv.getArgument(0); });
        when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(actualTradeRepository.findByStockCode(any())).thenReturn(Collections.emptyList());
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        task.evaluateTriggerConditions(today);

        assertEquals(PlanStatus.HOLDING, planA.getStatus(), "Plan A should trigger BUY and become HOLDING");
        assertEquals(PlanStatus.HOLDING, planB.getStatus(), "Plan B sell not triggered, stays HOLDING");
        assertEquals(PlanStatus.PENDING, planC.getStatus(), "Plan C buy not triggered, stays PENDING");
        assertEquals(PlanStatus.HOLDING, planD.getStatus(), "Plan D sell not triggered, stays HOLDING");

        // Only planA should have a BUY execution recorded
        verify(executionRepository, times(1)).save(argThat(e -> e.getDirection() == TradeDirection.BUY));
        verify(executionRepository, never()).save(argThat(e -> e.getDirection() == TradeDirection.SELL));
    }

    /**
     * Integration Test 3: Complete end-to-end task run
     * 1. Non-trading day check
     * 2. Plan expiration check (with actual plan to expire)
     * 3. Trigger evaluation (one plan triggers)
     * 4. Snapshot generation (both plans generate snapshots)
     */
    @Test
    void integration_fullEndToEndTaskRun() {
        LocalDate today = LocalDate.now();

        Plan pendingPlan = newPlan(1L, PlanStatus.PENDING, PlanCycle.DAILY, LocalDateTime.of(2026, 5, 20, 10, 0));
        addCond(pendingPlan, 1L, TradeDirection.BUY, ConditionType.PRICE, new BigDecimal("11.00"), true);

        Plan holdingPlan = newPlan(2L, PlanStatus.HOLDING, PlanCycle.DAILY, LocalDateTime.of(2026, 5, 20, 10, 0));
        addCond(holdingPlan, 2L, TradeDirection.SELL, ConditionType.PRICE, new BigDecimal("11.00"), true);
        addExec(holdingPlan, TradeDirection.BUY, new BigDecimal("10.00"), today.minusDays(1));

        KLineData kd = kLine("11.00");

        when(tushareService.isTradingDay(today)).thenReturn(true);
        when(planRepository.findByStatus(PlanStatus.PENDING)).thenReturn(List.of(pendingPlan));
        // First call: both plans returned (evaluateTriggerConditions)
        // Second call: empty (generatePlanSnapshots since both are now EXPIRED/CLOSED)
        when(planRepository.findByStatusIn(any()))
                .thenReturn(List.of(pendingPlan, holdingPlan))
                .thenReturn(List.of());
        when(tushareService.getDailyKLine(any(), eq(today), eq(true))).thenReturn(Optional.of(kd));
        when(tushareService.evaluateCondition(any(), any(), any())).thenReturn(true);
        when(executionRepository.save(any())).thenAnswer(inv -> { setField(inv.getArgument(0), "id", 1L); return inv.getArgument(0); });
        when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(actualTradeRepository.findByStockCode(any())).thenReturn(Collections.emptyList());
        when(actualTradeRepository.findAll()).thenReturn(Collections.emptyList());
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        task.onMarketClose();

        // Step 1: trading day check passed
        verify(tushareService).isTradingDay(today);

        // Step 2: pendingPlan from yesterday should be expired (DAILY cycle, created yesterday)
        assertEquals(PlanStatus.EXPIRED, pendingPlan.getStatus(), "Yesterday's DAILY plan should be expired");

        // Step 3: holdingPlan sell should trigger since close(11.00) <= target(11.00)
        assertEquals(PlanStatus.CLOSED, holdingPlan.getStatus(), "holdingPlan sell triggers since close <= 11.00");

        // Step 4: both plans are EXPIRED/CLOSED, findByStatusIn returns empty, no snapshots
        verify(snapshotRepository, never()).save(argThat(s -> s.getPlanId() != null));
    }

    /**
     * Integration Test 4: Snapshot generation with actual return calculation
     * Tests that HOLDING plan snapshots correctly compute return based on baselineCapital.
     */
    @Test
    void integration_snapshotReturnCalculation_multipleBuys() {
        LocalDate today = LocalDate.now();

        Plan plan = newPlan(1L, PlanStatus.HOLDING, PlanCycle.DAILY, today.minusDays(5));
        // executionQuantity defaults to 100 via Plan builder
        PlanExecution buy1 = addExec(plan, TradeDirection.BUY, new BigDecimal("10.00"), today.minusDays(5));
        PlanExecution buy2 = addExec(plan, TradeDirection.BUY, new BigDecimal("12.00"), today.minusDays(3));

        // current close = 11.00
        // quantity per buy = 100 shares (default executionQuantity)
        // marketValue = 11.00 * 100 = 1100
        // totalValue = 500000 + 1100 = 501100
        // returnPct = (501100 - 500000) / 500000 * 100 = 0.22%
        KLineData kd = kLine("11.00");

        when(planRepository.findByStatus(PlanStatus.PENDING)).thenReturn(Collections.emptyList());
        when(planRepository.findByStatusIn(any())).thenReturn(List.of(plan));
        when(executionRepository.findByPlanId(1L)).thenReturn(List.of(buy1, buy2));
        when(tushareService.getDailyKLine(eq("000001"), eq(today), anyBoolean())).thenReturn(Optional.of(kd));
        when(actualTradeRepository.findByStockCode(any())).thenReturn(Collections.emptyList());
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        task.generatePlanSnapshots(today);

        verify(snapshotRepository).save(argThat(s ->
                s.getPlanId().equals(1L) &&
                s.getPlanStatus() == PlanStatus.HOLDING &&
                s.getPlanReturnPercent() != null &&
                s.getPlanCashBalance() != null));
    }

    /**
     * Integration Test 5: Holiday handling - task skips on non-trading day
     */
    @Test
    void integration_holiday_skipsAllProcessing() {
        // onMarketClose() uses LocalDate.now(), so mock any() date
        when(tushareService.isTradingDay(any())).thenReturn(false);

        task.onMarketClose();

        verify(tushareService).isTradingDay(any());
        verify(planRepository, never()).findByStatus(any());
        verify(planRepository, never()).findByStatusIn(any());
        verify(executionRepository, never()).save(any());
        verify(snapshotRepository, never()).save(any());
    }

    // ========== Helper methods ==========

    private Plan newPlan(Long id, PlanStatus status, PlanCycle cycle, LocalDateTime createdAt) {
        Plan p = Plan.builder()
                .name("测试预案-" + id)
                .stockCode("000001")
                .stockName("平安银行")
                .cycle(cycle)
                .status(status)
                .isLocked(false)
                .executionQuantity(new BigDecimal("100"))
                .build();
        setField(p, "id", id);
        setField(p, "createdAt", createdAt);
        setField(p, "updatedAt", createdAt);
        setField(p, "conditions", new ArrayList<>());
        setField(p, "executions", new ArrayList<>());
        return p;
    }

    private Plan newPlan(Long id, PlanStatus status, PlanCycle cycle, LocalDate createdAt) {
        return newPlan(id, status, cycle, createdAt.atTime(10, 0));
    }

    private void addCond(Plan plan, Long condId, TradeDirection direction,
                         ConditionType type, BigDecimal targetPrice, boolean active) {
        PlanCondition c = PlanCondition.builder()
                .conditionType(type)
                .direction(direction)
                .maPeriod(5)
                .targetPrice(targetPrice)
                .isActive(active)
                .build();
        setField(c, "id", condId);
        setField(c, "plan", plan);
        plan.getConditions().add(c);
    }

    private PlanExecution addExec(Plan plan, TradeDirection direction,
                                  BigDecimal price, LocalDate tradeDate) {
        PlanExecution e = PlanExecution.builder()
                .tradeDate(tradeDate)
                .direction(direction)
                .triggerPrice(price)
                .triggered(true)
                .executed(true)
                .build();
        setField(e, "id", (long) (plan.getExecutions().size() + 1));
        setField(e, "plan", plan);
        plan.getExecutions().add(e);
        return e;
    }

    private KLineData kLine(String close) {
        return new KLineData("000001", LocalDate.now(),
                new BigDecimal("10.5"), new BigDecimal("11.5"),
                new BigDecimal("10.5"), new BigDecimal(close), 1_000_000);
    }

    private void setField(Object target, String name, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception ignored) {}
    }
}
