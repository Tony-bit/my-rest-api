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
class MarketCloseTaskTest {

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

    // 7.1 onMarketClose

    @Test
    void onMarketClose_nonTradingDay_skipsAllLogic() {
        when(tushareService.isTradingDay(any())).thenReturn(false);
        task.onMarketClose();
        verify(tushareService).isTradingDay(any());
        verifyNoInteractions(planRepository);
    }

    @Test
    void onMarketClose_tradingDay_callsAllSteps() {
        when(tushareService.isTradingDay(any())).thenReturn(true);
        when(planRepository.findByStatus(PlanStatus.PENDING)).thenReturn(List.of());
        when(planRepository.findByStatusIn(any())).thenReturn(List.of());
        when(actualTradeRepository.findAll()).thenReturn(List.of());
        task.onMarketClose();
        verify(tushareService).isTradingDay(any());
    }

    // 7.2 checkAndExpirePlans

    @Test
    void checkAndExpirePlans_dailyPlanNotToday_expires() {
        Plan plan = newPlan(PlanStatus.PENDING, PlanCycle.DAILY, LocalDateTime.of(2026, 5, 20, 10, 0));
        when(planRepository.findByStatus(PlanStatus.PENDING)).thenReturn(listOf(plan));
        task.checkAndExpirePlans(LocalDate.of(2026, 5, 21));
        assertEquals(PlanStatus.EXPIRED, plan.getStatus());
        verify(planRepository).save(plan);
    }

    @Test
    void checkAndExpirePlans_dailyPlanToday_staysPending() {
        Plan plan = newPlan(PlanStatus.PENDING, PlanCycle.DAILY, LocalDateTime.of(2026, 5, 21, 10, 0));
        when(planRepository.findByStatus(PlanStatus.PENDING)).thenReturn(listOf(plan));
        task.checkAndExpirePlans(LocalDate.of(2026, 5, 21));
        assertEquals(PlanStatus.PENDING, plan.getStatus());
        verify(planRepository, never()).save(any());
    }

    @Test
    void checkAndExpirePlans_monthlyValidUntilFuture_staysPending() {
        Plan plan = newPlan(PlanStatus.PENDING, PlanCycle.MONTHLY, LocalDateTime.of(2026, 5, 1, 10, 0));
        plan.setValidUntil(LocalDate.of(2026, 5, 31));
        when(planRepository.findByStatus(PlanStatus.PENDING)).thenReturn(listOf(plan));
        task.checkAndExpirePlans(LocalDate.of(2026, 5, 21));
        assertEquals(PlanStatus.PENDING, plan.getStatus());
        verify(planRepository, never()).save(any());
    }

    @Test
    void checkAndExpirePlans_validUntilPast_expires() {
        Plan plan = newPlan(PlanStatus.PENDING, PlanCycle.MONTHLY, LocalDateTime.of(2026, 5, 1, 10, 0));
        plan.setValidUntil(LocalDate.of(2026, 5, 20));
        when(planRepository.findByStatus(PlanStatus.PENDING)).thenReturn(listOf(plan));
        task.checkAndExpirePlans(LocalDate.of(2026, 5, 21));
        assertEquals(PlanStatus.EXPIRED, plan.getStatus());
        verify(planRepository).save(plan);
    }

    @Test
    void checkAndExpirePlans_validUntilNull_monthly_staysPending() {
        Plan plan = newPlan(PlanStatus.PENDING, PlanCycle.MONTHLY, LocalDateTime.of(2026, 5, 1, 10, 0));
        plan.setValidUntil(null);
        when(planRepository.findByStatus(PlanStatus.PENDING)).thenReturn(listOf(plan));
        task.checkAndExpirePlans(LocalDate.of(2026, 5, 21));
        assertEquals(PlanStatus.PENDING, plan.getStatus());
        verify(planRepository, never()).save(any());
    }

    @Test
    void checkAndExpirePlans_holdingPlan_notChecked() {
        when(planRepository.findByStatus(PlanStatus.PENDING)).thenReturn(List.of());
        task.checkAndExpirePlans(LocalDate.of(2026, 5, 21));
        verify(planRepository, never()).save(any());
    }

    // 7.3 evaluateTriggerConditions

