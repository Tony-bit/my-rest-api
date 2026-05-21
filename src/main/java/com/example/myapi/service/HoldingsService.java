package com.example.myapi.service;

import com.example.myapi.dto.ViewDTO;
import com.example.myapi.entity.*;
import com.example.myapi.repository.*;
import com.example.myapi.service.TushareService.KLineData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class HoldingsService {

    private final PlanRepository planRepository;
    private final PlanExecutionRepository executionRepository;
    private final ActualTradeRepository tradeRepository;
    private final TushareService tushareService;

    @Transactional(readOnly = true)
    public ViewDTO.HoldingsResponse getHoldings(boolean refresh) {
        LocalDate today = LocalDate.now();
        List<Plan> holdingPlans = planRepository.findByStatus(PlanStatus.HOLDING);

        List<ViewDTO.PlanHoldingDTO> planHoldings = new ArrayList<>();
        BigDecimal totalPlanPL = BigDecimal.ZERO;
        BigDecimal totalPlanPLPct = BigDecimal.ZERO;

        for (Plan plan : holdingPlans) {
            Optional<KLineData> kDataOpt = refresh
                    ? tushareService.getDailyKLine(plan.getStockCode(), today, true)
                    : tushareService.getDailyKLine(plan.getStockCode(), today, true);

            if (kDataOpt.isEmpty()) continue;
            KLineData kData = kDataOpt.get();

            List<PlanExecution> buys = executionRepository.findByPlanId(plan.getId()).stream()
                    .filter(e -> e.getDirection() == TradeDirection.BUY)
                    .toList();

            if (buys.isEmpty()) continue;

            BigDecimal avgCost = buys.stream()
                    .map(PlanExecution::getTriggerPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(buys.size()), 4, RoundingMode.HALF_UP);

            BigDecimal unrealizedPL = kData.close.subtract(avgCost).setScale(2, RoundingMode.HALF_UP);
            BigDecimal unrealizedPLPct = kData.close.subtract(avgCost)
                    .divide(avgCost, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(4, RoundingMode.HALF_UP);

            PlanExecution firstBuy = buys.get(0);
            int holdDays = (int) ChronoUnit.DAYS.between(firstBuy.getTradeDate(), today);

            planHoldings.add(ViewDTO.PlanHoldingDTO.builder()
                    .planId(plan.getId())
                    .name(plan.getName())
                    .stockCode(plan.getStockCode())
                    .status(plan.getStatus())
                    .costBasis(avgCost)
                    .currentPrice(kData.close)
                    .unrealizedPL(unrealizedPL)
                    .unrealizedPLPercent(unrealizedPLPct)
                    .holdDays(holdDays)
                    .highPrice(kData.high)
                    .lowPrice(kData.low)
                    .closePrice(kData.close)
                    .entryDate(firstBuy.getTradeDate())
                    .build());

            totalPlanPL = totalPlanPL.add(unrealizedPL);
            totalPlanPLPct = totalPlanPLPct.add(unrealizedPLPct);
        }

        Map<String, List<ActualTrade>> tradesByStock = tradeRepository.findAll().stream()
                .filter(t -> t.getDirection() == TradeDirection.BUY && !Boolean.TRUE.equals(t.getIsMatched()))
                .collect(Collectors.groupingBy(ActualTrade::getStockCode));

        List<ViewDTO.ActualHoldingDTO> actualHoldings = new ArrayList<>();
        BigDecimal totalActualPL = BigDecimal.ZERO;
        BigDecimal totalActualPLPct = BigDecimal.ZERO;

        for (Map.Entry<String, List<ActualTrade>> entry : tradesByStock.entrySet()) {
            String stockCode = entry.getKey();
            List<ActualTrade> buys = entry.getValue();

            Optional<KLineData> kDataOpt = tushareService.getDailyKLine(stockCode, today, true);
            if (kDataOpt.isEmpty()) continue;
            KLineData kData = kDataOpt.get();

            BigDecimal totalQty = buys.stream()
                    .map(ActualTrade::getQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalCost = buys.stream()
                    .map(t -> t.getPrice().multiply(t.getQuantity()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal avgCostBasis = totalCost.divide(totalQty, 4, RoundingMode.HALF_UP);

            BigDecimal unrealizedPL = kData.close.subtract(avgCostBasis)
                    .multiply(totalQty)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal unrealizedPLPct = kData.close.subtract(avgCostBasis)
                    .divide(avgCostBasis, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(4, RoundingMode.HALF_UP);

            String stockName = buys.get(0).getStockName();

            actualHoldings.add(ViewDTO.ActualHoldingDTO.builder()
                    .stockCode(stockCode)
                    .stockName(stockName)
                    .openQuantity(totalQty)
                    .avgCostBasis(avgCostBasis)
                    .currentPrice(kData.close)
                    .unrealizedPL(unrealizedPL)
                    .unrealizedPLPercent(unrealizedPLPct)
                    .build());

            totalActualPL = totalActualPL.add(unrealizedPL);
            totalActualPLPct = totalActualPLPct.add(unrealizedPLPct);
        }

        BigDecimal holdingGap = planHoldings.isEmpty() || actualHoldings.isEmpty()
                ? BigDecimal.ZERO
                : totalPlanPLPct.subtract(totalActualPLPct).setScale(4, RoundingMode.HALF_UP);

        return ViewDTO.HoldingsResponse.builder()
                .summary(ViewDTO.Summary.builder()
                        .totalPlanUnrealizedPL(totalPlanPL)
                        .totalPlanUnrealizedPLPercent(totalPlanPLPct)
                        .totalActualUnrealizedPL(totalActualPL)
                        .totalActualUnrealizedPLPercent(totalActualPLPct)
                        .holdingGap(holdingGap)
                        .build())
                .planHoldings(planHoldings)
                .actualHoldings(actualHoldings)
                .build();
    }
}
