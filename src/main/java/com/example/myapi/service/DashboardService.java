package com.example.myapi.service;

import com.example.myapi.dto.DashboardDTO;
import com.example.myapi.dto.ViewDTO;
import com.example.myapi.entity.*;
import com.example.myapi.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final BigDecimal DEFAULT_BASELINE = new BigDecimal("500000");
    private static final BigDecimal DEFAULT_CASH = new BigDecimal("500000");

    private final HoldingsService holdingsService;
    private final DailySnapshotRepository snapshotRepository;
    private final PlanExecutionRepository planExecutionRepository;
    private final ActualTradeRepository actualTradeRepository;
    private final PlanRepository planRepository;

    @Transactional(readOnly = true)
    public DashboardDTO.Response getDashboard() {
        ViewDTO.HoldingsResponse holdings = holdingsService.getHoldings(false);

        BigDecimal baselineCapital = holdings.getBaselineCapital() != null
                ? holdings.getBaselineCapital() : DEFAULT_BASELINE;
        BigDecimal planCashBalance = holdings.getPlanCashBalance() != null
                ? holdings.getPlanCashBalance() : DEFAULT_CASH;
        BigDecimal actualCashBalance = holdings.getActualCashBalance() != null
                ? holdings.getActualCashBalance() : DEFAULT_CASH;
        ViewDTO.Summary summary = holdings.getSummary();

        List<Plan> allPlans = planRepository.findAll();
        long activeCount = allPlans.stream()
                .filter(p -> p.getStatus() == PlanStatus.PENDING || p.getStatus() == PlanStatus.HOLDING)
                .count();
        long holdingCount = allPlans.stream()
                .filter(p -> p.getStatus() == PlanStatus.HOLDING)
                .count();

        DashboardDTO.Kpis kpis = DashboardDTO.Kpis.builder()
                .planReturnPercent(summary != null ? summary.getPlanReturnPercent() : BigDecimal.ZERO)
                .actualReturnPercent(summary != null ? summary.getActualReturnPercent() : BigDecimal.ZERO)
                .holdingGap(summary != null ? summary.getHoldingGap() : BigDecimal.ZERO)
                .planCashBalance(planCashBalance)
                .actualCashBalance(actualCashBalance)
                .activePlanCount((int) activeCount)
                .holdingPlanCount((int) holdingCount)
                .build();

        List<DashboardDTO.TrendPoint> trend = buildTrend(holdings.getSummary());
        List<DashboardDTO.ExecutionLogItem> executionLog = buildExecutionLog();
        List<DashboardDTO.PlanHoldingItem> planHoldings = holdings.getPlanHoldings().stream()
                .map(this::toPlanHoldingItem)
                .toList();
        List<DashboardDTO.ActualHoldingItem> actualHoldings = holdings.getActualHoldings().stream()
                .map(this::toActualHoldingItem)
                .toList();

        return DashboardDTO.Response.builder()
                .baselineCapital(baselineCapital)
                .kpis(kpis)
                .trend(trend)
                .planHoldings(planHoldings)
                .actualHoldings(actualHoldings)
                .executionLog(executionLog)
                .build();
    }

    private List<DashboardDTO.TrendPoint> buildTrend(ViewDTO.Summary summary) {
        List<DailySnapshot> snapshots = snapshotRepository.findAll();
        if (snapshots.isEmpty() && summary == null) return List.of();

        Map<LocalDate, List<DailySnapshot>> byDate = snapshots.stream()
                .collect(Collectors.groupingBy(DailySnapshot::getSnapshotDate, TreeMap::new, Collectors.toList()));

        BigDecimal baseline = DEFAULT_BASELINE;
        LocalDate today = LocalDate.now();

        // Track last valid values for continuity
        BigDecimal lastPlanTotal = baseline;
        BigDecimal lastActualTotal = baseline;
        List<DashboardDTO.TrendPoint> trend = new ArrayList<>();

        for (Map.Entry<LocalDate, List<DailySnapshot>> entry : byDate.entrySet()) {
            LocalDate date = entry.getKey();
            List<DailySnapshot> daySnapshots = entry.getValue();

            // Check if we have plan snapshots for this date
            List<DailySnapshot> planSnapshots = daySnapshots.stream()
                    .filter(s -> s.getPlanId() != null)
                    .toList();
            boolean hasPlanSnapshot = !planSnapshots.isEmpty();

            // Check if we have actual trade snapshots for this date
            List<DailySnapshot> actualSnapshots = daySnapshots.stream()
                    .filter(s -> s.getActualTradeId() != null)
                    .toList();
            boolean hasActualSnapshot = !actualSnapshots.isEmpty();

            // Only include dates that have at least one type of snapshot
            if (!hasPlanSnapshot && !hasActualSnapshot) {
                continue;
            }

            BigDecimal planTotal = BigDecimal.ZERO;
            BigDecimal actualTotal = BigDecimal.ZERO;
            BigDecimal planReturnPct = BigDecimal.ZERO;
            BigDecimal actualReturnPct = BigDecimal.ZERO;

            if (hasPlanSnapshot) {
                planTotal = planSnapshots.stream()
                        .map(s -> {
                            BigDecimal cashBalance = s.getPlanCashBalance() != null
                                    ? s.getPlanCashBalance() : DEFAULT_CASH;
                            BigDecimal marketValue = s.getPlanMarketValue() != null
                                    ? s.getPlanMarketValue() : BigDecimal.ZERO;
                            return cashBalance.add(marketValue);
                        })
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                lastPlanTotal = planTotal;
            } else {
                // No plan snapshot, use last valid value
                planTotal = lastPlanTotal;
            }

            if (hasActualSnapshot) {
                actualTotal = actualSnapshots.stream()
                        .map(s -> {
                            BigDecimal cashBalance = s.getActualCashBalance() != null
                                    ? s.getActualCashBalance() : DEFAULT_CASH;
                            BigDecimal marketValue = s.getActualMarketValue() != null
                                    ? s.getActualMarketValue() : BigDecimal.ZERO;
                            return cashBalance.add(marketValue);
                        })
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                lastActualTotal = actualTotal;
            } else {
                // No actual snapshot, use last valid value
                actualTotal = lastActualTotal;
            }

            planReturnPct = planTotal.subtract(baseline)
                    .divide(baseline, 6, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(4, RoundingMode.HALF_UP);
            actualReturnPct = actualTotal.subtract(baseline)
                    .divide(baseline, 6, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(4, RoundingMode.HALF_UP);

            // For today's date, use live data from summary
            if (date.equals(today) && summary != null) {
                if (summary.getPlanTotalValue() != null) {
                    planTotal = summary.getPlanTotalValue();
                    planReturnPct = summary.getPlanReturnPercent() != null
                            ? summary.getPlanReturnPercent() : planReturnPct;
                }
                if (summary.getActualTotalValue() != null) {
                    actualTotal = summary.getActualTotalValue();
                    actualReturnPct = summary.getActualReturnPercent() != null
                            ? summary.getActualReturnPercent() : actualReturnPct;
                }
            }

            trend.add(DashboardDTO.TrendPoint.builder()
                    .date(date)
                    .planTotalValue(planTotal)
                    .actualTotalValue(actualTotal)
                    .planReturnPercent(planReturnPct)
                    .actualReturnPercent(actualReturnPct)
                    .build());
        }

        // If today has no snapshot but we have live data, add today's point
        if (!byDate.containsKey(today) && summary != null) {
            BigDecimal planTotal = summary.getPlanTotalValue() != null
                    ? summary.getPlanTotalValue() : lastPlanTotal;
            BigDecimal actualTotal = summary.getActualTotalValue() != null
                    ? summary.getActualTotalValue() : lastActualTotal;
            BigDecimal planReturnPct = summary.getPlanReturnPercent() != null
                    ? summary.getPlanReturnPercent()
                    : planTotal.subtract(baseline).divide(baseline, 6, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100")).setScale(4, RoundingMode.HALF_UP);
            BigDecimal actualReturnPct = summary.getActualReturnPercent() != null
                    ? summary.getActualReturnPercent()
                    : actualTotal.subtract(baseline).divide(baseline, 6, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100")).setScale(4, RoundingMode.HALF_UP);

            trend.add(DashboardDTO.TrendPoint.builder()
                    .date(today)
                    .planTotalValue(planTotal)
                    .actualTotalValue(actualTotal)
                    .planReturnPercent(planReturnPct)
                    .actualReturnPercent(actualReturnPct)
                    .build());
        }

        return trend;
    }

    private List<DashboardDTO.ExecutionLogItem> buildExecutionLog() {
        List<PlanExecution> executions = planExecutionRepository.findAllWithPlan();
        List<ActualTrade> trades = actualTradeRepository.findAll();

        List<DashboardDTO.ExecutionLogItem> log = new ArrayList<>();

        for (PlanExecution ex : executions) {
            String direction = ex.getPlan().getPlanType() == PlanType.BUY ? "买入" : "卖出";
            log.add(DashboardDTO.ExecutionLogItem.builder()
                    .date(ex.getTradeDate())
                    .type("PLAN")
                    .content(String.format("预案 %s 触发 %s @ %s",
                            ex.getPlan() != null ? ex.getPlan().getName() : "N/A",
                            direction,
                            ex.getTriggerPrice() != null ? ex.getTriggerPrice().toString() : "N/A"))
                    .planStatus(ex.getPlan() != null ? ex.getPlan().getStatus() : null)
                    .build());
        }

        for (ActualTrade t : trades) {
            log.add(DashboardDTO.ExecutionLogItem.builder()
                    .date(t.getTradeDate())
                    .type("ACTUAL")
                    .content(String.format("%s %s %s %s股 @ %.2f",
                            t.getStockCode(),
                            t.getDirection(),
                            t.getProfitLoss() != null ? "盈利" : "",
                            t.getQuantity(),
                            t.getPrice()))
                    .direction(t.getDirection())
                    .build());
        }

        log.sort((a, b) -> {
            if (a.getDate() == null && b.getDate() == null) return 0;
            if (a.getDate() == null) return 1;
            if (b.getDate() == null) return -1;
            return b.getDate().compareTo(a.getDate());
        });

        return log.stream().limit(20).toList();
    }

    private DashboardDTO.PlanHoldingItem toPlanHoldingItem(ViewDTO.PlanHoldingDTO h) {
        return DashboardDTO.PlanHoldingItem.builder()
                .planId(h.getPlanId())
                .planName(h.getPlanName())
                .stockCode(h.getStockCode())
                .status(h.getStatus())
                .unrealizedPLAmount(h.getUnrealizedPLAmount())
                .unrealizedPLPercent(h.getUnrealizedPLPercent())
                .holdDays(h.getHoldDays())
                .build();
    }

    private DashboardDTO.ActualHoldingItem toActualHoldingItem(ViewDTO.ActualHoldingDTO h) {
        return DashboardDTO.ActualHoldingItem.builder()
                .stockCode(h.getStockCode())
                .stockName(h.getStockName())
                .unrealizedPLAmount(h.getUnrealizedPLAmount())
                .unrealizedPLPercent(h.getUnrealizedPLPercent())
                .build();
    }
}
