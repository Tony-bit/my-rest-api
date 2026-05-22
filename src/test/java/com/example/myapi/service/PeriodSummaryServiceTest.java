package com.example.myapi.service;

import com.example.myapi.dto.ViewDTO;
import com.example.myapi.entity.*;
import com.example.myapi.repository.*;
import com.example.myapi.service.TushareService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PeriodSummaryServiceTest {

    @Mock private PlanRepository planRepository;
    @Mock private PlanExecutionRepository executionRepository;
    @Mock private ActualTradeRepository tradeRepository;
    @Mock private SystemConfigService systemConfigService;
    @Mock private TushareService tushareService;
    @Mock private SystemConfig systemConfig;
    @Mock private PlanAccount planAccount;

    private PeriodSummaryService service;

    @BeforeEach
    void setUp() {
        when(systemConfig.getBaselineCapital()).thenReturn(new BigDecimal("500000"));
        when(systemConfigService.getSystemConfig()).thenReturn(systemConfig);
        when(systemConfigService.getPlanAccount()).thenReturn(planAccount);
        when(planAccount.getCashBalance()).thenReturn(new BigDecimal("500000"));
        service = new PeriodSummaryService(planRepository, executionRepository, tradeRepository, systemConfigService, tushareService);
    }

    @Test
    void getSummary_weekPeriod() {
        when(planRepository.findAll()).thenReturn(List.of());
        when(tradeRepository.findByTradeDateBetween(any(), any())).thenReturn(List.of());

        ViewDTO.PeriodSummaryResponse resp = service.getSummary("WEEK");

        assertEquals("WEEK", resp.getPeriod());
        assertNotNull(resp.getPlanSummary());
    }

    @Test
    void getSummary_invalidPeriod_throws() {
        assertThrows(IllegalArgumentException.class, () -> service.getSummary("DECADE"));
    }

    @Test
    void getSummary_filtersPlansByCreatedAt() {
        Plan inRange = TestFixtures.planBuilder()
                .id(1L)
                .createdAt(LocalDateTime.of(2026, 5, 20, 10, 0))
                .build();
        Plan outRange = TestFixtures.planBuilder()
                .id(2L)
                .createdAt(LocalDateTime.of(2026, 1, 1, 10, 0))
                .build();

        when(planRepository.findAll()).thenReturn(Arrays.asList(inRange, outRange));
        when(tradeRepository.findByTradeDateBetween(any(), any())).thenReturn(List.of());

        ViewDTO.PeriodSummaryResponse resp = service.getSummary("WEEK");

        assertEquals(1, resp.getPlanList().size());
        assertEquals(1L, resp.getPlanList().get(0).getPlanId());
    }

    @Test
    void getSummary_pendingPlan_returnPercentIsZero() {
        Plan plan = TestFixtures.planBuilder()
                .id(1L)
                .status(PlanStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        when(planRepository.findAll()).thenReturn(List.of(plan));
        when(tradeRepository.findByTradeDateBetween(any(), any())).thenReturn(List.of());

        ViewDTO.PeriodSummaryResponse resp = service.getSummary("WEEK");

        assertEquals(BigDecimal.ZERO, resp.getPlanList().get(0).getReturnPercent());
    }

    @Test
    void getSummary_holdingPlan_returnPercentIsZero() {
        Plan plan = TestFixtures.planBuilder()
                .id(1L)
                .status(PlanStatus.HOLDING)
                .planType(PlanType.BUY)
                .createdAt(LocalDateTime.now())
                .build();
        PlanExecution buy = TestFixtures.executionBuilder()
                .id(1L).plan(plan)
                .triggerPrice(new BigDecimal("10.00"))
                .build();

        when(planRepository.findAll()).thenReturn(List.of(plan));
        when(executionRepository.findByPlanId(plan.getId())).thenReturn(List.of(buy));
        when(tradeRepository.findByTradeDateBetween(any(), any())).thenReturn(List.of());

        ViewDTO.PeriodSummaryResponse resp = service.getSummary("WEEK");

        assertEquals(BigDecimal.ZERO, resp.getPlanList().get(0).getReturnPercent());
    }

    @Test
    void getSummary_closedPlan_calculatesCorrectReturn() {
        // In the new buy-sell separation model, a CLOSED BUY plan with no SELL plan
        // returns 0 (since there's no sell execution to calculate against)
        Plan buyPlan = TestFixtures.planBuilder()
                .id(1L)
                .status(PlanStatus.CLOSED)
                .planType(PlanType.BUY)
                .createdAt(LocalDateTime.now())
                .build();

        PlanExecution buy = TestFixtures.executionBuilder()
                .id(1L).plan(buyPlan)
                .triggerPrice(new BigDecimal("10.00"))
                .build();

        when(planRepository.findAll()).thenReturn(List.of(buyPlan));
        when(executionRepository.findByPlanId(buyPlan.getId())).thenReturn(List.of(buy));
        when(tradeRepository.findByTradeDateBetween(any(), any())).thenReturn(List.of());

        ViewDTO.PeriodSummaryResponse resp = service.getSummary("WEEK");

        // No SELL executions for this BUY plan, so return is 0
        assertEquals(BigDecimal.ZERO, resp.getPlanList().get(0).getReturnPercent());
    }

    @Test
    void getSummary_noData_returnsZeros() {
        when(planRepository.findAll()).thenReturn(List.of());
        when(tradeRepository.findByTradeDateBetween(any(), any())).thenReturn(List.of());

        ViewDTO.PeriodSummaryResponse resp = service.getSummary("WEEK");

        assertEquals(0, resp.getPlanSummary().getPendingCount());
        assertTrue(resp.getPlanSummary().getTotalReturn().compareTo(BigDecimal.ZERO) == 0);
        assertTrue(resp.getPlanSummary().getAvgReturn().compareTo(BigDecimal.ZERO) == 0);
        assertTrue(resp.getGapPercent().compareTo(BigDecimal.ZERO) == 0);
    }
}