    @Test
    void evaluateTriggerConditions_pendingBuyTriggered_transitionsToHolding() {
        Plan plan = newPlan(PlanStatus.PENDING, PlanCycle.DAILY, LocalDateTime.of(2026, 5, 20, 10, 0));
        addConds(plan, cond(TradeDirection.BUY, ConditionType.PRICE, new BigDecimal("11.00"), true));
        KLineData kd = kLine("11.00");

        when(planRepository.findByStatusIn(any())).thenReturn(listOf(plan));
        when(tushareService.getDailyKLine(any(), any(), anyBoolean())).thenReturn(Optional.of(kd));
        when(tushareService.evaluateCondition(any(), any(), any())).thenReturn(true);
        when(executionRepository.save(any())).thenAnswer(inv -> { setField(inv.getArgument(0), "id", 1L); return inv.getArgument(0); });
        when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        task.evaluateTriggerConditions(LocalDate.now());

        verify(executionRepository).save(any(PlanExecution.class));
        assertEquals(PlanStatus.HOLDING, plan.getStatus());
    }

    @Test
    void evaluateTriggerConditions_pendingNotTriggered_staysPending() {
        Plan plan = newPlan(PlanStatus.PENDING, PlanCycle.DAILY, LocalDateTime.of(2026, 5, 20, 10, 0));
        addConds(plan, cond(TradeDirection.BUY, ConditionType.PRICE, new BigDecimal("12.00"), true));
        KLineData kd = kLine("11.00");

        when(planRepository.findByStatusIn(any())).thenReturn(listOf(plan));
        when(tushareService.getDailyKLine(any(), any(), anyBoolean())).thenReturn(Optional.of(kd));
        when(tushareService.evaluateCondition(any(), any(), any())).thenReturn(false);

        task.evaluateTriggerConditions(LocalDate.now());

        verify(executionRepository, never()).save(any());
    }

    @Test
    void evaluateTriggerConditions_pendingBuyTriggeredOnlyOnce_breaksLoop() {
        Plan plan = newPlan(PlanStatus.PENDING, PlanCycle.DAILY, LocalDateTime.of(2026, 5, 20, 10, 0));
        addConds(plan,
                cond(TradeDirection.BUY, ConditionType.PRICE, new BigDecimal("11.00"), true),
                cond(TradeDirection.BUY, ConditionType.PRICE, new BigDecimal("11.00"), true));
        KLineData kd = kLine("11.00");

        when(planRepository.findByStatusIn(any())).thenReturn(listOf(plan));
        when(tushareService.getDailyKLine(any(), any(), anyBoolean())).thenReturn(Optional.of(kd));
        when(tushareService.evaluateCondition(any(), any(), any())).thenReturn(true);
        when(executionRepository.save(any())).thenAnswer(inv -> { setField(inv.getArgument(0), "id", 1L); return inv.getArgument(0); });
        when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        task.evaluateTriggerConditions(LocalDate.now());

        verify(executionRepository, times(1)).save(any(PlanExecution.class));
        assertEquals(PlanStatus.HOLDING, plan.getStatus());
    }

    @Test
    void evaluateTriggerConditions_holdingSellTriggered_transitionsToClosed() {
        Plan plan = newPlan(PlanStatus.HOLDING, PlanCycle.DAILY, LocalDateTime.of(2026, 5, 20, 10, 0));
        addConds(plan, cond(TradeDirection.SELL, ConditionType.PRICE, new BigDecimal("11.00"), true));
        KLineData kd = kLine("11.00");

        when(planRepository.findByStatusIn(any())).thenReturn(listOf(plan));
        when(tushareService.getDailyKLine(any(), any(), anyBoolean())).thenReturn(Optional.of(kd));
        when(tushareService.evaluateCondition(any(), any(), any())).thenReturn(true);
        when(executionRepository.save(any())).thenAnswer(inv -> { setField(inv.getArgument(0), "id", 1L); return inv.getArgument(0); });
        when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        task.evaluateTriggerConditions(LocalDate.now());

        verify(executionRepository).save(any(PlanExecution.class));
        assertEquals(PlanStatus.CLOSED, plan.getStatus());
    }

    @Test
    void evaluateTriggerConditions_holdingNotTriggered_staysHolding() {
        Plan plan = newPlan(PlanStatus.HOLDING, PlanCycle.DAILY, LocalDateTime.of(2026, 5, 20, 10, 0));
        addConds(plan, cond(TradeDirection.SELL, ConditionType.PRICE, new BigDecimal("9.00"), true));
        KLineData kd = kLine("11.00");

        when(planRepository.findByStatusIn(any())).thenReturn(listOf(plan));
        when(tushareService.getDailyKLine(any(), any(), anyBoolean())).thenReturn(Optional.of(kd));
        when(tushareService.evaluateCondition(any(), any(), any())).thenReturn(false);

        task.evaluateTriggerConditions(LocalDate.now());

        verify(executionRepository, never()).save(any());
    }

