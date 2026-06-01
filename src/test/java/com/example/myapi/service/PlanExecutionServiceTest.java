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

    @Mock private PlanExecutionRepository executionRepository;
    @Mock private PlanRepository planRepository;
    @Mock private SystemConfigService systemConfigService;
    @Mock private PlanAccount planAccount;

    private PlanExecutionService service;

    @BeforeEach
    void setUp() {
        service = new PlanExecutionService(executionRepository, planRepository, systemConfigService);
    }

    @Test
    void recordExecution_buy_createsExecutionAndDeductsCash() {
        Plan buyPlan = TestFixtures.planBuilder()
                .planType(PlanType.BUY)
                .executionQuantity(new BigDecimal("100"))
                .build();
        when(systemConfigService.getPlanAccount()).thenReturn(planAccount);
        when(planAccount.getCashBalance()).thenReturn(new BigDecimal("500000"));
        when(executionRepository.save(any(PlanExecution.class))).thenAnswer(inv -> {
            PlanExecution e = inv.getArgument(0);
            setField(e, "id", 1L);
            return e;
        });

        PlanExecution result = service.recordExecution(
                buyPlan,
                new BigDecimal("10.00"),
                new BigDecimal("10.05"),
                null,
                null);

        assertNotNull(result.getId());
        assertTrue(result.getTriggered());
        assertTrue(result.getExecuted());
        assertEquals(new BigDecimal("10.00"), result.getTriggerPrice());
        verify(systemConfigService).deductPlanCashBalance(new BigDecimal("1000.00"));
        verify(executionRepository).save(any(PlanExecution.class));
    }

    @Test
    void recordExecution_sell_createsExecutionAndAddsCash() {
        Plan sellPlan = TestFixtures.planBuilder()
                .planType(PlanType.SELL)
                .executionQuantity(new BigDecimal("100"))
                .build();
        when(executionRepository.save(any(PlanExecution.class))).thenAnswer(inv -> {
            PlanExecution e = inv.getArgument(0);
            setField(e, "id", 1L);
            return e;
        });

        service.recordExecution(sellPlan,
                new BigDecimal("11.00"),
                new BigDecimal("11.00"),
                null,
                null);

        verify(systemConfigService).addPlanCashBalance(new BigDecimal("1100.00"));
    }

    @Test
    void recordExecution_buyInsufficientCash_throwsException() {
        Plan buyPlan = TestFixtures.planBuilder()
                .planType(PlanType.BUY)
                .executionQuantity(new BigDecimal("100"))
                .build();
        when(systemConfigService.getPlanAccount()).thenReturn(planAccount);
        when(planAccount.getCashBalance()).thenReturn(new BigDecimal("500"));

        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.recordExecution(buyPlan,
                        new BigDecimal("10.00"),
                        new BigDecimal("10.00"),
                        null,
                        null));

        assertEquals(400, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("超过"));
        verify(systemConfigService, never()).deductPlanCashBalance(any());
        verify(executionRepository, never()).save(any());
    }

    @Test
    void transitionState_toHolding_setsStatus() {
        Plan plan = TestFixtures.planBuilder()
                .status(PlanStatus.PENDING)
                .build();
        when(planRepository.save(any(Plan.class))).thenReturn(plan);

        service.transitionState(plan, PlanStatus.HOLDING);

        assertEquals(PlanStatus.HOLDING, plan.getStatus());
        verify(planRepository).save(plan);
    }

    @Test
    void transitionState_toClosed_setsStatus() {
        Plan plan = TestFixtures.planBuilder()
                .status(PlanStatus.HOLDING)
                .build();
        when(planRepository.save(any(Plan.class))).thenReturn(plan);

        service.transitionState(plan, PlanStatus.CLOSED);

        assertEquals(PlanStatus.CLOSED, plan.getStatus());
        verify(planRepository).save(plan);
    }

    @Test
    void closeBuyPlan_closesLinkedBuyPlan() {
        Plan buyPlan = TestFixtures.planBuilder()
                .id(1L)
                .planType(PlanType.BUY)
                .status(PlanStatus.HOLDING)
                .build();
        Plan sellPlan = TestFixtures.planBuilder()
                .id(2L)
                .planType(PlanType.SELL)
                .buyPlan(buyPlan)
                .build();

        service.closeBuyPlan(sellPlan);

        assertEquals(PlanStatus.CLOSED, buyPlan.getStatus());
        verify(planRepository).save(buyPlan);
    }

    @Test
    void closeBuyPlan_buyPlanNotHolding_doesNothing() {
        Plan buyPlan = TestFixtures.planBuilder()
                .id(1L)
                .planType(PlanType.BUY)
                .status(PlanStatus.PENDING)
                .build();
        Plan sellPlan = TestFixtures.planBuilder()
                .id(2L)
                .planType(PlanType.SELL)
                .buyPlan(buyPlan)
                .build();

        service.closeBuyPlan(sellPlan);

        verify(planRepository, never()).save(any());
    }

    @Test
    void calculateReturn_buyPlanPositivePrice_returnsPositivePercent() {
        Plan buyPlan = TestFixtures.planBuilder()
                .id(1L)
                .planType(PlanType.BUY)
                .build();
        PlanExecution buyExec = TestFixtures.executionBuilder()
                .id(1L)
                .plan(buyPlan)
                .triggerPrice(new BigDecimal("10.00"))
                .build();
        when(executionRepository.findByPlanIdWithPlan(buyPlan.getId())).thenReturn(List.of(buyExec));

        BigDecimal result = service.calculateCurrentReturn(buyPlan, new BigDecimal("11.00"));

        assertEquals(new BigDecimal("10.0000"), result);
    }

    @Test
    void calculateReturn_noBuyRecords_returnsZero() {
        Plan buyPlan = TestFixtures.planBuilder().build();
        when(executionRepository.findByPlanIdWithPlan(buyPlan.getId())).thenReturn(List.of());

        BigDecimal result = service.calculateCurrentReturn(buyPlan, new BigDecimal("11.00"));

        assertEquals(BigDecimal.ZERO, result);
    }

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

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception ignored) {}
    }
}
