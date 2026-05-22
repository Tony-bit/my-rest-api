package com.example.myapi.service;

import com.example.myapi.entity.*;
import com.example.myapi.repository.ActualTradeRepository;
import com.example.myapi.repository.DailySnapshotRepository;
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
    @Mock private ActualTradeRepository actualTradeRepository;
    @Mock private DailySnapshotRepository snapshotRepository;
    @Mock private TushareService tushareService;
    @Mock private PlanExecutionService executionService;
    @Mock private SystemConfigService systemConfigService;

    private ManualTriggerService service;

    @BeforeEach
    void setUp() {
        service = new ManualTriggerService(
                planRepository, executionRepository,
                actualTradeRepository, snapshotRepository,
                tushareService, executionService, systemConfigService);
    }

    @Test
    void triggerPlans_normalBuyExecution_triggersPlan() {
        LocalDate targetDate = LocalDate.of(2026, 5, 15);
        Plan buyPlan = TestFixtures.planBuilder()
                .id(1L).planType(PlanType.BUY)
                .triggerDate(targetDate)
                .status(PlanStatus.PENDING)
                .build();
        addDefaultCondition(buyPlan);
        KLineData kLine = TestFixtures.kLineBuilder()
                .close(new BigDecimal("11.00"))
                .build();

        when(planRepository.findByTriggerDateAndStatus(targetDate, PlanStatus.PENDING))
                .thenReturn(List.of(buyPlan));
        when(executionRepository.existsByPlanIdAndExecutedTrue(1L)).thenReturn(false);
        when(tushareService.getDailyKLine(eq("000001"), eq(targetDate), anyBoolean()))
                .thenReturn(Optional.of(kLine));
        when(tushareService.evaluateCondition(any(), any(PlanType.class), any(), any())).thenReturn(true);
        when(executionService.recordExecution(any(), any(), any(), any(), any()))
                .thenReturn(TestFixtures.executionBuilder().build());
        doAnswer(inv -> {
            Plan p = inv.getArgument(0);
            p.setStatus((PlanStatus) inv.getArgument(1));
            return null;
        }).when(executionService).transitionState(any(), any());
        when(systemConfigService.getSystemConfig()).thenReturn(TestFixtures.systemConfigBuilder().build());
        when(systemConfigService.getPlanAccount()).thenReturn(TestFixtures.accountBuilder().build());

        ManualTriggerService.TriggerResult result = service.triggerPlans(targetDate);

        assertEquals(1, result.totalPlans());
        assertEquals(1, result.triggered());
        assertEquals(0, result.skipped());
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
    void triggerPlans_alreadyExecuted_skipsPlan() {
        LocalDate targetDate = LocalDate.of(2026, 5, 15);
        Plan buyPlan = TestFixtures.planBuilder()
                .id(1L).planType(PlanType.BUY)
                .triggerDate(targetDate)
                .status(PlanStatus.PENDING)
                .build();

        when(planRepository.findByTriggerDateAndStatus(targetDate, PlanStatus.PENDING))
                .thenReturn(List.of(buyPlan));
        when(executionRepository.existsByPlanIdAndExecutedTrue(1L)).thenReturn(true);

        ManualTriggerService.TriggerResult result = service.triggerPlans(targetDate);

        assertEquals(1, result.totalPlans());
        assertEquals(0, result.triggered());
        assertEquals(1, result.skipped());
        assertEquals("already_executed", result.details().get(0).status());
        verify(tushareService, never()).getDailyKLine(any(), any(), anyBoolean());
    }

    @Test
    void triggerPlans_noKLineData_marksDataUnavailable() {
        LocalDate targetDate = LocalDate.of(2026, 5, 15);
        Plan buyPlan = TestFixtures.planBuilder()
                .id(1L).planType(PlanType.BUY)
                .triggerDate(targetDate)
                .status(PlanStatus.PENDING)
                .build();

        when(planRepository.findByTriggerDateAndStatus(targetDate, PlanStatus.PENDING))
                .thenReturn(List.of(buyPlan));
        when(executionRepository.existsByPlanIdAndExecutedTrue(1L)).thenReturn(false);
        when(tushareService.getDailyKLine(any(), any(), anyBoolean())).thenReturn(Optional.empty());

        ManualTriggerService.TriggerResult result = service.triggerPlans(targetDate);

        assertEquals(1, result.totalPlans());
        assertEquals(0, result.triggered());
        assertEquals(1, result.skipped());
        assertEquals("data_unavailable", result.details().get(0).status());
    }

    @Test
    void triggerPlans_conditionNotMet_skipsPlan() {
        LocalDate targetDate = LocalDate.of(2026, 5, 15);
        Plan buyPlan = TestFixtures.planBuilder()
                .id(1L).planType(PlanType.BUY)
                .triggerDate(targetDate)
                .status(PlanStatus.PENDING)
                .build();
        addDefaultCondition(buyPlan);
        KLineData kLine = TestFixtures.kLineBuilder().close(new BigDecimal("9.00")).build();

        when(planRepository.findByTriggerDateAndStatus(targetDate, PlanStatus.PENDING))
                .thenReturn(List.of(buyPlan));
        when(executionRepository.existsByPlanIdAndExecutedTrue(1L)).thenReturn(false);
        when(tushareService.getDailyKLine(any(), any(), anyBoolean()))
                .thenReturn(Optional.of(kLine));
        when(tushareService.evaluateCondition(any(), any(PlanType.class), any(), any())).thenReturn(false);

        ManualTriggerService.TriggerResult result = service.triggerPlans(targetDate);

        assertEquals(1, result.totalPlans());
        assertEquals(0, result.triggered());
        assertEquals(1, result.skipped());
        assertEquals("condition_not_met", result.details().get(0).status());
    }

    @Test
    void triggerSingle_buyTriggered_transitionsToHolding() {
        LocalDate targetDate = LocalDate.of(2026, 5, 15);
        Plan buyPlan = TestFixtures.planBuilder()
                .id(1L).planType(PlanType.BUY)
                .triggerDate(targetDate)
                .status(PlanStatus.PENDING)
                .build();
        addDefaultCondition(buyPlan);
        KLineData kLine = TestFixtures.kLineBuilder().close(new BigDecimal("11.00")).build();

        when(planRepository.findById(1L)).thenReturn(Optional.of(buyPlan));
        when(executionRepository.existsByPlanIdAndExecutedTrue(1L)).thenReturn(false);
        when(tushareService.getDailyKLine(any(), any(), anyBoolean())).thenReturn(Optional.of(kLine));
        when(tushareService.evaluateCondition(any(), any(PlanType.class), any(), any())).thenReturn(true);
        when(executionService.recordExecution(any(), any(), any(), any(), any()))
                .thenReturn(TestFixtures.executionBuilder().build());
        doAnswer(inv -> {
            Plan p = inv.getArgument(0);
            p.setStatus((PlanStatus) inv.getArgument(1));
            return null;
        }).when(executionService).transitionState(any(), any());
        when(systemConfigService.getSystemConfig()).thenReturn(TestFixtures.systemConfigBuilder().build());
        when(systemConfigService.getPlanAccount()).thenReturn(TestFixtures.accountBuilder().build());

        ManualTriggerService.TriggerDetail result = service.triggerSingle(1L, null);

        assertEquals("triggered", result.status());
        verify(executionService).transitionState(buyPlan, PlanStatus.HOLDING);
    }

    private void addDefaultCondition(Plan plan) {
        PlanCondition cond = TestFixtures.conditionBuilder()
                .conditionType(ConditionType.PRICE)
                .targetPrice(new BigDecimal("11.00"))
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
