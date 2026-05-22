package com.example.myapi.service;

import com.example.myapi.dto.ConditionDTO;
import com.example.myapi.dto.PlanDTO;
import com.example.myapi.entity.*;
import com.example.myapi.exception.BusinessException;
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
class PlanServiceTest {

    @Mock private PlanRepository planRepository;

    private PlanService service;

    @BeforeEach
    void setUp() {
        service = new PlanService(planRepository);
    }

    @Test
    void create_buyPlan_savesPlanWithPlanType() {
        ConditionDTO.CreateRequest condReq = ConditionDTO.CreateRequest.builder()
                .conditionType(ConditionType.PRICE)
                .targetPrice(new BigDecimal("10.00"))
                .build();
        PlanDTO.CreateRequest request = PlanDTO.CreateRequest.builder()
                .name("测试买入预案")
                .stockCode("000001")
                .cycle(PlanCycle.DAILY)
                .planType(PlanType.BUY)
                .condition(condReq)
                .build();

        when(planRepository.save(any(Plan.class))).thenAnswer(inv -> {
            Plan p = inv.getArgument(0);
            if (p.getId() == null) setField(p, "id", 2L);
            return p;
        });

        PlanDTO.Response resp = service.create(request);

        assertNotNull(resp.getId());
        assertEquals(PlanType.BUY, resp.getPlanType());
        assertNotNull(resp.getCondition());
    }

    @Test
    void create_buyPlan_setsTradePlanId() {
        PlanDTO.CreateRequest request = PlanDTO.CreateRequest.builder()
                .name("测试")
                .stockCode("000001")
                .cycle(PlanCycle.DAILY)
                .planType(PlanType.BUY)
                .build();

        when(planRepository.save(any(Plan.class))).thenAnswer(inv -> {
            Plan p = inv.getArgument(0);
            setField(p, "id", 5L);
            return p;
        });

        PlanDTO.Response resp = service.create(request);

        assertEquals(5L, resp.getTradePlanId());
    }

    @Test
    void create_withoutCondition_succeeds() {
        PlanDTO.CreateRequest request = PlanDTO.CreateRequest.builder()
                .name("无条件预案")
                .stockCode("000001")
                .cycle(PlanCycle.DAILY)
                .planType(PlanType.BUY)
                .build();

        when(planRepository.save(any(Plan.class))).thenAnswer(inv -> {
            Plan p = inv.getArgument(0);
            setField(p, "id", 1L);
            return p;
        });

        PlanDTO.Response resp = service.create(request);
        assertNull(resp.getCondition());
    }

