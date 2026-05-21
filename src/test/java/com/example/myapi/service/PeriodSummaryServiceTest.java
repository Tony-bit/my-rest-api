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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

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
    void getSummary_monthPeriod() {
        when(planRepository.findAll()).thenReturn(List.of());
        when(tradeRepository.findByTradeDateBetween(any(), any())).thenReturn(List.of());

        ViewDTO.PeriodSummaryResponse resp = service.getSummary("MONTH");

        assertEquals("MONTH", resp.getPeriod());
    }

    @Test
    void getSummary_yearPeriod() {
        when(planRepository.findAll()).thenReturn(List.of());
        when(tradeRepository.findByTradeDateBetween(any(), any())).thenReturn(List.of());

        ViewDTO.PeriodSummaryResponse resp = service.getSummary("YEAR");

        assertEquals("YEAR", resp.getPeriod());
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
                .createdAt(LocalDateTime.now())
                .build();
        PlanExecution buy = TestFixtures.executionBuilder()
                .id(1L).plan(plan)
                .direction(TradeDirection.BUY)
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
        Plan plan = TestFixtures.planBuilder()
                .id(1L)
                .status(PlanStatus.CLOSED)
                .createdAt(LocalDateTime.now())
                .build();
        PlanExecution buy = TestFixtures.executionBuilder()
                .id(1L).plan(plan)
                .direction(TradeDirection.BUY)
                .triggerPrice(new BigDecimal("10.00"))
                .build();
        PlanExecution sell = TestFixtures.executionBuilder()
                .id(2L).plan(plan)
                .direction(TradeDirection.SELL)
                .triggerPrice(new BigDecimal("12.00"))
                .build();

        when(planRepository.findAll()).thenReturn(List.of(plan));
        when(executionRepository.findByPlanId(plan.getId())).thenReturn(Arrays.asList(buy, sell));
        when(tradeRepository.findByTradeDateBetween(any(), any())).thenReturn(List.of());

        ViewDTO.PeriodSummaryResponse resp = service.getSummary("WEEK");

        assertEquals(new BigDecimal("20.0000"), resp.getPlanList().get(0).getReturnPercent());
    }

    @Test
    void getSummary_calculatesGap() {
        // plan: BUY@10.00, SELL@11.50 → (11.5-10)/10*100 = 15%
        // actual: 10%
        // gap = 15% - 10% = 5%
        Plan plan = TestFixtures.planBuilder()
                .id(1L).status(PlanStatus.CLOSED).createdAt(LocalDateTime.now()).build();
        PlanExecution buy = TestFixtures.executionBuilder()
                .id(1L).plan(plan).direction(TradeDirection.BUY)
                .triggerPrice(new BigDecimal("10.00")).build();
        PlanExecution sell = TestFixtures.executionBuilder()
                .id(2L).plan(plan).direction(TradeDirection.SELL)
                .triggerPrice(new BigDecimal("11.50")).build();

        ActualTrade actualSell = TestFixtures.tradeBuilder()
                .id(1L).direction(TradeDirection.SELL)
                .price(new BigDecimal("11.00"))
                .profitLoss(new BigDecimal("100.00"))
                .profitLossPercent(new BigDecimal("10.0000"))
                .build();

        when(planRepository.findAll()).thenReturn(List.of(plan));
        when(executionRepository.findByPlanId(plan.getId())).thenReturn(Arrays.asList(buy, sell));
        when(tradeRepository.findByTradeDateBetween(any(), any())).thenReturn(List.of(actualSell));

        ViewDTO.PeriodSummaryResponse resp = service.getSummary("WEEK");

        assertEquals(new BigDecimal("5.0000"), resp.getGapPercent());
    }

    @Test
    void getSummary_actualListOnlySellsWithPL() {
        Plan plan = TestFixtures.planBuilder()
                .id(1L).createdAt(LocalDateTime.now()).build();

        ActualTrade buy = TestFixtures.tradeBuilder()
                .id(1L).direction(TradeDirection.BUY).build();
        ActualTrade sellNoPL = TestFixtures.tradeBuilder()
                .id(2L).direction(TradeDirection.SELL)
                .profitLoss(null).build();
        ActualTrade sellWithPL = TestFixtures.tradeBuilder()
                .id(3L).direction(TradeDirection.SELL)
                .profitLoss(new BigDecimal("100.00"))
                .profitLossPercent(new BigDecimal("10.0000"))
                .build();

        when(planRepository.findAll()).thenReturn(List.of(plan));
        when(tradeRepository.findByTradeDateBetween(any(), any()))
                .thenReturn(Arrays.asList(buy, sellNoPL, sellWithPL));

        ViewDTO.PeriodSummaryResponse resp = service.getSummary("WEEK");

        assertEquals(1, resp.getActualList().size());
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

    @Test
    void getSummary_multiplePlans_avgReturnIsTotalDividedByCount() {
        Plan p1 = TestFixtures.planBuilder()
                .id(1L).status(PlanStatus.CLOSED).createdAt(LocalDateTime.now()).build();
        Plan p2 = TestFixtures.planBuilder()
                .id(2L).status(PlanStatus.CLOSED).createdAt(LocalDateTime.now()).build();

        for (Plan p : Arrays.asList(p1, p2)) {
            when(executionRepository.findByPlanId(p.getId())).thenReturn(List.of(
                    TestFixtures.executionBuilder().id(p.getId()).plan(p)
                            .direction(TradeDirection.BUY).triggerPrice(new BigDecimal("10.00")).build(),
                    TestFixtures.executionBuilder().id(p.getId() + 10).plan(p)
                            .direction(TradeDirection.SELL).triggerPrice(new BigDecimal("11.00")).build()
            ));
        }

        when(planRepository.findAll()).thenReturn(Arrays.asList(p1, p2));
        when(tradeRepository.findByTradeDateBetween(any(), any())).thenReturn(List.of());

        ViewDTO.PeriodSummaryResponse resp = service.getSummary("WEEK");

        // Each plan has 10% return → avg = 10%
        assertEquals(new BigDecimal("10.0000"), resp.getPlanSummary().getAvgReturn());
    }
}
