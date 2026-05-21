package com.example.myapi.service;

import com.example.myapi.entity.*;
import com.example.myapi.repository.PlanExecutionRepository;
import com.example.myapi.repository.PlanRepository;
import com.example.myapi.service.TushareService.KLineData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ManualTriggerServiceTest {

    @Mock private PlanRepository planRepository;
    @Mock private PlanExecutionRepository executionRepository;
    @Mock private TushareService tushareService;
    @Mock private PlanExecutionService executionService;
    @Mock private SystemConfigService systemConfigService;

    private ManualTriggerService service;

    @BeforeEach
    void setUp() {
        service = new ManualTriggerService(
                planRepository, executionRepository,
                tushareService, executionService, systemConfigService);
    }

    // ==================== Group 1: Normal Execution ====================

    @Test
    void triggerPlans_normalExecution_triggersPlan() {
        LocalDate targetDate = LocalDate.of(2026, 5, 15);
        Plan plan = TestFixtures.planBuilder()
                .id(1L)
                .triggerDate(targetDate)
                .status(PlanStatus.PENDING)
                .build();
        addDefaultCondition(plan);
        KLineData kLine = TestFixtures.kLineBuilder()
                .close(new BigDecimal("11.00"))
                .build();

        when(planRepository.findByTriggerDateAndStatus(targetDate, PlanStatus.PENDING))
                .thenReturn(List.of(plan));
        when(executionRepository.existsByPlanIdAndExecutedTrue(1L)).thenReturn(false);
        when(tushareService.getDailyKLine(eq("000001"), eq(targetDate), anyBoolean()))
                .thenReturn(Optional.of(kLine));
        when(tushareService.evaluateCondition(any(), any(), any())).thenReturn(true);
        when(executionService.recordExecution(any(), any(), any(), any(), any(), any()))
                .thenReturn(TestFixtures.executionBuilder().build());
        doAnswer(inv -> {
            Plan p = inv.getArgument(0);
            p.setStatus((PlanStatus) inv.getArgument(1));
            return null;
        }).when(executionService).transitionState(any(), any());

        ManualTriggerService.TriggerResult result = service.triggerPlans(targetDate);

        assertEquals(1, result.totalPlans());
        assertEquals(1, result.triggered());
        assertEquals(0, result.skipped());
        assertEquals(1, result.details().size());
        assertEquals("triggered", result.details().get(0).status());
    }

    @Test
    void triggerPlans_emptyResult_returnsZeroCounts() {
        LocalDate targetDate = LocalDate.of(2026, 5, 15);

        when(planRepository.findByTriggerDateAndStatus(targetDate, PlanStatus.PENDING))
                .thenReturn(List.of());

        ManualTriggerService.TriggerResult result = service.triggerPlans(targetDate);

        assertEquals(0, result.totalPlans());
        assertEquals(0, result.triggered());
        assertEquals(0, result.skipped());
        assertTrue(result.details().isEmpty());
    }

    @Test
    void triggerPlans_multiplePlans_mixedResults() {
        LocalDate targetDate = LocalDate.of(2026, 5, 15);
        Plan plan1 = TestFixtures.planBuilder().id(1L).triggerDate(targetDate).status(PlanStatus.PENDING).build();
        Plan plan2 = TestFixtures.planBuilder().id(2L).triggerDate(targetDate).status(PlanStatus.PENDING).build();
        Plan plan3 = TestFixtures.planBuilder().id(3L).triggerDate(targetDate).status(PlanStatus.PENDING).build();
        addDefaultCondition(plan1);
        addDefaultCondition(plan2);
        addDefaultCondition(plan3);
        KLineData kLine = TestFixtures.kLineBuilder().close(new BigDecimal("11.00")).build();

        when(planRepository.findByTriggerDateAndStatus(targetDate, PlanStatus.PENDING))
                .thenReturn(List.of(plan1, plan2, plan3));
        when(executionRepository.existsByPlanIdAndExecutedTrue(anyLong())).thenReturn(false);
        when(tushareService.getDailyKLine(any(), any(), anyBoolean()))
                .thenReturn(Optional.of(kLine));
        when(tushareService.evaluateCondition(any(), any(), any()))
                .thenReturn(true, false, false); // plan1=true, plan2=false, plan3=false
        when(executionService.recordExecution(any(), any(), any(), any(), any(), any()))
                .thenReturn(TestFixtures.executionBuilder().build());
        doAnswer(inv -> {
            Plan p = inv.getArgument(0);
            p.setStatus((PlanStatus) inv.getArgument(1));
            return null;
        }).when(executionService).transitionState(any(), any());

        ManualTriggerService.TriggerResult result = service.triggerPlans(targetDate);

        assertEquals(3, result.totalPlans());
        assertEquals(1, result.triggered());
        assertEquals(2, result.skipped());
    }

    @Test
    void triggerPlans_updatesPlanStatusToHolding() {
        LocalDate targetDate = LocalDate.of(2026, 5, 15);
        Plan plan = TestFixtures.planBuilder()
                .id(1L)
                .triggerDate(targetDate)
                .status(PlanStatus.PENDING)
                .build();
        addDefaultCondition(plan);
        KLineData kLine = TestFixtures.kLineBuilder().close(new BigDecimal("11.00")).build();

        when(planRepository.findByTriggerDateAndStatus(targetDate, PlanStatus.PENDING))
                .thenReturn(List.of(plan));
        when(executionRepository.existsByPlanIdAndExecutedTrue(1L)).thenReturn(false);
        when(tushareService.getDailyKLine(any(), any(), anyBoolean()))
                .thenReturn(Optional.of(kLine));
        when(tushareService.evaluateCondition(any(), any(), any())).thenReturn(true);
        when(executionService.recordExecution(any(), any(), any(), any(), any(), any()))
                .thenReturn(TestFixtures.executionBuilder().build());
        doAnswer(inv -> {
            Plan p = inv.getArgument(0);
            p.setStatus((PlanStatus) inv.getArgument(1));
            return null;
        }).when(executionService).transitionState(any(), any());

        service.triggerPlans(targetDate);

        verify(executionService).transitionState(plan, PlanStatus.HOLDING);
    }

    @Test
    void triggerPlans_createsPlanExecution() {
        LocalDate targetDate = LocalDate.of(2026, 5, 15);
        Plan plan = TestFixtures.planBuilder()
                .id(1L)
                .triggerDate(targetDate)
                .status(PlanStatus.PENDING)
                .build();
        addDefaultCondition(plan);
        KLineData kLine = TestFixtures.kLineBuilder().close(new BigDecimal("11.00")).build();

        when(planRepository.findByTriggerDateAndStatus(targetDate, PlanStatus.PENDING))
                .thenReturn(List.of(plan));
        when(executionRepository.existsByPlanIdAndExecutedTrue(1L)).thenReturn(false);
        when(tushareService.getDailyKLine(any(), any(), anyBoolean()))
                .thenReturn(Optional.of(kLine));
        when(tushareService.evaluateCondition(any(), any(), any())).thenReturn(true);
        when(executionService.recordExecution(any(), any(), any(), any(), any(), any()))
                .thenReturn(TestFixtures.executionBuilder().build());
        doAnswer(inv -> {
            Plan p = inv.getArgument(0);
            p.setStatus((PlanStatus) inv.getArgument(1));
            return null;
        }).when(executionService).transitionState(any(), any());

        service.triggerPlans(targetDate);

        verify(executionService).recordExecution(
                eq(plan), eq(TradeDirection.BUY), eq(new BigDecimal("11.00")),
                eq(new BigDecimal("11.00")), isNull(), isNull());
    }

    @Test
    void triggerPlans_fetchesKLineForTargetDate() {
        LocalDate targetDate = LocalDate.of(2026, 5, 15);
        Plan plan = TestFixtures.planBuilder()
                .id(1L)
                .triggerDate(targetDate)
                .status(PlanStatus.PENDING)
                .build();
        addDefaultCondition(plan);
        KLineData kLine = TestFixtures.kLineBuilder().date(targetDate).close(new BigDecimal("11.00")).build();

        when(planRepository.findByTriggerDateAndStatus(targetDate, PlanStatus.PENDING))
                .thenReturn(List.of(plan));
        when(executionRepository.existsByPlanIdAndExecutedTrue(1L)).thenReturn(false);
        when(tushareService.getDailyKLine(eq("000001"), eq(targetDate), anyBoolean()))
                .thenReturn(Optional.of(kLine));
        when(tushareService.evaluateCondition(any(), any(), any())).thenReturn(true);
        when(executionService.recordExecution(any(), any(), any(), any(), any(), any()))
                .thenReturn(TestFixtures.executionBuilder().build());
        doAnswer(inv -> {
            Plan p = inv.getArgument(0);
            p.setStatus((PlanStatus) inv.getArgument(1));
            return null;
        }).when(executionService).transitionState(any(), any());

        service.triggerPlans(targetDate);

        verify(tushareService).getDailyKLine(eq("000001"), eq(targetDate), anyBoolean());
    }

    // ==================== Group 2: Idempotency (Task 6.3) ====================

    @Test
    void triggerPlans_alreadyExecuted_skipsPlan() {
        LocalDate targetDate = LocalDate.of(2026, 5, 15);
        Plan plan = TestFixtures.planBuilder()
                .id(1L)
                .triggerDate(targetDate)
                .status(PlanStatus.PENDING)
                .build();

        when(planRepository.findByTriggerDateAndStatus(targetDate, PlanStatus.PENDING))
                .thenReturn(List.of(plan));
        when(executionRepository.existsByPlanIdAndExecutedTrue(1L)).thenReturn(true);

        ManualTriggerService.TriggerResult result = service.triggerPlans(targetDate);

        assertEquals(1, result.totalPlans());
        assertEquals(0, result.triggered());
        assertEquals(1, result.skipped());
        assertEquals("already_executed", result.details().get(0).status());
        verify(tushareService, never()).getDailyKLine(any(), any(), anyBoolean());
        verify(executionService, never()).recordExecution(any(), any(), any(), any(), any(), any());
    }

    @Test
    void triggerPlans_executedFalse_canTrigger() {
        LocalDate targetDate = LocalDate.of(2026, 5, 15);
        Plan plan = TestFixtures.planBuilder()
                .id(1L)
                .triggerDate(targetDate)
                .status(PlanStatus.PENDING)
                .build();
        addDefaultCondition(plan);
        KLineData kLine = TestFixtures.kLineBuilder().close(new BigDecimal("11.00")).build();

        when(planRepository.findByTriggerDateAndStatus(targetDate, PlanStatus.PENDING))
                .thenReturn(List.of(plan));
        when(executionRepository.existsByPlanIdAndExecutedTrue(1L)).thenReturn(false);
        when(tushareService.getDailyKLine(any(), any(), anyBoolean()))
                .thenReturn(Optional.of(kLine));
        when(tushareService.evaluateCondition(any(), any(), any())).thenReturn(true);
        when(executionService.recordExecution(any(), any(), any(), any(), any(), any()))
                .thenReturn(TestFixtures.executionBuilder().build());
        doAnswer(inv -> {
            Plan p = inv.getArgument(0);
            p.setStatus((PlanStatus) inv.getArgument(1));
            return null;
        }).when(executionService).transitionState(any(), any());

        ManualTriggerService.TriggerResult result = service.triggerPlans(targetDate);

        assertEquals("triggered", result.details().get(0).status());
        verify(executionService).recordExecution(any(), any(), any(), any(), any(), any());
    }

    @Test
    void triggerPlans_duplicateTrigger_callTwice() {
        LocalDate targetDate = LocalDate.of(2026, 5, 15);
        Plan plan = TestFixtures.planBuilder()
                .id(1L)
                .triggerDate(targetDate)
                .status(PlanStatus.PENDING)
                .build();
        addDefaultCondition(plan);
        KLineData kLine = TestFixtures.kLineBuilder().close(new BigDecimal("11.00")).build();

        when(planRepository.findByTriggerDateAndStatus(targetDate, PlanStatus.PENDING))
                .thenReturn(List.of(plan));
        when(executionRepository.existsByPlanIdAndExecutedTrue(1L))
                .thenReturn(false)  // first call
                .thenReturn(true);  // second call - now already executed
        when(tushareService.getDailyKLine(any(), any(), anyBoolean()))
                .thenReturn(Optional.of(kLine));
        when(tushareService.evaluateCondition(any(), any(), any())).thenReturn(true);
        when(executionService.recordExecution(any(), any(), any(), any(), any(), any()))
                .thenReturn(TestFixtures.executionBuilder().build());
        doAnswer(inv -> {
            Plan p = inv.getArgument(0);
            p.setStatus((PlanStatus) inv.getArgument(1));
            return null;
        }).when(executionService).transitionState(any(), any());

        // First trigger
        ManualTriggerService.TriggerResult result1 = service.triggerPlans(targetDate);
        assertEquals(1, result1.triggered());

        // Second trigger (idempotency)
        ManualTriggerService.TriggerResult result2 = service.triggerPlans(targetDate);
        assertEquals(0, result2.triggered());
        assertEquals(1, result2.skipped());
        assertEquals("already_executed", result2.details().get(0).status());
    }

    // ==================== Group 3: Data Unavailable ====================

    @Test
    void triggerPlans_noKLineData_marksDataUnavailable() {
        LocalDate targetDate = LocalDate.of(2026, 5, 15);
        Plan plan = TestFixtures.planBuilder()
                .id(1L)
                .triggerDate(targetDate)
                .status(PlanStatus.PENDING)
                .build();

        when(planRepository.findByTriggerDateAndStatus(targetDate, PlanStatus.PENDING))
                .thenReturn(List.of(plan));
        when(executionRepository.existsByPlanIdAndExecutedTrue(1L)).thenReturn(false);
        when(tushareService.getDailyKLine(any(), any(), anyBoolean())).thenReturn(Optional.empty());

        ManualTriggerService.TriggerResult result = service.triggerPlans(targetDate);

        assertEquals(1, result.totalPlans());
        assertEquals(0, result.triggered());
        assertEquals(1, result.skipped());
        assertEquals("data_unavailable", result.details().get(0).status());
    }

    @Test
    void triggerPlans_noKLineData_doesNotCreateExecution() {
        LocalDate targetDate = LocalDate.of(2026, 5, 15);
        Plan plan = TestFixtures.planBuilder()
                .id(1L)
                .triggerDate(targetDate)
                .status(PlanStatus.PENDING)
                .build();

        when(planRepository.findByTriggerDateAndStatus(targetDate, PlanStatus.PENDING))
                .thenReturn(List.of(plan));
        when(executionRepository.existsByPlanIdAndExecutedTrue(1L)).thenReturn(false);
        when(tushareService.getDailyKLine(any(), any(), anyBoolean())).thenReturn(Optional.empty());

        service.triggerPlans(targetDate);

        verify(executionService, never()).recordExecution(any(), any(), any(), any(), any(), any());
        verify(executionService, never()).transitionState(any(), any());
    }

    // ==================== Group 4: Condition Evaluation ====================

    @Test
    void triggerPlans_conditionNotMet_skipsPlan() {
        LocalDate targetDate = LocalDate.of(2026, 5, 15);
        Plan plan = TestFixtures.planBuilder()
                .id(1L)
                .triggerDate(targetDate)
                .status(PlanStatus.PENDING)
                .build();
        addDefaultCondition(plan);
        KLineData kLine = TestFixtures.kLineBuilder().close(new BigDecimal("9.00")).build(); // price below target

        when(planRepository.findByTriggerDateAndStatus(targetDate, PlanStatus.PENDING))
                .thenReturn(List.of(plan));
        when(executionRepository.existsByPlanIdAndExecutedTrue(1L)).thenReturn(false);
        when(tushareService.getDailyKLine(any(), any(), anyBoolean()))
                .thenReturn(Optional.of(kLine));
        when(tushareService.evaluateCondition(any(), any(), any())).thenReturn(false);

        ManualTriggerService.TriggerResult result = service.triggerPlans(targetDate);

        assertEquals(1, result.totalPlans());
        assertEquals(0, result.triggered());
        assertEquals(1, result.skipped());
        assertEquals("condition_not_met", result.details().get(0).status());
        verify(executionService, never()).recordExecution(any(), any(), any(), any(), any(), any());
    }

    @Test
    void triggerPlans_multipleConditions_allMet_triggers() {
        LocalDate targetDate = LocalDate.of(2026, 5, 15);
        Plan plan = TestFixtures.planBuilder()
                .id(1L)
                .triggerDate(targetDate)
                .status(PlanStatus.PENDING)
                .build();
        PlanCondition cond1 = TestFixtures.conditionBuilder()
                .conditionType(ConditionType.PRICE)
                .targetPrice(new BigDecimal("11.00"))
                .isActive(true)
                .build();
        PlanCondition cond2 = TestFixtures.conditionBuilder()
                .id(2L)
                .conditionType(ConditionType.MA)
                .maPeriod(5)
                .isActive(true)
                .build();
        setField(plan, "conditions", new ArrayList<>(List.of(cond1, cond2)));

        KLineData kLine = TestFixtures.kLineBuilder().close(new BigDecimal("11.00")).build();

        when(planRepository.findByTriggerDateAndStatus(targetDate, PlanStatus.PENDING))
                .thenReturn(List.of(plan));
        when(executionRepository.existsByPlanIdAndExecutedTrue(1L)).thenReturn(false);
        when(tushareService.getDailyKLine(any(), any(), anyBoolean()))
                .thenReturn(Optional.of(kLine));
        when(tushareService.evaluateCondition(any(), any(), any())).thenReturn(true);
        when(executionService.recordExecution(any(), any(), any(), any(), any(), any()))
                .thenReturn(TestFixtures.executionBuilder().build());
        doAnswer(inv -> {
            Plan p = inv.getArgument(0);
            p.setStatus((PlanStatus) inv.getArgument(1));
            return null;
        }).when(executionService).transitionState(any(), any());

        ManualTriggerService.TriggerResult result = service.triggerPlans(targetDate);

        assertEquals(1, result.triggered());
        verify(tushareService, times(2)).evaluateCondition(any(), any(), any());
    }

    @Test
    void triggerPlans_pendingStatusOnly_considered() {
        LocalDate targetDate = LocalDate.of(2026, 5, 15);
        Plan pendingPlan = TestFixtures.planBuilder()
                .id(1L)
                .triggerDate(targetDate)
                .status(PlanStatus.PENDING)
                .build();
        Plan holdingPlan = TestFixtures.planBuilder()
                .id(2L)
                .triggerDate(targetDate)
                .status(PlanStatus.HOLDING)
                .build();

        when(planRepository.findByTriggerDateAndStatus(targetDate, PlanStatus.PENDING))
                .thenReturn(List.of(pendingPlan));
        // Should NOT query execution for holding plan since it's not returned

        ManualTriggerService.TriggerResult result = service.triggerPlans(targetDate);

        assertEquals(1, result.totalPlans());
        verify(executionRepository, never()).existsByPlanIdAndExecutedTrue(2L);
    }

    // ==================== Utility ====================

    private void addDefaultCondition(Plan plan) {
        PlanCondition cond = TestFixtures.conditionBuilder()
                .conditionType(ConditionType.PRICE)
                .targetPrice(new BigDecimal("11.00"))
                .isActive(true)
                .build();
        setField(plan, "conditions", new ArrayList<>(List.of(cond)));
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception ignored) {}
    }
}