    @Test
    void evaluateTriggerConditions_holdingSkipsBuyConditions() {
        Plan plan = newPlan(PlanStatus.HOLDING, PlanCycle.DAILY, LocalDateTime.of(2026, 5, 20, 10, 0));
        addConds(plan, cond(TradeDirection.BUY, ConditionType.PRICE, new BigDecimal("11.00"), true));
        KLineData kd = kLine("11.00");

        when(planRepository.findByStatusIn(any())).thenReturn(listOf(plan));
        when(tushareService.getDailyKLine(any(), any(), anyBoolean())).thenReturn(Optional.of(kd));

        task.evaluateTriggerConditions(LocalDate.now());

        verify(tushareService, never()).calculateMA(any(), anyInt(), any());
    }

    @Test
    void evaluateTriggerConditions_inactiveCondition_skipped() {
        Plan plan = newPlan(PlanStatus.PENDING, PlanCycle.DAILY, LocalDateTime.of(2026, 5, 20, 10, 0));
        addConds(plan, cond(TradeDirection.BUY, ConditionType.PRICE, new BigDecimal("11.00"), false));

        when(planRepository.findByStatusIn(any())).thenReturn(listOf(plan));

        task.evaluateTriggerConditions(LocalDate.now());

        verify(executionRepository, never()).save(any());
    }

    @Test
    void evaluateTriggerConditions_noKLine_skipsPlan() {
        Plan plan = newPlan(PlanStatus.PENDING, PlanCycle.DAILY, LocalDateTime.of(2026, 5, 20, 10, 0));
        addConds(plan, cond(TradeDirection.BUY, ConditionType.PRICE, new BigDecimal("11.00"), true));

        when(planRepository.findByStatusIn(any())).thenReturn(listOf(plan));
        when(tushareService.getDailyKLine(any(), any(), anyBoolean())).thenReturn(Optional.empty());

        task.evaluateTriggerConditions(LocalDate.now());

        verify(executionRepository, never()).save(any());
    }

    @Test
    void evaluateTriggerConditions_maType_callsCalculateMA() {
        Plan plan = newPlan(PlanStatus.PENDING, PlanCycle.DAILY, LocalDateTime.of(2026, 5, 20, 10, 0));
        addConds(plan, cond(TradeDirection.BUY, ConditionType.MA, null, true));
        KLineData kd = kLine("11.00");

        when(planRepository.findByStatusIn(any())).thenReturn(listOf(plan));
        when(tushareService.getDailyKLine(any(), any(), anyBoolean())).thenReturn(Optional.of(kd));
        when(tushareService.evaluateCondition(any(), any(), any())).thenReturn(true);
        when(tushareService.calculateMA(any(), anyInt(), any())).thenReturn(new BigDecimal("10.00"));
        when(executionRepository.save(any())).thenAnswer(inv -> { setField(inv.getArgument(0), "id", 1L); return inv.getArgument(0); });
        when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        task.evaluateTriggerConditions(LocalDate.now());

        verify(tushareService).calculateMA(eq("000001"), eq(5), any());
    }