    @Test
    void create_invalidMaPeriod_throws() {
        ConditionDTO.CreateRequest condReq = ConditionDTO.CreateRequest.builder()
                .conditionType(ConditionType.MA)
                .maPeriod(7)
                .build();
        PlanDTO.CreateRequest request = PlanDTO.CreateRequest.builder()
                .name("测试")
                .stockCode("000001")
                .cycle(PlanCycle.DAILY)
                .planType(PlanType.BUY)
                .condition(condReq)
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(request));
        assertTrue(ex.getMessage().contains("MA"));
    }

    @Test
    void create_priceZeroOrNegative_throws() {
        ConditionDTO.CreateRequest condReq = ConditionDTO.CreateRequest.builder()
                .conditionType(ConditionType.PRICE)
                .targetPrice(BigDecimal.ZERO)
                .build();
        PlanDTO.CreateRequest request = PlanDTO.CreateRequest.builder()
                .name("测试")
                .stockCode("000001")
                .cycle(PlanCycle.DAILY)
                .planType(PlanType.BUY)
                .condition(condReq)
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(request));
        assertTrue(ex.getMessage().contains("大于 0"));
    }

    @Test
    void createSellPlan_buyNotHolding_throws() {
        Plan buyPlan = TestFixtures.planBuilder()
                .id(1L)
                .planType(PlanType.BUY)
                .status(PlanStatus.PENDING)
                .build();

        when(planRepository.findById(1L)).thenReturn(Optional.of(buyPlan));

        ConditionDTO.CreateRequest condReq = ConditionDTO.CreateRequest.builder()
                .conditionType(ConditionType.PRICE)
                .targetPrice(new BigDecimal("11.00"))
                .build();
        PlanDTO.CreateSellPlanRequest request = PlanDTO.CreateSellPlanRequest.builder()
                .buyPlanId(1L)
                .name("测试卖出")
                .cycle(PlanCycle.DAILY)
                .condition(condReq)
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.createSellPlan(request));
        assertEquals(400, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("尚未建仓"));
    }

    @Test
    void createSellPlan_buyNotExist_throws404() {
        when(planRepository.findById(999L)).thenReturn(Optional.empty());

        PlanDTO.CreateSellPlanRequest request = PlanDTO.CreateSellPlanRequest.builder()
                .buyPlanId(999L)
                .name("测试卖出")
                .cycle(PlanCycle.DAILY)
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.createSellPlan(request));
        assertEquals(404, ex.getStatusCode());
    }

    @Test
    void createSellPlan_buyHolding_success() {
        Plan buyPlan = TestFixtures.planBuilder()
                .id(1L)
                .planType(PlanType.BUY)
                .status(PlanStatus.HOLDING)
                .stockCode("000001")
                .stockName("平安银行")
                .executionQuantity(new BigDecimal("100"))
                .cycle(PlanCycle.DAILY)
                .build();

        when(planRepository.findById(1L)).thenReturn(Optional.of(buyPlan));
        when(planRepository.findByPlanTypeAndStatusNot(eq(PlanType.SELL), any())).thenReturn(List.of());
        when(planRepository.save(any(Plan.class))).thenAnswer(inv -> {
            Plan p = inv.getArgument(0);
            setField(p, "id", 2L);
            return p;
        });

        ConditionDTO.CreateRequest condReq = ConditionDTO.CreateRequest.builder()
                .conditionType(ConditionType.PRICE)
                .targetPrice(new BigDecimal("11.00"))
                .build();
        PlanDTO.CreateSellPlanRequest request = PlanDTO.CreateSellPlanRequest.builder()
                .buyPlanId(1L)
                .name("测试卖出")
                .cycle(PlanCycle.DAILY)
                .condition(condReq)
                .build();

        PlanDTO.Response resp = service.createSellPlan(request);

        assertEquals(PlanType.SELL, resp.getPlanType());
        assertEquals(1L, resp.getTradePlanId());
        assertEquals(1L, resp.getBuyPlanId());
    }

    @Test
    void update_pendingPlan_succeeds() {
        Plan plan = TestFixtures.planBuilder().status(PlanStatus.PENDING).build();
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(planRepository.save(any(Plan.class))).thenReturn(plan);

        PlanDTO.Response resp = service.update(1L, PlanDTO.UpdateRequest.builder().name("新名称").build());
        assertEquals("新名称", plan.getName());
    }

    @Test
    void update_holdingPlan_succeeds() {
        Plan plan = TestFixtures.planBuilder().status(PlanStatus.HOLDING).build();
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(planRepository.save(any(Plan.class))).thenReturn(plan);

        PlanDTO.Response resp = service.update(1L, PlanDTO.UpdateRequest.builder().name("持仓中改名").build());
        assertEquals("持仓中改名", plan.getName());
    }

    @Test
    void update_closedPlan_throws409() {
        Plan plan = TestFixtures.planBuilder().status(PlanStatus.CLOSED).build();
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(1L, PlanDTO.UpdateRequest.builder().name("x").build()));
        assertEquals(409, ex.getStatusCode());
    }

    @Test
    void update_expiredPlan_throws409() {
        Plan plan = TestFixtures.planBuilder().status(PlanStatus.EXPIRED).build();
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(1L, PlanDTO.UpdateRequest.builder().name("x").build()));
        assertEquals(409, ex.getStatusCode());
    }

    @Test
    void update_pendingPlan_editsCondition_maToPrice() {
        Plan plan = TestFixtures.planBuilder().status(PlanStatus.PENDING).build();
        PlanCondition existingCond = TestFixtures.conditionBuilder()
                .plan(plan)
                .conditionType(ConditionType.MA)
                .maPeriod(20)
                .build();
        plan.getConditions().add(existingCond);
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(planRepository.save(any(Plan.class))).thenReturn(plan);

        ConditionDTO.CreateRequest newCond = ConditionDTO.CreateRequest.builder()
                .conditionType(ConditionType.PRICE)
                .targetPrice(new BigDecimal("12.00"))
                .build();
        PlanDTO.Response resp = service.update(1L, PlanDTO.UpdateRequest.builder().condition(newCond).build());

        assertEquals(ConditionType.PRICE, existingCond.getConditionType());
        assertNull(existingCond.getMaPeriod());
        assertEquals(new BigDecimal("12.00"), existingCond.getTargetPrice());
    }

    @Test
    void update_pendingPlan_editsCondition_priceToMa() {
        Plan plan = TestFixtures.planBuilder().status(PlanStatus.PENDING).build();
        PlanCondition existingCond = TestFixtures.conditionBuilder()
                .plan(plan)
                .conditionType(ConditionType.PRICE)
                .targetPrice(new BigDecimal("10.00"))
                .build();
        plan.getConditions().add(existingCond);
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(planRepository.save(any(Plan.class))).thenReturn(plan);

        ConditionDTO.CreateRequest newCond = ConditionDTO.CreateRequest.builder()
                .conditionType(ConditionType.MA)
                .maPeriod(10)
                .build();
        service.update(1L, PlanDTO.UpdateRequest.builder().condition(newCond).build());

        assertEquals(ConditionType.MA, existingCond.getConditionType());
        assertEquals(10, existingCond.getMaPeriod());
        assertNull(existingCond.getTargetPrice());
    }

    @Test
    void update_pendingPlan_createsCondition_whenNoneExists() {
        Plan plan = TestFixtures.planBuilder().status(PlanStatus.PENDING).build();
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(planRepository.save(any(Plan.class))).thenReturn(plan);

        ConditionDTO.CreateRequest newCond = ConditionDTO.CreateRequest.builder()
                .conditionType(ConditionType.MA)
                .maPeriod(60)
                .build();
        PlanDTO.Response resp = service.update(1L, PlanDTO.UpdateRequest.builder().condition(newCond).build());

        assertEquals(1, plan.getConditions().size());
        assertEquals(ConditionType.MA, plan.getConditions().get(0).getConditionType());
        assertEquals(60, plan.getConditions().get(0).getMaPeriod());
    }

    @Test
    void update_holdingPlan_editsCondition_succeeds() {
        Plan plan = TestFixtures.planBuilder().status(PlanStatus.HOLDING).build();
        PlanCondition existingCond = TestFixtures.conditionBuilder()
                .plan(plan)
                .conditionType(ConditionType.PRICE)
                .targetPrice(new BigDecimal("1800"))
                .build();
        plan.getConditions().add(existingCond);
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(planRepository.save(any(Plan.class))).thenReturn(plan);

        ConditionDTO.CreateRequest newCond = ConditionDTO.CreateRequest.builder()
                .conditionType(ConditionType.PRICE)
                .targetPrice(new BigDecimal("2000"))
                .build();
        PlanDTO.Response resp = service.update(1L, PlanDTO.UpdateRequest.builder().condition(newCond).build());

        assertEquals(new BigDecimal("2000"), existingCond.getTargetPrice());
    }

    @Test
    void update_invalidMaPeriod_throws() {
        Plan plan = TestFixtures.planBuilder().status(PlanStatus.PENDING).build();
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));

        ConditionDTO.CreateRequest invalidCond = ConditionDTO.CreateRequest.builder()
                .conditionType(ConditionType.MA)
                .maPeriod(7)
                .build();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(1L, PlanDTO.UpdateRequest.builder().condition(invalidCond).build()));
        assertTrue(ex.getMessage().contains("MA"));
    }

    @Test
    void delete_pendingPlan_succeeds() {
        Plan plan = TestFixtures.planBuilder().status(PlanStatus.PENDING).build();
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));

        service.delete(1L);
        verify(planRepository).delete(plan);
    }

    @Test
    void delete_expiredPlan_succeeds() {
        Plan plan = TestFixtures.planBuilder().status(PlanStatus.EXPIRED).build();
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));

        service.delete(1L);
        verify(planRepository).delete(plan);
    }

    @Test
    void delete_holdingPlan_throws409() {
        Plan plan = TestFixtures.planBuilder().status(PlanStatus.HOLDING).build();
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(1L));
        assertEquals(409, ex.getStatusCode());
    }

    @Test
    void list_noFilters_callsFindAll() {
        when(planRepository.findAll()).thenReturn(List.of());
        service.list(null, null, null);
        verify(planRepository).findAll();
    }

    @Test
    void list_statusFilter_callsFindByStatus() {
        when(planRepository.findByStatus(PlanStatus.PENDING)).thenReturn(List.of());
        service.list(PlanStatus.PENDING, null, null);
        verify(planRepository).findByStatus(PlanStatus.PENDING);
    }

    @Test
    void list_tradePlanId_callsFindByTradePlanId() {
        when(planRepository.findByTradePlanId(5L)).thenReturn(List.of());
        service.list(null, null, 5L);
        verify(planRepository).findByTradePlanId(5L);
    }

    @Test
    void findActivePlans_returnsPendingAndHolding() {
        when(planRepository.findByStatusIn(anyList())).thenReturn(List.of());
        service.findActivePlans();
        verify(planRepository).findByStatusIn(List.of(PlanStatus.PENDING, PlanStatus.HOLDING));
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception ignored) {}
    }
}
