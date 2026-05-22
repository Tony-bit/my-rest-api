package com.example.myapi.service;

import com.example.myapi.dto.ConditionDTO;
import com.example.myapi.entity.*;
import com.example.myapi.exception.BusinessException;
import com.example.myapi.repository.PlanConditionRepository;
import com.example.myapi.repository.PlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlanConditionServiceTest {

    @Mock private PlanConditionRepository conditionRepository;
    @Mock private PlanRepository planRepository;

    private PlanConditionService service;

    @BeforeEach
    void setUp() {
        service = new PlanConditionService(conditionRepository, planRepository);
    }

    @Test
    void create_validMACondition_succeeds() {
        Plan plan = TestFixtures.planBuilder().status(PlanStatus.PENDING).build();
        ConditionDTO.CreateRequest request = ConditionDTO.CreateRequest.builder()
                .conditionType(ConditionType.MA)
                .maPeriod(20)
                .build();

        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(conditionRepository.save(any(PlanCondition.class))).thenAnswer(inv -> {
            PlanCondition c = inv.getArgument(0);
            setField(c, "id", 1L);
            return c;
        });

        ConditionDTO.Response resp = service.create(1L, request);

        assertNotNull(resp.getId());
        assertEquals(ConditionType.MA, resp.getConditionType());
        assertEquals(20, resp.getMaPeriod());
        verify(conditionRepository).save(any(PlanCondition.class));
    }

    @Test
    void create_validPriceCondition_succeeds() {
        Plan plan = TestFixtures.planBuilder().status(PlanStatus.PENDING).build();
        ConditionDTO.CreateRequest request = ConditionDTO.CreateRequest.builder()
                .conditionType(ConditionType.PRICE)
                .targetPrice(new BigDecimal("12.50"))
                .build();

        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(conditionRepository.save(any(PlanCondition.class))).thenAnswer(inv -> {
            PlanCondition c = inv.getArgument(0);
            setField(c, "id", 1L);
            return c;
        });

        ConditionDTO.Response resp = service.create(1L, request);

        assertEquals(ConditionType.PRICE, resp.getConditionType());
        assertEquals(0, new BigDecimal("12.50").compareTo(resp.getTargetPrice()));
    }

    @Test
    void create_planNotFound_throws404() {
        when(planRepository.findById(999L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(999L, ConditionDTO.CreateRequest.builder()
                        .conditionType(ConditionType.PRICE)
                        .targetPrice(new BigDecimal("10.00"))
                        .build()));

        assertEquals(404, ex.getStatusCode());
    }

    @Test
    void create_planNotPending_throws409() {
        Plan plan = TestFixtures.planBuilder().status(PlanStatus.HOLDING).build();
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(1L, ConditionDTO.CreateRequest.builder()
                        .conditionType(ConditionType.PRICE)
                        .targetPrice(new BigDecimal("10.00"))
                        .build()));

        assertEquals(409, ex.getStatusCode());
    }

    @Test
    void create_maPeriodInvalid_throws() {
        Plan plan = TestFixtures.planBuilder().status(PlanStatus.PENDING).build();
        ConditionDTO.CreateRequest request = ConditionDTO.CreateRequest.builder()
                .conditionType(ConditionType.MA)
                .maPeriod(7)
                .build();

        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(1L, request));

        assertTrue(ex.getMessage().contains("MA"));
    }

    @Test
    void create_maPeriodNull_throws() {
        Plan plan = TestFixtures.planBuilder().status(PlanStatus.PENDING).build();
        ConditionDTO.CreateRequest request = ConditionDTO.CreateRequest.builder()
                .conditionType(ConditionType.MA)
                .maPeriod(null)
                .build();

        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(1L, request));

        assertTrue(ex.getMessage().contains("MA"));
    }

    @Test
    void create_priceTargetZero_throws() {
        Plan plan = TestFixtures.planBuilder().status(PlanStatus.PENDING).build();
        ConditionDTO.CreateRequest request = ConditionDTO.CreateRequest.builder()
                .conditionType(ConditionType.PRICE)
                .targetPrice(BigDecimal.ZERO)
                .build();

        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(1L, request));

        assertTrue(ex.getMessage().contains("大于 0"));
    }

    @Test
    void create_priceTargetNegative_throws() {
        Plan plan = TestFixtures.planBuilder().status(PlanStatus.PENDING).build();
        ConditionDTO.CreateRequest request = ConditionDTO.CreateRequest.builder()
                .conditionType(ConditionType.PRICE)
                .targetPrice(new BigDecimal("-5.00"))
                .build();

        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(1L, request));

        assertTrue(ex.getMessage().contains("大于 0"));
    }

    @Test
    void create_priceTargetNull_throws() {
        Plan plan = TestFixtures.planBuilder().status(PlanStatus.PENDING).build();
        ConditionDTO.CreateRequest request = ConditionDTO.CreateRequest.builder()
                .conditionType(ConditionType.PRICE)
                .targetPrice(null)
                .build();

        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(1L, request));

        assertTrue(ex.getMessage().contains("大于 0"));
    }

    @Test
    void create_multipleConditions_throws() {
        Plan plan = TestFixtures.planBuilder().status(PlanStatus.PENDING).build();
        PlanCondition existing = TestFixtures.conditionBuilder().plan(plan).build();
        plan.getConditions().add(existing);

        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create(1L, ConditionDTO.CreateRequest.builder()
                        .conditionType(ConditionType.PRICE)
                        .targetPrice(new BigDecimal("10.00"))
                        .build()));

        assertEquals(409, ex.getStatusCode());
    }

    @Test
    void listByPlan_existingPlan_returnsConditions() {
        Plan plan = TestFixtures.planBuilder().id(1L).build();
        PlanCondition cond1 = TestFixtures.conditionBuilder().plan(plan).id(1L).build();
        PlanCondition cond2 = TestFixtures.conditionBuilder().plan(plan).id(2L).build();
        when(planRepository.existsById(1L)).thenReturn(true);
        when(conditionRepository.findByPlanId(1L)).thenReturn(List.of(cond1, cond2));

        List<ConditionDTO.Response> result = service.listByPlan(1L);

        assertEquals(2, result.size());
    }

    @Test
    void listByPlan_planNotFound_throws404() {
        when(planRepository.existsById(999L)).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.listByPlan(999L));

        assertEquals(404, ex.getStatusCode());
    }

    @Test
    void update_validData_succeeds() {
        Plan plan = TestFixtures.planBuilder().id(1L).status(PlanStatus.PENDING).build();
        PlanCondition cond = TestFixtures.conditionBuilder()
                .plan(plan).id(1L)
                .conditionType(ConditionType.PRICE)
                .targetPrice(new BigDecimal("10.00"))
                .build();
        ConditionDTO.UpdateRequest request = ConditionDTO.UpdateRequest.builder()
                .targetPrice(new BigDecimal("12.00"))
                .build();

        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(conditionRepository.findById(1L)).thenReturn(Optional.of(cond));
        when(conditionRepository.save(any(PlanCondition.class))).thenReturn(cond);

        ConditionDTO.Response resp = service.update(1L, 1L, request);

        assertEquals(0, new BigDecimal("12.00").compareTo(cond.getTargetPrice()));
        verify(conditionRepository).save(cond);
    }

    @Test
    void update_planNotPending_throws409() {
        Plan plan = TestFixtures.planBuilder().status(PlanStatus.CLOSED).build();
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(1L, 1L, ConditionDTO.UpdateRequest.builder().build()));

        assertEquals(409, ex.getStatusCode());
    }

    @Test
    void update_conditionNotFound_throws404() {
        Plan plan = TestFixtures.planBuilder().status(PlanStatus.PENDING).build();
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(conditionRepository.findById(999L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(1L, 999L, ConditionDTO.UpdateRequest.builder().build()));

        assertEquals(404, ex.getStatusCode());
    }

    @Test
    void update_maPeriodInvalid_throws() {
        Plan plan = TestFixtures.planBuilder().status(PlanStatus.PENDING).build();
        PlanCondition cond = TestFixtures.conditionBuilder()
                .plan(plan).conditionType(ConditionType.MA).maPeriod(5).build();
        ConditionDTO.UpdateRequest request = ConditionDTO.UpdateRequest.builder()
                .maPeriod(7)
                .build();

        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(conditionRepository.findById(1L)).thenReturn(Optional.of(cond));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(1L, 1L, request));

        assertTrue(ex.getMessage().contains("MA"));
    }

    @Test
    void update_priceTargetInvalid_throws() {
        Plan plan = TestFixtures.planBuilder().status(PlanStatus.PENDING).build();
        PlanCondition cond = TestFixtures.conditionBuilder()
                .plan(plan).conditionType(ConditionType.PRICE).targetPrice(new BigDecimal("10.00")).build();
        ConditionDTO.UpdateRequest request = ConditionDTO.UpdateRequest.builder()
                .targetPrice(new BigDecimal("-1.00"))
                .build();

        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(conditionRepository.findById(1L)).thenReturn(Optional.of(cond));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(1L, 1L, request));

        assertTrue(ex.getMessage().contains("大于 0"));
    }

    @Test
    void delete_existingCondition_succeeds() {
        Plan plan = TestFixtures.planBuilder().status(PlanStatus.PENDING).build();
        PlanCondition cond = TestFixtures.conditionBuilder().plan(plan).id(1L).build();
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(conditionRepository.findById(1L)).thenReturn(Optional.of(cond));

        service.delete(1L, 1L);

        verify(conditionRepository).delete(cond);
    }

    @Test
    void delete_planNotPending_throws409() {
        Plan plan = TestFixtures.planBuilder().status(PlanStatus.HOLDING).build();
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.delete(1L, 1L));

        assertEquals(409, ex.getStatusCode());
    }

    @Test
    void delete_conditionNotFound_throws404() {
        Plan plan = TestFixtures.planBuilder().status(PlanStatus.PENDING).build();
        when(planRepository.findById(1L)).thenReturn(Optional.of(plan));
        when(conditionRepository.findById(999L)).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.delete(1L, 999L));

        assertEquals(404, ex.getStatusCode());
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception ignored) {}
    }
}
