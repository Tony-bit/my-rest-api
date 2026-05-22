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
class MarketCloseTaskBuySellTest {

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

    @Test
    void evaluateBuyPlans_pendingBuyTriggered_transitionsToHolding() {
        Plan buyPlan = newBuyPlan(PlanStatus.PENDING, PlanCycle.DAILY, LocalDateTime.now());
        addCond(buyPlan, ConditionType.PRICE, new BigDecimal("11.00"));
        KLineData kd = kLine("11.00");

        when(planRepository.findByPlanTypeAndStatus(PlanType.BUY, PlanStatus.PENDING)).thenReturn(listOf(buyPlan));
        when(tushareService.getDailyKLine(any(), any(), anyBoolean())).thenReturn(Optional.of(kd));
        when(tushareService.evaluateCondition(any(), any(PlanType.class), any(), any())).thenReturn(true);
        when(executionRepository.save(any())).thenAnswer(inv -> { setField(inv.getArgument(0), "id", 1L); return inv.getArgument(0); });
        when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        task.evaluateBuyPlans(LocalDate.now());

        verify(executionRepository).save(any(PlanExecution.class));
        assertEquals(PlanStatus.HOLDING, buyPlan.getStatus());
        assertEquals(buyPlan.getId(), buyPlan.getTradePlanId());
    }

    @Test
    void evaluateBuyPlans_pendingBuyNotTriggered_staysPending() {
        Plan buyPlan = newBuyPlan(PlanStatus.PENDING, PlanCycle.DAILY, LocalDateTime.now());
        addCond(buyPlan, ConditionType.PRICE, new BigDecimal("12.00"));
        KLineData kd = kLine("11.00");

        when(planRepository.findByPlanTypeAndStatus(PlanType.BUY, PlanStatus.PENDING)).thenReturn(listOf(buyPlan));
        when(tushareService.getDailyKLine(any(), any(), anyBoolean())).thenReturn(Optional.of(kd));
        when(tushareService.evaluateCondition(any(), eq(PlanType.BUY), any(), any())).thenReturn(false);

        task.evaluateBuyPlans(LocalDate.now());

        verify(executionRepository, never()).save(any());
        assertEquals(PlanStatus.PENDING, buyPlan.getStatus());
    }

    @Test
    void evaluateBuyPlans_noConditions_skipped() {
        Plan buyPlan = newBuyPlan(PlanStatus.PENDING, PlanCycle.DAILY, LocalDateTime.now());
        when(planRepository.findByPlanTypeAndStatus(PlanType.BUY, PlanStatus.PENDING)).thenReturn(listOf(buyPlan));

        task.evaluateBuyPlans(LocalDate.now());

        verify(tushareService, never()).getDailyKLine(any(), any(), anyBoolean());
        verify(executionRepository, never()).save(any());
    }

    @Test
    void evaluateBuyPlans_maType_callsCalculateMA() {
        Plan buyPlan = newBuyPlan(PlanStatus.PENDING, PlanCycle.DAILY, LocalDateTime.now());
        addCond(buyPlan, ConditionType.MA, null);
        buyPlan.getConditions().get(0).setMaPeriod(5);
        KLineData kd = kLine("11.00");

        when(planRepository.findByPlanTypeAndStatus(PlanType.BUY, PlanStatus.PENDING)).thenReturn(listOf(buyPlan));
        when(tushareService.getDailyKLine(any(), any(), anyBoolean())).thenReturn(Optional.of(kd));
        when(tushareService.evaluateCondition(any(), eq(PlanType.BUY), any(), any())).thenReturn(true);
        when(tushareService.calculateMA(any(), anyInt(), any())).thenReturn(new BigDecimal("10.00"));
        when(executionRepository.save(any())).thenAnswer(inv -> { setField(inv.getArgument(0), "id", 1L); return inv.getArgument(0); });
        when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        task.evaluateBuyPlans(LocalDate.now());

        verify(tushareService).calculateMA(eq("000001"), eq(5), any());
    }

