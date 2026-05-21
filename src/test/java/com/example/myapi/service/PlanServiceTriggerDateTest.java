package com.example.myapi.service;

import com.example.myapi.dto.PlanDTO;
import com.example.myapi.exception.BusinessException;
import com.example.myapi.repository.PlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlanServiceTriggerDateTest {

    @Mock
    private PlanRepository planRepository;

    private PlanService service;

    @BeforeEach
    void setUp() {
        service = new PlanService(planRepository);
    }

    @Test
    void create_triggerDateWithin3Months_succeeds() {
        PlanDTO.CreateRequest request = PlanDTO.CreateRequest.builder()
                .name("测试预案")
                .stockCode("000001")
                .cycle(com.example.myapi.entity.PlanCycle.DAILY)
                .triggerDate(LocalDate.now().plusDays(30))
                .build();

        when(planRepository.save(any())).thenAnswer(inv -> {
            com.example.myapi.entity.Plan p = inv.getArgument(0);
            setField(p, "id", 1L);
            return p;
        });

        PlanDTO.Response resp = service.create(request);

        assertNotNull(resp);
        verify(planRepository).save(any());
    }

    @Test
    void create_triggerDateToday_succeeds() {
        PlanDTO.CreateRequest request = PlanDTO.CreateRequest.builder()
                .name("测试预案")
                .stockCode("000001")
                .cycle(com.example.myapi.entity.PlanCycle.DAILY)
                .triggerDate(LocalDate.now())
                .build();

        when(planRepository.save(any())).thenAnswer(inv -> {
            com.example.myapi.entity.Plan p = inv.getArgument(0);
            setField(p, "id", 1L);
            return p;
        });

        PlanDTO.Response resp = service.create(request);

        assertNotNull(resp);
        verify(planRepository).save(any());
    }

    @Test
    void create_triggerDateFuture_succeeds() {
        PlanDTO.CreateRequest request = PlanDTO.CreateRequest.builder()
                .name("测试预案")
                .stockCode("000001")
                .cycle(com.example.myapi.entity.PlanCycle.DAILY)
                .triggerDate(LocalDate.now().plusDays(60))
                .build();

        when(planRepository.save(any())).thenAnswer(inv -> {
            com.example.myapi.entity.Plan p = inv.getArgument(0);
            setField(p, "id", 1L);
            return p;
        });

        PlanDTO.Response resp = service.create(request);

        assertNotNull(resp);
        assertEquals(LocalDate.now().plusDays(60), resp.getTriggerDate());
    }

    @Test
    void create_triggerDateExactly3MonthsAgo_succeeds() {
        PlanDTO.CreateRequest request = PlanDTO.CreateRequest.builder()
                .name("测试预案")
                .stockCode("000001")
                .cycle(com.example.myapi.entity.PlanCycle.DAILY)
                .triggerDate(LocalDate.now().minusDays(90))
                .build();

        when(planRepository.save(any())).thenAnswer(inv -> {
            com.example.myapi.entity.Plan p = inv.getArgument(0);
            setField(p, "id", 1L);
            return p;
        });

        PlanDTO.Response resp = service.create(request);

        assertNotNull(resp);
        verify(planRepository).save(any());
    }

    @Test
    void create_triggerDateOlderThan3Months_throws400() {
        PlanDTO.CreateRequest request = PlanDTO.CreateRequest.builder()
                .name("测试预案")
                .stockCode("000001")
                .cycle(com.example.myapi.entity.PlanCycle.DAILY)
                .triggerDate(LocalDate.now().minusDays(91))
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.create(request));

        assertEquals(400, ex.getStatusCode());
        assertTrue(ex.getMessage().contains("90"));
        verify(planRepository, never()).save(any());
    }

    @Test
    void create_triggerDateNull_defaultsToToday() {
        PlanDTO.CreateRequest request = PlanDTO.CreateRequest.builder()
                .name("测试预案")
                .stockCode("000001")
                .cycle(com.example.myapi.entity.PlanCycle.DAILY)
                .triggerDate(null)
                .build();

        when(planRepository.save(any())).thenAnswer(inv -> {
            com.example.myapi.entity.Plan p = inv.getArgument(0);
            setField(p, "id", 1L);
            return p;
        });

        PlanDTO.Response resp = service.create(request);

        assertNotNull(resp);
        assertEquals(LocalDate.now(), resp.getTriggerDate());
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception ignored) {}
    }
}
