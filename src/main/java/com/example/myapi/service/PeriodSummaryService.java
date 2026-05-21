package com.example.myapi.service;

import com.example.myapi.dto.ViewDTO;
import com.example.myapi.entity.*;
import com.example.myapi.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PeriodSummaryService {

    private final PlanRepository planRepository;
    private final PlanExecutionRepository executionRepository;
    private final ActualTradeRepository tradeRepository;

    @Transactional(readOnly = true)
    public ViewDTO.PeriodSummaryResponse getSummary(String period) {
        LocalDate today = LocalDate.now();
        LocalDate start;
        LocalDate end = today;

        switch (period.toUpperCase()) {
            case "WEEK" -> {
                start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            }
            case "MONTH" -> {
                start = today.withDayOfMonth(1);
            }
            case "YEAR" -> {
                start = today.withDayOfYear(1);
            }
            default -> throw new IllegalArgumentException("period must be WEEK/MONTH/YEAR");
        }

        List<Plan> allPlans = planRepository.findAll();
        List<Plan> plansInRange = allPlans.stream()
                .filter(p -> {
                    LocalDate created = p.getCreatedAt().toLocalDate();
                    return !created.isBefore(start) && !created.isAfter(end);
                })
                .toList();

        List<ViewDTO.PlanPeriodItem> planItems = plansInRange.stream()
                .map(p -> {
                    BigDecimal returnPct = calculatePlanReturn(p);
                    return ViewDTO.PlanPeriodItem.builder()
                            .planId(p.getId())
                            .name(p.getName())
                            .stockCode(p.getStockCode())
                            .status(p.getStatus())
                            .returnPercent(returnPct)
                            .build();
                })
                .toList();

        List<ActualTrade> tradesInRange = tradeRepository.findByTradeDateBetween(start, end);
        List<ActualTrade> sells = tradesInRange.stream()
                .filter(t -> t.getDirection() == TradeDirection.SELL && t.getProfitLoss() != null)
                .toList();

        List<ViewDTO.ActualPeriodItem> actualItems = sells.stream()
                .map(t -> ViewDTO.ActualPeriodItem.builder()
                        .id(t.getId())
                        .stockCode(t.getStockCode())
                        .direction(t.getDirection())
                        .tradeDate(t.getTradeDate())
                        .profitLossPercent(t.getProfitLossPercent())
                        .build())
                .toList();

        int pendingCount = (int) plansInRange.stream().filter(p -> p.getStatus() == PlanStatus.PENDING).count();
        BigDecimal planTotalReturn = planItems.stream()
                .map(ViewDTO.PlanPeriodItem::getReturnPercent)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal planAvgReturn = planItems.isEmpty() ? BigDecimal.ZERO :
                planTotalReturn.divide(BigDecimal.valueOf(planItems.size()), 4, RoundingMode.HALF_UP);

        BigDecimal actualTotalReturn = sells.stream()
                .map(ActualTrade::getProfitLossPercent)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal actualAvgReturn = sells.isEmpty() ? BigDecimal.ZERO :
                actualTotalReturn.divide(BigDecimal.valueOf(sells.size()), 4, RoundingMode.HALF_UP);

        BigDecimal gap = planAvgReturn.subtract(actualAvgReturn).setScale(4, RoundingMode.HALF_UP);

        return ViewDTO.PeriodSummaryResponse.builder()
                .period(period.toUpperCase())
                .planSummary(ViewDTO.PlanSummary.builder()
                        .totalTrades(plansInRange.size())
                        .pendingCount(pendingCount)
                        .totalReturn(planTotalReturn)
                        .avgReturn(planAvgReturn)
                        .build())
                .actualSummary(ViewDTO.ActualSummary.builder()
                        .totalTrades(sells.size())
                        .totalReturn(actualTotalReturn)
                        .avgReturn(actualAvgReturn)
                        .build())
                .gapPercent(gap)
                .planList(planItems)
                .actualList(actualItems)
                .build();
    }

    private BigDecimal calculatePlanReturn(Plan plan) {
        if (plan.getStatus() == PlanStatus.PENDING) return BigDecimal.ZERO;
        List<PlanExecution> buys = executionRepository.findByPlanId(plan.getId()).stream()
                .filter(e -> e.getDirection() == TradeDirection.BUY)
                .toList();
        if (buys.isEmpty()) return BigDecimal.ZERO;

        List<PlanExecution> sells = executionRepository.findByPlanId(plan.getId()).stream()
                .filter(e -> e.getDirection() == TradeDirection.SELL)
                .toList();

        if (!sells.isEmpty()) {
            PlanExecution lastSell = sells.get(sells.size() - 1);
            BigDecimal avgCost = buys.stream()
                    .map(PlanExecution::getTriggerPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(buys.size()), 4, RoundingMode.HALF_UP);
            return lastSell.getTriggerPrice()
                    .subtract(avgCost)
                    .divide(avgCost, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }
}
