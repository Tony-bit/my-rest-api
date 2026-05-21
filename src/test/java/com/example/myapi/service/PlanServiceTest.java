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

    @Mock
    private PlanRepository planRepository;

    private PlanService service;

    @BeforeEach
    void setUp() {
        service = new PlanService(planRepository);
    }

    // 2.1 create

    @Test
    void create_withConditions_savesPlan() {
        ConditionDTO.CreateRequest condReq = ConditionDTO.CreateRequest.builder()
                .conditionType(ConditionType.PRICE)
                .direction(TradeDirection.BUY)
                .targetPrice(new BigDecimal("10.00"))
                .build();
        ConditionDTO.CreateRequest condReq2 = ConditionDTO.CreateRequest.builder()
                .conditionType(ConditionType.MA)
                .direction(TradeDirection.SELL)
                .maPeriod(5)
                .build();

        PlanDTO.CreateRequest request = PlanDTO.CreateRequest.builder()
                .name("测试预案")
                .stockCode("000001")
                .cycle(PlanCycle.DAILY)
                .conditions(Arrays.asList(condReq, condReq2))
                .build();

        when(planRepository.save(any(Plan.class))).thenAnswer(inv -> {
            Plan p = inv.getArgument(0);
            setField(p, "id", 1L);
            return p;
        });

        PlanDTO.Response resp = service.create(request);

        assertNotNull(resp.getId());
        assertEquals(2, resp.getConditions().size());
        verify(planRepository, times(1)).save(any(Plan.class));
    }

    @Test
    void create_withoutConditions_succeeds() {
        PlanDTO.CreateRequest request = PlanDTO.CreateRequest.builder()
                .name("无条件预案")
                .stockCode("000001")
                .cycle(PlanCycle.DAILY)
                .build();

        when(planRepository.save(any(Plan.class))).thenAnswer(inv -> {
            Plan p = inv.getArgument(0);
            setField(p, "id", 1L);
            return p;
        });

        PlanDTO.Response resp = service.create(request);
        assertEquals(0, resp.getConditions().size());
    }

    @Test
    void create_invalidMaPeriod_throws() {
        ConditionDTO.CreateRequest condReq = ConditionDTO.CreateRequest.builder()
                .conditionType(ConditionType.MA)
                .direction(TradeDirection.BUY)
                .maPeriod(7) // invalid
                .build();
        PlanDTO.CreateRequest request = PlanDTO.CreateRequest.builder()
                .name("测试")
                .stockCode("000001")
                .cycle(PlanCycle.DAILY)
                .conditions(List.of(condReq))
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(request));
        assertTrue(ex.getMessage().contains("MA"));
    }

    @Test
    void create_priceZeroOrNegative_throws() {
        ConditionDTO.CreateRequest condReq = ConditionDTO.CreateRequest.builder()
                .conditionType(ConditionType.PRICE)
                .direction(TradeDirection.BUY)
                .targetPrice(BigDecimal.ZERO)
                .build();
        PlanDTO.CreateRequest request = PlanDTO.CreateRequest.builder()
                .name("测试")
                .stockCode("000001")
                .cycle(PlanCycle.DAILY)
                .conditions(List.of(condReq))
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(request));
        assertTrue(ex.getMessage().contains("大于 0"));
    }

    @Test
    void create_priceTargetNull_throws() {
        ConditionDTO.CreateRequest condReq = ConditionDTO.CreateRequest.builder()
                .conditionType(ConditionType.PRICE)
                .direction(TradeDirection.BUY)
                .targetPrice(null)
                .build();
        PlanDTO.CreateRequest request = PlanDTO.CreateRequest.builder()
                .name("测试")
                .stockCode("000001")
                .cycle(PlanCycle.DAILY)
                .conditions(List.of(condReq))
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(request));
        assertTrue(ex.getMessage().contains("大于 0"));
    }

    @Test
    void create_conditionTypeNull_throws() {
        ConditionDTO.CreateRequest condReq = ConditionDTO.CreateRequest.builder()
                .conditionType(null)
                .direction(TradeDirection.BUY)
                .targetPrice(new BigDecimal("10.00"))
                .build();
        PlanDTO.CreateRequest request = PlanDTO.CreateRequest.builder()
                .name("测试")
                .stockCode("000001")
                .cycle(PlanCycle.DAILY)
                .conditions(List.of(condReq))
                .build();

        assertThrows(BusinessException.class, () -> service.create(request));
    }

    @Test
    void create_directionNull_throws() {
        ConditionDTO.CreateRequest condReq = ConditionDTO.CreateRequest.builder()
                .conditionType(ConditionType.PRICE)
                .direction(null)
                .targetPrice(new BigDecimal("10.00"))
                .build();
        PlanDTO.CreateRequest request = PlanDTO.CreateRequest.builder()
                .name("测试")
                .stockCode("000001")
                .cycle(PlanCycle.DAILY)
                .conditions(List.of(condReq))
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(request));
        assertTrue(ex.getMessage().contains("direction"));
    }

    // 2.2 update

    @Test
    void update_pendingPlan_succeeds() {
        Plan plan = TestFixtures.planBuilder().status(PlanStatus.PENDING).build();
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(planRepository.save(any(Plan.class))).thenReturn(plan);

        PlanDTO.UpdateRequest request = PlanDTO.UpdateRequest.builder()
                .name("新名称")
                .build();

        PlanDTO.Response resp = service.update(1L, request);
        verify(planRepository, atLeastOnce()).save(any(Plan.class));
    }

    @Test
    void update_holdingPlan_throws409() {
        Plan plan = TestFixtures.planBuilder().status(PlanStatus.HOLDING).build();
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));

        PlanDTO.UpdateRequest request = PlanDTO.UpdateRequest.builder()
                .name("新名称")
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.update(1L, request));
        assertEquals(409, ex.getStatusCode());
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
    void update_partialFields_onlyUpdatesNonNull() {
        Plan plan = TestFixtures.planBuilder().name("原名称").cycle(PlanCycle.DAILY).build();
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(planRepository.save(any(Plan.class))).thenReturn(plan);

        PlanDTO.UpdateRequest request = PlanDTO.UpdateRequest.builder()
                .name("新名称")
                .build();

        service.update(1L, request);
        assertEquals("新名称", plan.getName());
        assertEquals(PlanCycle.DAILY, plan.getCycle());
    }

    @Test
    void update_cycleChanged_succeeds() {
        Plan plan = TestFixtures.planBuilder().cycle(PlanCycle.DAILY).build();
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(planRepository.save(any(Plan.class))).thenReturn(plan);

        PlanDTO.UpdateRequest request = PlanDTO.UpdateRequest.builder()
                .cycle(PlanCycle.WEEKLY)
                .build();

        service.update(1L, request);
        assertEquals(PlanCycle.WEEKLY, plan.getCycle());
    }

    // 2.3 delete

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
        verify(planRepository, never()).delete(any());
    }

    @Test
    void delete_closedPlan_throws409() {
        Plan plan = TestFixtures.planBuilder().status(PlanStatus.CLOSED).build();
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(1L));
        assertEquals(409, ex.getStatusCode());
    }

    @Test
    void delete_notFound_throws404() {
        when(planRepository.findById(999L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(999L));
        assertEquals(404, ex.getStatusCode());
    }

    // 2.4 list

    @Test
    void list_noFilters_callsFindAll() {
        when(planRepository.findAll()).thenReturn(List.of());
        service.list(null, null);
        verify(planRepository).findAll();
    }

    @Test
    void list_statusFilter_callsFindByStatus() {
        when(planRepository.findByStatus(PlanStatus.PENDING)).thenReturn(List.of());
        service.list(PlanStatus.PENDING, null);
        verify(planRepository).findByStatus(PlanStatus.PENDING);
    }

    @Test
    void list_stockCodeFilter_callsFindByStockCode() {
        when(planRepository.findByStockCode("000001")).thenReturn(List.of());
        service.list(null, "000001");
        verify(planRepository).findByStockCode("000001");
    }

    @Test
    void list_bothFilters_callsFindByStatusAndStockCode() {
        when(planRepository.findByStatusAndStockCode(PlanStatus.PENDING, "000001")).thenReturn(List.of());
        service.list(PlanStatus.PENDING, "000001");
        verify(planRepository).findByStatusAndStockCode(PlanStatus.PENDING, "000001");
    }

    // 2.5 findActivePlans

    @Test
    void findActivePlans_returnsPendingAndHolding() {
        when(planRepository.findByStatusIn(anyList())).thenReturn(List.of());
        service.findActivePlans();
        verify(planRepository).findByStatusIn(Arrays.asList(PlanStatus.PENDING, PlanStatus.HOLDING));
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