    @Test
    void evaluateSellPlans_linkedBuyNotHolding_skipped() {
        Plan buyPlan = newBuyPlan(PlanStatus.PENDING, PlanCycle.DAILY, LocalDateTime.now());
        Plan sellPlan = newSellPlan(buyPlan, PlanStatus.PENDING);
        addCond(sellPlan, ConditionType.PRICE, new BigDecimal("11.00"));

        when(planRepository.findByPlanTypeAndStatus(PlanType.SELL, PlanStatus.PENDING)).thenReturn(listOf(sellPlan));

        task.evaluateSellPlans(LocalDate.now());

        verify(tushareService, never()).getDailyKLine(any(), any(), anyBoolean());
        verify(executionRepository, never()).save(any());
    }

    @Test
    void evaluateSellPlans_linkedBuyHolding_triggered() {
        Plan buyPlan = newBuyPlan(PlanStatus.HOLDING, PlanCycle.DAILY, LocalDateTime.now());
        Plan sellPlan = newSellPlan(buyPlan, PlanStatus.PENDING);
        addCond(sellPlan, ConditionType.PRICE, new BigDecimal("11.00"));
        KLineData kd = kLine("11.00");

        when(planRepository.findByPlanTypeAndStatus(PlanType.SELL, PlanStatus.PENDING)).thenReturn(listOf(sellPlan));
        when(tushareService.getDailyKLine(any(), any(), anyBoolean())).thenReturn(Optional.of(kd));
        when(tushareService.evaluateCondition(any(), eq(PlanType.SELL), any(), any())).thenReturn(true);
        when(executionRepository.save(any())).thenAnswer(inv -> { setField(inv.getArgument(0), "id", 1L); return inv.getArgument(0); });
        when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        task.evaluateSellPlans(LocalDate.now());

        verify(executionRepository).save(any(PlanExecution.class));
        assertEquals(PlanStatus.CLOSED, sellPlan.getStatus());
        assertEquals(PlanStatus.CLOSED, buyPlan.getStatus());
    }

    @Test
    void evaluateSellPlans_triggered_closesLinkedBuyPlan() {
        Plan buyPlan = newBuyPlan(PlanStatus.HOLDING, PlanCycle.DAILY, LocalDateTime.now());
        Plan sellPlan = newSellPlan(buyPlan, PlanStatus.PENDING);
        addCond(sellPlan, ConditionType.PRICE, new BigDecimal("11.00"));
        KLineData kd = kLine("11.00");

        when(planRepository.findByPlanTypeAndStatus(PlanType.SELL, PlanStatus.PENDING)).thenReturn(listOf(sellPlan));
        when(tushareService.getDailyKLine(any(), any(), anyBoolean())).thenReturn(Optional.of(kd));
        when(tushareService.evaluateCondition(any(), eq(PlanType.SELL), any(), any())).thenReturn(true);
        when(executionRepository.save(any())).thenAnswer(inv -> { setField(inv.getArgument(0), "id", 1L); return inv.getArgument(0); });
        when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        task.evaluateSellPlans(LocalDate.now());

        assertEquals(PlanStatus.CLOSED, buyPlan.getStatus());
        verify(planRepository).save(buyPlan);
    }

    @Test
    void evaluateSellPlans_notTriggered_staysPending() {
        Plan buyPlan = newBuyPlan(PlanStatus.HOLDING, PlanCycle.DAILY, LocalDateTime.now());
        Plan sellPlan = newSellPlan(buyPlan, PlanStatus.PENDING);
        addCond(sellPlan, ConditionType.PRICE, new BigDecimal("9.00"));
        KLineData kd = kLine("11.00");

        when(planRepository.findByPlanTypeAndStatus(PlanType.SELL, PlanStatus.PENDING)).thenReturn(listOf(sellPlan));
        when(tushareService.getDailyKLine(any(), any(), anyBoolean())).thenReturn(Optional.of(kd));
        when(tushareService.evaluateCondition(any(), eq(PlanType.SELL), any(), any())).thenReturn(false);

        task.evaluateSellPlans(LocalDate.now());

        verify(executionRepository, never()).save(any());
        assertEquals(PlanStatus.PENDING, sellPlan.getStatus());
        assertEquals(PlanStatus.HOLDING, buyPlan.getStatus());
    }

