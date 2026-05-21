package com.example.myapi.service;

import com.example.myapi.dto.DashboardDTO;
import com.example.myapi.dto.ViewDTO;
import com.example.myapi.entity.*;
import com.example.myapi.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock private HoldingsService holdingsService;
    @Mock private DailySnapshotRepository snapshotRepository;
    @Mock private PlanExecutionRepository planExecutionRepository;
    @Mock private ActualTradeRepository actualTradeRepository;
    @Mock private PlanRepository planRepository;

    private DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(
                holdingsService, snapshotRepository, planExecutionRepository,
                actualTradeRepository, planRepository);
    }

    @Test
    void getDashboard_shouldReturnAllSections() {
        ViewDTO.HoldingsResponse holdings = ViewDTO.HoldingsResponse.builder()
                .baselineCapital(new BigDecimal("500000"))
                .planCashBalance(new BigDecimal("500000"))
                .actualCashBalance(new BigDecimal("500000"))
                .summary(ViewDTO.Summary.builder()
                        .planReturnPercent(new BigDecimal("1.0000"))
                        .actualReturnPercent(new BigDecimal("0.5000"))
                        .holdingGap(new BigDecimal("0.5000"))
                        .build())
                .planHoldings(List.of())
                .actualHoldings(List.of())
                .build();

        when(holdingsService.getHoldings(false)).thenReturn(holdings);
        when(snapshotRepository.findAll()).thenReturn(List.of());
        when(planExecutionRepository.findAll()).thenReturn(List.of());
        when(actualTradeRepository.findAll()).thenReturn(List.of());
        when(planRepository.findAll()).thenReturn(List.of());

        DashboardDTO.Response resp = service.getDashboard();

        assertNotNull(resp.getBaselineCapital());
        assertNotNull(resp.getKpis());
        assertNotNull(resp.getTrend());
        assertNotNull(resp.getPlanHoldings());
        assertNotNull(resp.getActualHoldings());
        assertNotNull(resp.getExecutionLog());
    }

    @Test
    void getDashboard_kpisFromHoldings() {
        ViewDTO.HoldingsResponse holdings = ViewDTO.HoldingsResponse.builder()
                .baselineCapital(new BigDecimal("500000"))
                .planCashBalance(new BigDecimal("480000"))
                .actualCashBalance(new BigDecimal("490000"))
                .summary(ViewDTO.Summary.builder()
                        .planReturnPercent(new BigDecimal("2.5000"))
                        .actualReturnPercent(new BigDecimal("1.0000"))
                        .holdingGap(new BigDecimal("1.5000"))
                        .build())
                .planHoldings(List.of())
                .actualHoldings(List.of())
                .build();

        when(holdingsService.getHoldings(false)).thenReturn(holdings);
        when(snapshotRepository.findAll()).thenReturn(List.of());
        when(planExecutionRepository.findAll()).thenReturn(List.of());
        when(actualTradeRepository.findAll()).thenReturn(List.of());
        when(planRepository.findAll()).thenReturn(List.of());

        DashboardDTO.Response resp = service.getDashboard();

        DashboardDTO.Kpis kpis = resp.getKpis();
        assertEquals(new BigDecimal("2.5000"), kpis.getPlanReturnPercent());
        assertEquals(new BigDecimal("1.0000"), kpis.getActualReturnPercent());
        assertEquals(new BigDecimal("1.5000"), kpis.getHoldingGap());
        assertEquals(new BigDecimal("480000"), kpis.getPlanCashBalance());
        assertEquals(new BigDecimal("490000"), kpis.getActualCashBalance());
    }

    @Test
    void getDashboard_planCountsFromRepository() {
        ViewDTO.HoldingsResponse holdings = ViewDTO.HoldingsResponse.builder()
                .baselineCapital(new BigDecimal("500000"))
                .planCashBalance(new BigDecimal("500000"))
                .actualCashBalance(new BigDecimal("500000"))
                .summary(ViewDTO.Summary.builder().build())
                .planHoldings(List.of())
                .actualHoldings(List.of())
                .build();

        when(holdingsService.getHoldings(false)).thenReturn(holdings);
        when(snapshotRepository.findAll()).thenReturn(List.of());
        when(planExecutionRepository.findAll()).thenReturn(List.of());
        when(actualTradeRepository.findAll()).thenReturn(List.of());
        when(planRepository.findAll()).thenReturn(List.of(
                TestFixtures.planBuilder().id(1L).status(PlanStatus.PENDING).build(),
                TestFixtures.planBuilder().id(2L).status(PlanStatus.HOLDING).build(),
                TestFixtures.planBuilder().id(3L).status(PlanStatus.CLOSED).build()
        ));

        DashboardDTO.Response resp = service.getDashboard();

        assertEquals(2, resp.getKpis().getActivePlanCount());
        assertEquals(1, resp.getKpis().getHoldingPlanCount());
    }

    @Test
    void getDashboard_executionLogFromExecutionsAndTrades() {
        ViewDTO.HoldingsResponse holdings = ViewDTO.HoldingsResponse.builder()
                .baselineCapital(new BigDecimal("500000"))
                .planCashBalance(new BigDecimal("500000"))
                .actualCashBalance(new BigDecimal("500000"))
                .summary(ViewDTO.Summary.builder().build())
                .planHoldings(List.of())
                .actualHoldings(List.of())
                .build();

        Plan plan = TestFixtures.planBuilder().id(1L).name("测试预案").status(PlanStatus.HOLDING).build();
        PlanExecution exec = TestFixtures.executionBuilder()
                .id(1L).plan(plan).direction(TradeDirection.BUY)
                .triggerPrice(new BigDecimal("10.00"))
                .tradeDate(LocalDate.of(2026, 5, 15))
                .build();

        ActualTrade trade = TestFixtures.tradeBuilder()
                .id(1L).stockCode("000001").direction(TradeDirection.BUY)
                .price(new BigDecimal("10.00")).quantity(new BigDecimal("100"))
                .tradeDate(LocalDate.of(2026, 5, 16))
                .build();

        when(holdingsService.getHoldings(false)).thenReturn(holdings);
        when(snapshotRepository.findAll()).thenReturn(List.of());
        when(planExecutionRepository.findAll()).thenReturn(List.of(exec));
        when(actualTradeRepository.findAll()).thenReturn(List.of(trade));
        when(planRepository.findAll()).thenReturn(List.of());

        DashboardDTO.Response resp = service.getDashboard();

        assertEquals(2, resp.getExecutionLog().size());
    }

    @Test
    void getDashboard_planHoldingsFromHoldings() {
        ViewDTO.PlanHoldingDTO ph = ViewDTO.PlanHoldingDTO.builder()
                .planId(1L).planName("预案A").stockCode("000001")
                .status(PlanStatus.HOLDING)
                .unrealizedPLAmount(new BigDecimal("500.00"))
                .unrealizedPLPercent(new BigDecimal("5.0000"))
                .holdDays(5)
                .build();

        ViewDTO.HoldingsResponse holdings = ViewDTO.HoldingsResponse.builder()
                .baselineCapital(new BigDecimal("500000"))
                .planCashBalance(new BigDecimal("500000"))
                .actualCashBalance(new BigDecimal("500000"))
                .summary(ViewDTO.Summary.builder().build())
                .planHoldings(List.of(ph))
                .actualHoldings(List.of())
                .build();

        when(holdingsService.getHoldings(false)).thenReturn(holdings);
        when(snapshotRepository.findAll()).thenReturn(List.of());
        when(planExecutionRepository.findAll()).thenReturn(List.of());
        when(actualTradeRepository.findAll()).thenReturn(List.of());
        when(planRepository.findAll()).thenReturn(List.of());

        DashboardDTO.Response resp = service.getDashboard();

        assertEquals(1, resp.getPlanHoldings().size());
        assertEquals("预案A", resp.getPlanHoldings().get(0).getPlanName());
        assertEquals(new BigDecimal("500.00"), resp.getPlanHoldings().get(0).getUnrealizedPLAmount());
    }
}
