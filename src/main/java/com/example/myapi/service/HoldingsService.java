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
    private final SystemConfigService systemConfigService;

    private static final BigDecimal DEFAULT_BASELINE = new BigDecimal("500000");
    private static final BigDecimal DEFAULT_CASH = new BigDecimal("500000");

    @Transactional(readOnly = true)
    public ViewDTO.HoldingsResponse getHoldings(boolean refresh) {
        log.info("getHoldings called, refresh={}", refresh);
        LocalDate today = LocalDate.now();
        List<Plan> holdingPlans = planRepository.findByStatus(PlanStatus.HOLDING);
        log.info("Found {} holding plans", holdingPlans.size());

        BigDecimal baselineCapital = systemConfigService.getSystemConfig().getBaselineCapital();
        BigDecimal planCashBalance = systemConfigService.getPlanAccount().getCashBalance();
        BigDecimal actualCashBalance = systemConfigService.getActualAccount().getCashBalance();
        log.info("Account balances - baseline={}, planCash={}, actualCash={}", baselineCapital, planCashBalance, actualCashBalance);

        List<ViewDTO.PlanHoldingDTO> planHoldings = new ArrayList<>();
        BigDecimal totalPlanPL = BigDecimal.ZERO;
        BigDecimal totalPlanMarketValue = BigDecimal.ZERO;

        for (Plan plan : holdingPlans) {
            LocalDate queryDate = today;
            Optional<KLineData> kDataOpt = tushareService.getDailyKLine(plan.getStockCode(), queryDate, true);
            if (kDataOpt.isEmpty()) {
                // Fallback to previous trading days
                for (int i = 1; i <= 7; i++) {
                    LocalDate prevDate = today.minusDays(i);
                    kDataOpt = tushareService.getDailyKLine(plan.getStockCode(), prevDate, true);
                    if (kDataOpt.isPresent()) {
                        queryDate = prevDate;
                        break;
                    }
                }
            }
            if (kDataOpt.isEmpty()) continue;
            KLineData kData = kDataOpt.get();

            List<PlanExecution> buys = executionRepository.findByPlanIdWithPlan(plan.getId()).stream()
                    .filter(e -> e.getPlan().getPlanType() == PlanType.BUY)
                    .toList();
            if (buys.isEmpty()) continue;

            BigDecimal totalShares = BigDecimal.valueOf(buys.size())
                    .multiply(plan.getExecutionQuantity() != null ? plan.getExecutionQuantity() : BigDecimal.ONE);
            BigDecimal avgCostPerShare = buys.stream()
                    .map(PlanExecution::getTriggerPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(buys.size()), 4, RoundingMode.HALF_UP);
            BigDecimal costPrice = totalShares.multiply(avgCostPerShare);
            BigDecimal currentValue = kData.close.multiply(totalShares);
            BigDecimal unrealizedPLAmount = currentValue.subtract(costPrice).setScale(2, RoundingMode.HALF_UP);
            BigDecimal unrealizedPLPercent = kData.close.subtract(avgCostPerShare)
                    .divide(avgCostPerShare, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(4, RoundingMode.HALF_UP);

            PlanExecution firstBuy = buys.get(0);
            int holdDays = (int) ChronoUnit.DAYS.between(firstBuy.getTradeDate(), today);

            planHoldings.add(ViewDTO.PlanHoldingDTO.builder()
                    .planId(plan.getId())
                    .planName(plan.getName())
                    .stockCode(plan.getStockCode())
                    .stockName(plan.getStockName())
                    .status(plan.getStatus())
                    .costPrice(avgCostPerShare)
                    .quantity(totalShares)
                    .currentPrice(kData.close)
                    .unrealizedPLAmount(unrealizedPLAmount)
                    .unrealizedPLPercent(unrealizedPLPercent)
                    .holdDays(holdDays)
                    .highPrice(kData.high)
                    .lowPrice(kData.low)
                    .closePrice(kData.close)
                    .entryDate(firstBuy.getTradeDate())
                    .build());

            totalPlanPL = totalPlanPL.add(unrealizedPLAmount);
            totalPlanMarketValue = totalPlanMarketValue.add(currentValue);
        }

        Map<String, List<ActualTrade>> tradesByStock = tradeRepository.findAll().stream()
                .filter(t -> t.getDirection() == TradeDirection.BUY && !Boolean.TRUE.equals(t.getIsMatched()))
                .collect(Collectors.groupingBy(ActualTrade::getStockCode));

        List<ViewDTO.ActualHoldingDTO> actualHoldings = new ArrayList<>();
        BigDecimal totalActualPL = BigDecimal.ZERO;
        BigDecimal totalActualMarketValue = BigDecimal.ZERO;

        for (Map.Entry<String, List<ActualTrade>> entry : tradesByStock.entrySet()) {
            String stockCode = entry.getKey();
            List<ActualTrade> buys = entry.getValue();

            LocalDate queryDate = today;
            Optional<KLineData> kDataOpt = tushareService.getDailyKLine(stockCode, queryDate, true);
            if (kDataOpt.isEmpty()) {
                for (int i = 1; i <= 7; i++) {
                    LocalDate prevDate = today.minusDays(i);
                    kDataOpt = tushareService.getDailyKLine(stockCode, prevDate, true);
                    if (kDataOpt.isPresent()) {
                        queryDate = prevDate;
                        break;
                    }
                }
            }
            if (kDataOpt.isEmpty()) continue;
            KLineData kData = kDataOpt.get();

            BigDecimal totalQty = buys.stream()
                    .map(ActualTrade::getQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalCost = buys.stream()
                    .map(t -> t.getPrice().multiply(t.getQuantity()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal avgCostPrice = totalCost.divide(totalQty, 4, RoundingMode.HALF_UP);
            BigDecimal marketValue = kData.close.multiply(totalQty);
            BigDecimal unrealizedPLAmount = marketValue.subtract(totalCost).setScale(2, RoundingMode.HALF_UP);
            BigDecimal unrealizedPLPercent = kData.close.subtract(avgCostPrice)
                    .divide(avgCostPrice, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(4, RoundingMode.HALF_UP);

            String stockName = buys.get(0).getStockName();

            actualHoldings.add(ViewDTO.ActualHoldingDTO.builder()
                    .stockCode(stockCode)
                    .stockName(stockName)
                    .quantity(totalQty)
                    .avgCostPrice(avgCostPrice)
                    .currentPrice(kData.close)
                    .unrealizedPLAmount(unrealizedPLAmount)
                    .unrealizedPLPercent(unrealizedPLPercent)
                    .build());

            totalActualPL = totalActualPL.add(unrealizedPLAmount);
            totalActualMarketValue = totalActualMarketValue.add(marketValue);
        }

        BigDecimal planTotalValue = planCashBalance.add(totalPlanMarketValue);
        BigDecimal actualTotalValue = actualCashBalance.add(totalActualMarketValue);
        BigDecimal planReturnPercent = planTotalValue.subtract(baselineCapital)
                .divide(baselineCapital, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal actualReturnPercent = actualTotalValue.subtract(baselineCapital)
                .divide(baselineCapital, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal holdingGap = planReturnPercent.subtract(actualReturnPercent).setScale(4, RoundingMode.HALF_UP);

        return ViewDTO.HoldingsResponse.builder()
                .baselineCapital(baselineCapital)
                .planCashBalance(planCashBalance)
                .actualCashBalance(actualCashBalance)
                .summary(ViewDTO.Summary.builder()
                        .totalPlanUnrealizedPL(totalPlanPL)
                        .totalPlanUnrealizedPLPercent(planCashBalance.compareTo(BigDecimal.ZERO) > 0
                                ? totalPlanPL.multiply(BigDecimal.valueOf(100))
                                    .divide(planCashBalance, 6, RoundingMode.HALF_UP)
                                    .setScale(4, RoundingMode.HALF_UP)
                                : BigDecimal.ZERO)
                        .totalActualUnrealizedPL(totalActualPL)
                        .totalActualUnrealizedPLPercent(actualCashBalance.compareTo(BigDecimal.ZERO) > 0
                                ? totalActualPL.multiply(BigDecimal.valueOf(100))
                                    .divide(actualCashBalance, 6, RoundingMode.HALF_UP)
                                    .setScale(4, RoundingMode.HALF_UP)
                                : BigDecimal.ZERO)
                        .holdingGap(holdingGap)
                        .planTotalValue(planTotalValue)
                        .actualTotalValue(actualTotalValue)
                        .planReturnPercent(planReturnPercent)
                        .actualReturnPercent(actualReturnPercent)
                        .build())
                .planHoldings(planHoldings)
                .actualHoldings(actualHoldings)
                .build();
    }
}
