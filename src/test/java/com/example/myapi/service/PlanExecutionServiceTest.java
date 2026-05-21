package com.example.myapi.service;

import com.example.myapi.entity.*;
import com.example.myapi.exception.BusinessException;
import com.example.myapi.repository.PlanExecutionRepository;
import com.example.myapi.repository.PlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlanExecutionServiceTest {

    @Mock
    private PlanExecutionRepository executionRepository;
    @Mock
    private PlanRepository planRepository;

    private PlanExecutionService service;

    @BeforeEach
    void setUp() {
        service = new PlanExecutionService(executionRepository, planRepository);
    }

    // 3.1 recordExecution

    @Test
    void recordExecution_normal_createsExecution() {
        Plan plan = TestFixtures.planBuilder().build();
        when(executionRepository.save(any(PlanExecution.class))).thenAnswer(inv -> {
            PlanExecution e = inv.getArgument(0);
            setField(e, "id", 1L);
            return e;
        });

        PlanExecution result = service.recordExecution(
                plan, TradeDirection.BUY,
                new BigDecimal("10.00"), new BigDecimal("10.05"),
                null, 1L);

        assertNotNull(result.getId());
        assertEquals(TradeDirection.BUY, result.getDirection());
        assertTrue(result.getTriggered());
        assertTrue(result.getExecuted());
        assertEquals(new BigDecimal("10.00"), result.getTriggerPrice());
        assertEquals(new BigDecimal("10.05"), result.getClosePrice());
        verify(executionRepository, times(1)).save(any(PlanExecution.class));
    }

    @Test
    void recordExecution_conditionIdNull_noCrash() {
        Plan plan = TestFixtures.planBuilder().build();
        when(executionRepository.save(any(PlanExecution.class))).thenAnswer(inv -> {
            PlanExecution e = inv.getArgument(0);
            setField(e, "id", 1L);
            return e;
        });

        PlanExecution result = service.recordExecution(
                plan, TradeDirection.BUY,
                new BigDecimal("10.00"), new BigDecimal("10.00"),
                null, null);

        assertNotNull(result);
        verify(executionRepository).save(any(PlanExecution.class));
    }

    @Test
    void recordExecution_conditionIdValid_associatesCondition() {
        Plan plan = TestFixtures.planBuilder().build();
        PlanCondition cond = TestFixtures.conditionBuilder().id(5L).plan(plan).build();
        setField(plan, "conditions", new ArrayList<>(List.of(cond)));

        when(executionRepository.save(any(PlanExecution.class))).thenAnswer(inv -> {
            PlanExecution e = inv.getArgument(0);
            setField(e, "id", 1L);
            return e;
        });

        PlanExecution result = service.recordExecution(
                plan, TradeDirection.BUY,
                new BigDecimal("10.00"), new BigDecimal("10.00"),
                null, 5L);

        assertNotNull(result.getCondition());
        assertEquals(5L, result.getCondition().getId());
    }

    // 3.2 transitionState

    @Test
    void transitionState_toHolding_setsStatusAndLocked() {
        Plan plan = TestFixtures.planBuilder()
                .status(PlanStatus.PENDING)
                .isLocked(false)
                .build();
        when(planRepository.save(any(Plan.class))).thenReturn(plan);

        service.transitionState(plan, PlanStatus.HOLDING);

        assertEquals(PlanStatus.HOLDING, plan.getStatus());
        assertTrue(plan.getIsLocked());
        verify(planRepository).save(plan);
    }

    @Test
    void transitionState_toClosed_onlyChangesStatus() {
        Plan plan = TestFixtures.planBuilder()
                .status(PlanStatus.HOLDING)
                .isLocked(true)
                .build();
        when(planRepository.save(any(Plan.class))).thenReturn(plan);

        service.transitionState(plan, PlanStatus.CLOSED);

        assertEquals(PlanStatus.CLOSED, plan.getStatus());
        assertTrue(plan.getIsLocked()); // stays true
        verify(planRepository).save(plan);
    }

    @Test
    void transitionState_alwaysSaves() {
        Plan plan = TestFixtures.planBuilder().status(PlanStatus.PENDING).build();
        when(planRepository.save(any(Plan.class))).thenReturn(plan);

        service.transitionState(plan, PlanStatus.HOLDING);
        verify(planRepository, times(1)).save(plan);
    }

    // 3.3 calculateCurrentReturn

    @Test
    void calculateReturn_positivePrice_returnsPositivePercent() {
        Plan plan = TestFixtures.planBuilder().build();
        PlanExecution buy = TestFixtures.executionBuilder()
                .plan(plan)
                .direction(TradeDirection.BUY)
                .triggerPrice(new BigDecimal("10.00"))
                .build();
        when(executionRepository.findByPlanId(plan.getId())).thenReturn(List.of(buy));

        BigDecimal result = service.calculateCurrentReturn(plan, new BigDecimal("11.00"));

        assertEquals(new BigDecimal("10.0000"), result);
    }

    @Test
    void calculateReturn_negativePrice_returnsNegativePercent() {
        Plan plan = TestFixtures.planBuilder().build();
        PlanExecution buy = TestFixtures.executionBuilder()
                .plan(plan)
                .direction(TradeDirection.BUY)
                .triggerPrice(new BigDecimal("10.00"))
                .build();
        when(executionRepository.findByPlanId(plan.getId())).thenReturn(List.of(buy));

        BigDecimal result = service.calculateCurrentReturn(plan, new BigDecimal("9.00"));

        assertEquals(new BigDecimal("-10.0000"), result);
    }

    @Test
    void calculateReturn_multipleBuys_usesAverageCost() {
        Plan plan = TestFixtures.planBuilder().build();

        Plan p1 = TestFixtures.planBuilder().build();
        PlanExecution buy1 = TestFixtures.executionBuilder().id(1L).plan(p1)
                .direction(TradeDirection.BUY).triggerPrice(new BigDecimal("10.00")).build();
        PlanExecution buy2 = TestFixtures.executionBuilder().id(2L).plan(p1)
                .direction(TradeDirection.BUY).triggerPrice(new BigDecimal("20.00")).build();

        when(executionRepository.findByPlanId(plan.getId())).thenReturn(List.of(buy1, buy2));

        BigDecimal result = service.calculateCurrentReturn(plan, new BigDecimal("15.00"));
        assertEquals(new BigDecimal("0.0000"), result); // avgCost=15, current=15 → 0%
    }

    @Test
    void calculateReturn_noBuyRecords_returnsZero() {
        Plan plan = TestFixtures.planBuilder().build();
        when(executionRepository.findByPlanId(plan.getId())).thenReturn(List.of());

        BigDecimal result = service.calculateCurrentReturn(plan, new BigDecimal("11.00"));
        assertEquals(BigDecimal.ZERO, result);
    }

    // 3.4 getByPlanId

    @Test
    void getByPlanId_notFound_throws404() {
        when(planRepository.existsById(999L)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.getByPlanId(999L));
        assertEquals(404, ex.getStatusCode());
    }

    @Test
    void getByPlanId_exists_returnsSortedExecutions() {
        when(planRepository.existsById(1L)).thenReturn(true);
        when(executionRepository.findByPlanIdOrderByTradeDateAsc(1L)).thenReturn(List.of());

        List<PlanExecution> result = service.getByPlanId(1L);

        verify(executionRepository).findByPlanIdOrderByTradeDateAsc(1L);
    }

    // Utility
    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception ignored) {}
    }
}