    @Test
    void evaluateTriggerConditions_priceType_noCalculateMA() {
        Plan plan = newPlan(PlanStatus.PENDING, PlanCycle.DAILY, LocalDateTime.of(2026, 5, 20, 10, 0));
        addConds(plan, cond(TradeDirection.BUY, ConditionType.PRICE, new BigDecimal("11.00"), true));
        KLineData kd = kLine("11.00");

        when(planRepository.findByStatusIn(any())).thenReturn(listOf(plan));
        when(tushareService.getDailyKLine(any(), any(), anyBoolean())).thenReturn(Optional.of(kd));
        when(tushareService.evaluateCondition(any(), any(), any())).thenReturn(true);
        when(executionRepository.save(any())).thenAnswer(inv -> { setField(inv.getArgument(0), "id", 1L); return inv.getArgument(0); });
        when(planRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        task.evaluateTriggerConditions(LocalDate.now());

        verify(tushareService, never()).calculateMA(any(), anyInt(), any());
    }

    // 7.4 generatePlanSnapshots

    @Test
    void generatePlanSnapshots_pending_returnsZeroPL() {
        Plan plan = newPlan(PlanStatus.PENDING, PlanCycle.DAILY, LocalDateTime.now());
        KLineData kd = kLine("11.00");

        when(planRepository.findByStatusIn(any())).thenReturn(listOf(plan));
        when(actualTradeRepository.findByStockCode(any())).thenReturn(List.of());
        when(tushareService.getDailyKLine(any(), any(), anyBoolean())).thenReturn(Optional.of(kd));
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        task.generatePlanSnapshots(LocalDate.now());

        verify(snapshotRepository).save(argThat(s ->
                BigDecimal.ZERO.compareTo(s.getPlanReturnPercent()) == 0 &&
                BigDecimal.ZERO.compareTo(s.getPlanCashBalance().subtract(new BigDecimal("500000"))) == 0));
    }

    @Test
    void generatePlanSnapshots_holding_calculatesReturnUsingBaselineCapital() {
        Plan plan = newPlan(PlanStatus.HOLDING, PlanCycle.DAILY, LocalDateTime.now());
        PlanExecution buy = exec(TradeDirection.BUY, new BigDecimal("10.00"), LocalDate.of(2026, 5, 1), plan);
        KLineData kd = kLine("11.00");

        when(planRepository.findByStatusIn(any())).thenReturn(listOf(plan));
        when(actualTradeRepository.findByStockCode(any())).thenReturn(List.of());
        when(executionRepository.findByPlanId(plan.getId())).thenReturn(listOf(buy));
        when(tushareService.getDailyKLine(any(), any(), anyBoolean())).thenReturn(Optional.of(kd));
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        task.generatePlanSnapshots(LocalDate.now());

        verify(snapshotRepository).save(argThat(s ->
                BigDecimal.ZERO.compareTo(s.getPlanCashBalance().subtract(new BigDecimal("500000"))) == 0 &&
                BigDecimal.ZERO.compareTo(s.getPlanMarketValue().subtract(new BigDecimal("1100"))) == 0 &&
                BigDecimal.ZERO.compareTo(s.getPlanTotalValue().subtract(new BigDecimal("501100"))) == 0 &&
                BigDecimal.ZERO.compareTo(s.getPlanReturnPercent().subtract(new BigDecimal("0.2200"))) == 0));
    }

    @Test
    void generatePlanSnapshots_includesVolatilityData() {
        Plan plan = newPlan(PlanStatus.PENDING, PlanCycle.DAILY, LocalDateTime.now());
        KLineData kd = new KLineData("000001", LocalDate.now(),
                new BigDecimal("10.5"), new BigDecimal("11.5"),
                new BigDecimal("10.5"), new BigDecimal("11.00"), 1_000_000);

        when(planRepository.findByStatusIn(any())).thenReturn(listOf(plan));
        when(actualTradeRepository.findByStockCode(any())).thenReturn(List.of());
        when(tushareService.getDailyKLine(any(), any(), anyBoolean())).thenReturn(Optional.of(kd));
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        task.generatePlanSnapshots(LocalDate.now());

        verify(snapshotRepository).save(argThat(s ->
                s.getHighPrice().compareTo(new BigDecimal("11.5")) == 0 &&
                s.getLowPrice().compareTo(new BigDecimal("10.5")) == 0 &&
                s.getClosePrice().compareTo(new BigDecimal("11.00")) == 0));
    }

    @Test
    void generatePlanSnapshots_hasActualTradeFlag() {
        Plan plan = newPlan(PlanStatus.PENDING, PlanCycle.DAILY, LocalDateTime.now());
        ActualTrade trade = mkTrade(1L);
        KLineData kd = kLine("11.00");

        when(planRepository.findByStatusIn(any())).thenReturn(listOf(plan));
        when(actualTradeRepository.findByStockCode("000001")).thenReturn(listOf(trade));
        when(tushareService.getDailyKLine(any(), any(), anyBoolean())).thenReturn(Optional.of(kd));
        when(snapshotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        task.generatePlanSnapshots(LocalDate.now());

        verify(snapshotRepository).save(argThat(s -> Boolean.TRUE.equals(s.getHasActualTrade())));
    }

    @Test
    void generatePlanSnapshots_noKLine_skips() {
        Plan plan = newPlan(PlanStatus.PENDING, PlanCycle.DAILY, LocalDateTime.now());

        when(planRepository.findByStatusIn(any())).thenReturn(listOf(plan));
        when(tushareService.getDailyKLine(any(), any(), anyBoolean())).thenReturn(Optional.empty());

        task.generatePlanSnapshots(LocalDate.now());

        verify(snapshotRepository, never()).save(any());
    }

    // 7.5 generateActualTradeSnapshots

    @Test
    void generateActualTradeSnapshots_generatesOnePerTrade() {
        when(actualTradeRepository.findAll()).thenReturn(listOf(mkTrade(1L), mkTrade(2L), mkTrade(3L)));
        when(actualTradeRepository.findUnmatchedBuys(any())).thenReturn(List.of());
        when(tushareService.getDailyKLine(any(), any(), anyBoolean())).thenReturn(Optional.of(kLine("11.00")));

        task.generateActualTradeSnapshots(LocalDate.now());

        verify(snapshotRepository, times(3)).save(any());
    }

    @Test
    void generateActualTradeSnapshots_includesTradeData() {
        ActualTrade t = ActualTrade.builder()
                .stockCode("000001").stockName("平安银行")
                .direction(TradeDirection.BUY)
                .price(new BigDecimal("10.00")).quantity(new BigDecimal("100"))
                .tradeDate(LocalDate.now())
                .build();
        setField(t, "id", 1L);

        when(actualTradeRepository.findAll()).thenReturn(listOf(t));
        when(actualTradeRepository.findUnmatchedBuys(any())).thenReturn(List.of(t));
        when(tushareService.getDailyKLine(any(), any(), anyBoolean())).thenReturn(Optional.of(kLine("11.00")));

        task.generateActualTradeSnapshots(LocalDate.now());

        verify(snapshotRepository).save(argThat(s ->
                "000001".equals(s.getStockCode()) &&
                "平安银行".equals(s.getStockName()) &&
                s.getClosePrice() != null));
    }

    @Test
    void generateActualTradeSnapshots_noKLine_skips() {
        ActualTrade t = mkTrade(1L);
        when(actualTradeRepository.findAll()).thenReturn(listOf(t));
        when(actualTradeRepository.findUnmatchedBuys(any())).thenReturn(List.of());
        when(tushareService.getDailyKLine(any(), any(), anyBoolean())).thenReturn(Optional.empty());

        task.generateActualTradeSnapshots(LocalDate.now());

        verify(snapshotRepository, never()).save(any());
    }

    // --- Factory helpers ---

    private Plan newPlan(PlanStatus status, PlanCycle cycle, LocalDateTime createdAt) {
        Plan p = Plan.builder()
                .name("测试预案")
                .stockCode("000001")
                .stockName("平安银行")
                .cycle(cycle)
                .status(status)
                .isLocked(false)
                .executionQuantity(new BigDecimal("100"))
                .build();
        setField(p, "id", 1L);
        setField(p, "createdAt", createdAt);
        setField(p, "updatedAt", createdAt);
        setField(p, "conditions", new ArrayList<>());
        setField(p, "executions", new ArrayList<>());
        return p;
    }

    private void addConds(Plan plan, PlanCondition... conds) {
        for (PlanCondition c : conds) {
            plan.getConditions().add(c);
        }
    }

    private PlanCondition cond(TradeDirection direction, ConditionType type,
                             BigDecimal targetPrice, boolean active) {
        PlanCondition c = PlanCondition.builder()
                .conditionType(type)
                .direction(direction)
                .maPeriod(5)
                .targetPrice(targetPrice)
                .isActive(active)
                .build();
        setField(c, "id", 1L);
        return c;
    }

    private PlanExecution exec(TradeDirection direction, BigDecimal triggerPrice,
                            LocalDate tradeDate, Plan plan) {
        PlanExecution e = PlanExecution.builder()
                .tradeDate(tradeDate)
                .direction(direction)
                .triggerPrice(triggerPrice)
                .triggered(true).executed(true)
                .build();
        setField(e, "id", 1L);
        setField(e, "plan", plan);
        return e;
    }

    private KLineData kLine(String close) {
        return new KLineData("000001", LocalDate.now(),
                new BigDecimal("10.5"), new BigDecimal("11.5"),
                new BigDecimal("10.5"), new BigDecimal(close), 1_000_000);
    }

    private ActualTrade mkTrade(long id) {
        ActualTrade t = ActualTrade.builder()
                .stockCode("000001").stockName("平安银行")
                .direction(TradeDirection.BUY)
                .price(new BigDecimal("10.00")).quantity(new BigDecimal("100"))
                .tradeDate(LocalDate.now())
                .build();
        setField(t, "id", id);
        return t;
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
