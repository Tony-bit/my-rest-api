package com.example.myapi.service;

import com.example.myapi.dto.ViewDTO;
import com.example.myapi.entity.*;
import com.example.myapi.repository.*;
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
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HoldingsServiceTest {

    @Mock private PlanRepository planRepository;
    @Mock private PlanExecutionRepository executionRepository;
    @Mock private ActualTradeRepository tradeRepository;
    @Mock private TushareService tushareService;

    private HoldingsService service;

    @BeforeEach
    void setUp() {
        service = new HoldingsService(planRepository, executionRepository, tradeRepository, tushareService);
    }

    @Test
    void getHoldings_noHoldingPlans_emptyPlanHoldings() {
        when(planRepository.findByStatus(PlanStatus.HOLDING)).thenReturn(List.of());

        ViewDTO.HoldingsResponse resp = service.getHoldings(false);

        assertTrue(resp.getPlanHoldings().isEmpty());
        assertEquals(BigDecimal.ZERO, resp.getSummary().getTotalPlanUnrealizedPL());
    }

    @Test
    void getHoldings_withHoldingPlan_positivePL() {
        Plan plan = TestFixtures.planBuilder()
                .id(1L).status(PlanStatus.HOLDING).planType(PlanType.BUY)
                .createdAt(LocalDateTime.of(2026, 5, 20, 10, 0))
                .build();
        PlanExecution buy = TestFixtures.executionBuilder()
                .id(1L).plan(plan)
                .triggerPrice(new BigDecimal("10.00"))
                .tradeDate(LocalDate.of(2026, 5, 2))
                .build();

        KLineData kd = new KLineData("000001", LocalDate.of(2026, 5, 21),
                new BigDecimal("10.50"), new BigDecimal("11.50"),
                new BigDecimal("10.50"), new BigDecimal("11.00"), 1_000_000);

        when(planRepository.findByStatus(PlanStatus.HOLDING)).thenReturn(List.of(plan));
        when(executionRepository.findByPlanId(plan.getId())).thenReturn(List.of(buy));
        when(tushareService.getDailyKLine(eq("000001"), any(LocalDate.class), eq(true)))
                .thenReturn(Optional.of(kd));

        ViewDTO.HoldingsResponse resp = service.getHoldings(false);

        assertEquals(1, resp.getPlanHoldings().size());
        assertEquals(new BigDecimal("100.00"), resp.getPlanHoldings().get(0).getUnrealizedPLAmount());
    }

    @Test
    void getHoldings_negativePL() {
        Plan plan = TestFixtures.planBuilder()
                .id(1L).status(PlanStatus.HOLDING).planType(PlanType.BUY)
                .createdAt(LocalDateTime.of(2026, 5, 20, 10, 0))
                .build();
        PlanExecution buy = TestFixtures.executionBuilder()
                .id(1L).plan(plan)
                .triggerPrice(new BigDecimal("10.00"))
                .tradeDate(LocalDate.of(2026, 5, 2))
                .build();

        KLineData kd = new KLineData("000001", LocalDate.now(),
                new BigDecimal("8.50"), new BigDecimal("9.50"),
                new BigDecimal("8.50"), new BigDecimal("9.00"), 1_000_000);

        when(planRepository.findByStatus(PlanStatus.HOLDING)).thenReturn(List.of(plan));
        when(executionRepository.findByPlanId(plan.getId())).thenReturn(List.of(buy));
        when(tushareService.getDailyKLine(eq("000001"), any(LocalDate.class), eq(true)))
                .thenReturn(Optional.of(kd));

        ViewDTO.HoldingsResponse resp = service.getHoldings(false);

        assertEquals(new BigDecimal("-100.00"), resp.getPlanHoldings().get(0).getUnrealizedPLAmount());
        assertEquals(new BigDecimal("-10.0000"), resp.getPlanHoldings().get(0).getUnrealizedPLPercent());
    }

    @Test
    void getHoldings_holdDaysCalculated() {
        Plan plan = TestFixtures.planBuilder()
                .id(1L).status(PlanStatus.HOLDING).planType(PlanType.BUY)
                .createdAt(LocalDateTime.of(2026, 5, 20, 10, 0))
                .build();
        PlanExecution buy = TestFixtures.executionBuilder()
                .id(1L).plan(plan)
                .triggerPrice(new BigDecimal("10.00"))
                .tradeDate(LocalDate.of(2026, 5, 2))
                .build();

        KLineData kd = new KLineData("000001", LocalDate.of(2026, 5, 21),
                new BigDecimal("10.50"), new BigDecimal("11.50"),
                new BigDecimal("10.50"), new BigDecimal("11.00"), 1_000_000);

        when(planRepository.findByStatus(PlanStatus.HOLDING)).thenReturn(List.of(plan));
        when(executionRepository.findByPlanId(plan.getId())).thenReturn(List.of(buy));
        when(tushareService.getDailyKLine(eq("000001"), any(LocalDate.class), eq(true)))
                .thenReturn(Optional.of(kd));

        ViewDTO.HoldingsResponse resp = service.getHoldings(false);

        assertEquals(20, resp.getPlanHoldings().get(0).getHoldDays());
    }

    @Test
    void getHoldings_includesIntradayVolatility() {
        Plan plan = TestFixtures.planBuilder()
                .id(1L).status(PlanStatus.HOLDING).planType(PlanType.BUY)
                .createdAt(LocalDateTime.of(2026, 5, 20, 10, 0))
                .build();
        PlanExecution buy = TestFixtures.executionBuilder()
                .id(1L).plan(plan)
                .triggerPrice(new BigDecimal("10.00"))
                .tradeDate(LocalDate.of(2026, 5, 2))
                .build();

        KLineData kd = new KLineData("000001", LocalDate.now(),
                new BigDecimal("10.50"), new BigDecimal("11.50"),
                new BigDecimal("10.50"), new BigDecimal("11.00"), 1_000_000);

        when(planRepository.findByStatus(PlanStatus.HOLDING)).thenReturn(List.of(plan));
        when(executionRepository.findByPlanId(plan.getId())).thenReturn(List.of(buy));
        when(tushareService.getDailyKLine(eq("000001"), any(LocalDate.class), eq(true)))
                .thenReturn(Optional.of(kd));

        ViewDTO.HoldingsResponse resp = service.getHoldings(false);

        ViewDTO.PlanHoldingDTO ph = resp.getPlanHoldings().get(0);
        assertEquals(new BigDecimal("11.50"), ph.getHighPrice());
        assertEquals(new BigDecimal("10.50"), ph.getLowPrice());
        assertEquals(new BigDecimal("11.00"), ph.getClosePrice());
    }

    @Test
    void getHoldings_noActualTrades_emptyActualHoldings() {
        when(planRepository.findByStatus(PlanStatus.HOLDING)).thenReturn(List.of());
        when(tradeRepository.findAll()).thenReturn(List.of());

        ViewDTO.HoldingsResponse resp = service.getHoldings(false);

        assertTrue(resp.getActualHoldings().isEmpty());
        assertEquals(BigDecimal.ZERO, resp.getSummary().getTotalActualUnrealizedPL());
    }

    @Test
    void getHoldings_aggregatesByStockCode() {
        ActualTrade buy1 = TestFixtures.tradeBuilder()
                .id(1L).stockCode("000001").direction(TradeDirection.BUY)
                .price(new BigDecimal("10.00")).quantity(new BigDecimal("100"))
                .isMatched(false).build();
        ActualTrade buy2 = TestFixtures.tradeBuilder()
                .id(2L).stockCode("000001").direction(TradeDirection.BUY)
                .price(new BigDecimal("12.00")).quantity(new BigDecimal("100"))
                .isMatched(false).build();
        ActualTrade buy3 = TestFixtures.tradeBuilder()
                .id(3L).stockCode("600000").direction(TradeDirection.BUY)
                .price(new BigDecimal("20.00")).quantity(new BigDecimal("200"))
                .isMatched(false).build();

        when(planRepository.findByStatus(PlanStatus.HOLDING)).thenReturn(List.of());
        when(tradeRepository.findAll()).thenReturn(Arrays.asList(buy1, buy2, buy3));

        KLineData kd1 = new KLineData("000001", LocalDate.now(),
                new BigDecimal("10.50"), new BigDecimal("12.00"),
                new BigDecimal("10.50"), new BigDecimal("12.00"), 1_000_000);
        KLineData kd2 = new KLineData("600000", LocalDate.now(),
                new BigDecimal("19.00"), new BigDecimal("22.00"),
                new BigDecimal("19.00"), new BigDecimal("22.00"), 2_000_000);

        when(tushareService.getDailyKLine(eq("000001"), any(), eq(true))).thenReturn(Optional.of(kd1));
        when(tushareService.getDailyKLine(eq("600000"), any(), eq(true))).thenReturn(Optional.of(kd2));

        ViewDTO.HoldingsResponse resp = service.getHoldings(false);

        assertEquals(2, resp.getActualHoldings().size());
        ViewDTO.ActualHoldingDTO ah000001 = resp.getActualHoldings().stream()
                .filter(a -> "000001".equals(a.getStockCode())).findFirst().orElseThrow();
        assertEquals(new BigDecimal("11.0000"), ah000001.getAvgCostPrice());
    }

    @Test
    void getHoldings_actualUnrealizedPLCalculation() {
        ActualTrade buy = TestFixtures.tradeBuilder()
                .id(1L).stockCode("000001").direction(TradeDirection.BUY)
                .price(new BigDecimal("10.00")).quantity(new BigDecimal("100"))
                .isMatched(false).stockName("平安银行").build();

        when(planRepository.findByStatus(PlanStatus.HOLDING)).thenReturn(List.of());
        when(tradeRepository.findAll()).thenReturn(List.of(buy));

        KLineData kd = new KLineData("000001", LocalDate.now(),
                new BigDecimal("10.50"), new BigDecimal("12.50"),
                new BigDecimal("10.50"), new BigDecimal("12.00"), 1_000_000);

        when(tushareService.getDailyKLine(eq("000001"), any(), eq(true))).thenReturn(Optional.of(kd));

        ViewDTO.HoldingsResponse resp = service.getHoldings(false);

        ViewDTO.ActualHoldingDTO ah = resp.getActualHoldings().get(0);
        assertEquals(new BigDecimal("200.00"), ah.getUnrealizedPLAmount());
    }

    @Test
    void getHoldings_tushareReturnsEmpty_skipsPlan() {
        Plan plan = TestFixtures.planBuilder()
                .id(1L).status(PlanStatus.HOLDING).planType(PlanType.BUY)
                .createdAt(LocalDateTime.of(2026, 5, 20, 10, 0))
                .build();
        PlanExecution buy = TestFixtures.executionBuilder()
                .id(1L).plan(plan)
                .triggerPrice(new BigDecimal("10.00"))
                .tradeDate(LocalDate.of(2026, 5, 2))
                .build();

        when(planRepository.findByStatus(PlanStatus.HOLDING)).thenReturn(List.of(plan));
        when(executionRepository.findByPlanId(plan.getId())).thenReturn(List.of(buy));
        when(tushareService.getDailyKLine(anyString(), any(), anyBoolean())).thenReturn(Optional.empty());

        ViewDTO.HoldingsResponse resp = service.getHoldings(false);

        assertTrue(resp.getPlanHoldings().isEmpty());
    }

    @Test
    void getHoldings_noBuyRecords_skipsPlan() {
        Plan plan = TestFixtures.planBuilder()
                .id(1L).status(PlanStatus.HOLDING).planType(PlanType.BUY)
                .createdAt(LocalDateTime.of(2026, 5, 20, 10, 0))
                .build();

        when(planRepository.findByStatus(PlanStatus.HOLDING)).thenReturn(List.of(plan));
        when(executionRepository.findByPlanId(plan.getId())).thenReturn(List.of());

        ViewDTO.HoldingsResponse resp = service.getHoldings(false);

        assertTrue(resp.getPlanHoldings().isEmpty());
    }
}