    @Test
    void checkAndExpirePlans_pendingPlans_expiresOthers() {
        Plan buyPlan = newBuyPlan(PlanStatus.PENDING, PlanCycle.MONTHLY, LocalDateTime.of(2026, 5, 1, 10, 0));
        buyPlan.setValidUntil(LocalDate.of(2026, 5, 20));

        when(planRepository.findByStatus(PlanStatus.PENDING)).thenReturn(listOf(buyPlan));

        task.checkAndExpirePlans(LocalDate.of(2026, 5, 21));

        assertEquals(PlanStatus.EXPIRED, buyPlan.getStatus());
        verify(planRepository).save(buyPlan);
    }

    @Test
    void checkAndExpirePlans_dailyPlanToday_staysPending() {
        Plan buyPlan = newBuyPlan(PlanStatus.PENDING, PlanCycle.DAILY, LocalDateTime.of(2026, 5, 21, 10, 0));

        when(planRepository.findByStatus(PlanStatus.PENDING)).thenReturn(listOf(buyPlan));

        task.checkAndExpirePlans(LocalDate.of(2026, 5, 21));

        assertEquals(PlanStatus.PENDING, buyPlan.getStatus());
        verify(planRepository, never()).save(any());
    }

    // --- Factory helpers ---

    private Plan newBuyPlan(PlanStatus status, PlanCycle cycle, LocalDateTime createdAt) {
        Plan p = Plan.builder()
                .name("测试买入预案")
                .stockCode("000001")
                .stockName("平安银行")
                .cycle(cycle)
                .planType(PlanType.BUY)
                .status(status)
                .executionQuantity(new BigDecimal("100"))
                .build();
        setField(p, "id", 1L);
        setField(p, "tradePlanId", 1L);
        setField(p, "createdAt", createdAt);
        setField(p, "updatedAt", createdAt);
        setField(p, "conditions", new ArrayList<>());
        setField(p, "executions", new ArrayList<>());
        return p;
    }

    private Plan newSellPlan(Plan buyPlan, PlanStatus status) {
        Plan p = Plan.builder()
                .name("测试卖出预案")
                .stockCode(buyPlan.getStockCode())
                .stockName(buyPlan.getStockName())
                .cycle(buyPlan.getCycle())
                .planType(PlanType.SELL)
                .status(status)
                .tradePlanId(buyPlan.getId())
                .buyPlan(buyPlan)
                .executionQuantity(buyPlan.getExecutionQuantity())
                .build();
        setField(p, "id", 2L);
        setField(p, "createdAt", LocalDateTime.now());
        setField(p, "updatedAt", LocalDateTime.now());
        setField(p, "conditions", new ArrayList<>());
        setField(p, "executions", new ArrayList<>());
        return p;
    }

    private void addCond(Plan plan, ConditionType type, BigDecimal targetPrice) {
        PlanCondition c = PlanCondition.builder()
                .conditionType(type)
                .maPeriod(5)
                .targetPrice(targetPrice)
                .build();
        setField(c, "id", 1L);
        setField(c, "plan", plan);
        plan.getConditions().add(c);
    }

    private KLineData kLine(String close) {
        return new KLineData("000001", LocalDate.now(),
                new BigDecimal("10.5"), new BigDecimal("11.5"),
                new BigDecimal("10.5"), new BigDecimal(close), 1_000_000);
    }

    @SafeVarargs
    private <T> List<T> listOf(T... items) {
        List<T> list = new ArrayList<>();
        for (T item : items) list.add(item);
        return list;
    }

    private void setField(Object target, String name, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception ignored) {}
    }
}
