package com.example.myapi.scheduler;

import com.example.myapi.entity.*;
import com.example.myapi.repository.*;
import com.example.myapi.service.PlanExecutionService;
import com.example.myapi.service.SystemConfigService;
import com.example.myapi.service.TushareService;
import com.example.myapi.service.TushareService.KLineData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketCloseTask {

    private final PlanRepository planRepository;
    private final PlanExecutionRepository executionRepository;
    private final DailySnapshotRepository snapshotRepository;
    private final ActualTradeRepository actualTradeRepository;
    private final TushareService tushareService;
    private final PlanExecutionService executionService;
    private final SystemConfigService systemConfigService;

    @Scheduled(cron = "0 0 17 * * MON-FRI", zone = "Asia/Shanghai")
    @Transactional
    public void onMarketClose() {
        LocalDate today = LocalDate.now();
        log.info("Market close task started at {}", today);

        if (!tushareService.isTradingDay(today)) {
            log.info("Skipping non-trading day: {}", today);
            return;
        }

        checkAndExpirePlans(today);
        evaluateTriggerConditions(today);
        generatePlanSnapshots(today);
        generateActualTradeSnapshots(today);

        log.info("Market close task completed for {}", today);
    }

    void checkAndExpirePlans(LocalDate today) {
        List<Plan> pendingPlans = planRepository.findByStatus(PlanStatus.PENDING);
        for (Plan plan : pendingPlans) {
            if (isOutsideValidityWindow(plan, today)) {
                plan.setStatus(PlanStatus.EXPIRED);
                planRepository.save(plan);
                log.info("Plan id={} expired (outside validity window)", plan.getId());
            }
        }
    }

    boolean isOutsideValidityWindow(Plan plan, LocalDate today) {
        if (plan.getCycle() == PlanCycle.DAILY) {
            return !today.equals(plan.getCreatedAt().toLocalDate());
        }
        LocalDate validUntil = plan.getValidUntil();
        if (validUntil != null) {
            return today.isAfter(validUntil);
        }
        return false;
    }

    void evaluateTriggerConditions(LocalDate today) {
        List<Plan> activePlans = planRepository.findByStatusIn(List.of(PlanStatus.PENDING, PlanStatus.HOLDING));
        for (Plan plan : activePlans) {
            List<PlanCondition> conditions = plan.getConditions().stream()
                    .filter(PlanCondition::getIsActive)
                    .toList();

            Optional<KLineData> kDataOpt = tushareService.getDailyKLine(plan.getStockCode(), today, true);
            if (kDataOpt.isEmpty()) {
                log.warn("No K-line data for stock {} on {}", plan.getStockCode(), today);
                continue;
            }
            KLineData kData = kDataOpt.get();

            for (PlanCondition cond : conditions) {
                if (plan.getStatus() == PlanStatus.PENDING && cond.getDirection() == TradeDirection.BUY) {
                    BigDecimal maValue = null;
                    if (cond.getConditionType() == ConditionType.MA) {
                        maValue = tushareService.calculateMA(plan.getStockCode(), cond.getMaPeriod(), today);
                    }
                    if (tushareService.evaluateCondition(cond, kData, maValue)) {
                        executionService.recordExecution(plan, TradeDirection.BUY, kData.close,
                                kData.close, maValue, cond.getId());
                        executionService.transitionState(plan, PlanStatus.HOLDING);
                        log.info("Plan id={} BUY triggered at price={}", plan.getId(), kData.close);
                        break;
                    }
                } else if (plan.getStatus() == PlanStatus.HOLDING && cond.getDirection() == TradeDirection.SELL) {
                    BigDecimal maValue = null;
                    if (cond.getConditionType() == ConditionType.MA) {
                        maValue = tushareService.calculateMA(plan.getStockCode(), cond.getMaPeriod(), today);
                    }
                    if (tushareService.evaluateCondition(cond, kData, maValue)) {
                        executionService.recordExecution(plan, TradeDirection.SELL, kData.close,
                                kData.close, maValue, cond.getId());
                        executionService.transitionState(plan, PlanStatus.CLOSED);
                        log.info("Plan id={} SELL triggered at price={}", plan.getId(), kData.close);
                        break;
                    }
                }
            }
        }
    }

    void generatePlanSnapshots(LocalDate today) {
        BigDecimal baselineCapital = systemConfigService.getSystemConfig().getBaselineCapital();
        BigDecimal planCashBalance = systemConfigService.getPlanAccount().getCashBalance();

        List<Plan> activePlans = planRepository.findByStatusIn(List.of(PlanStatus.PENDING, PlanStatus.HOLDING));
        for (Plan plan : activePlans) {
            Optional<KLineData> kDataOpt = tushareService.getDailyKLine(plan.getStockCode(), today, true);
            if (kDataOpt.isEmpty()) continue;
            KLineData kData = kDataOpt.get();

            BigDecimal planReturnPct = BigDecimal.ZERO;

            if (plan.getStatus() == PlanStatus.HOLDING) {
                List<PlanExecution> buys = executionRepository.findByPlanId(plan.getId()).stream()
                        .filter(e -> e.getDirection() == TradeDirection.BUY)
                        .toList();
                if (!buys.isEmpty()) {
                    BigDecimal totalCost = buys.stream()
                            .map(PlanExecution::getTriggerPrice)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal quantity = BigDecimal.valueOf(buys.size())
                            .multiply(plan.getExecutionQuantity());
                    BigDecimal marketValue = kData.close.multiply(quantity);
                    BigDecimal totalValue = planCashBalance.add(marketValue);
                    planReturnPct = totalValue.subtract(baselineCapital)
                            .divide(baselineCapital, 6, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(4, RoundingMode.HALF_UP);
                }
            }

            BigDecimal totalHoldingValue = planCashBalance.add(
                    kData.close.multiply(plan.getExecutionQuantity()));

            boolean hasActualTrade = !actualTradeRepository.findByStockCode(plan.getStockCode()).isEmpty();

            DailySnapshot snapshot = DailySnapshot.builder()
                    .planId(plan.getId())
                    .snapshotDate(today)
                    .stockCode(plan.getStockCode())
                    .stockName(plan.getStockName())
                    .planStatus(plan.getStatus())
                    .hasActualTrade(hasActualTrade)
                    .closePrice(kData.close)
                    .highPrice(kData.high)
                    .lowPrice(kData.low)
                    .planCashBalance(planCashBalance)
                    .planMarketValue(kData.close.multiply(plan.getExecutionQuantity()))
                    .planTotalValue(totalHoldingValue)
                    .planReturnPercent(planReturnPct)
                    .build();

            snapshotRepository.save(snapshot);
            log.debug("Generated plan snapshot for planId={} date={}", plan.getId(), today);
        }
    }

    void generateActualTradeSnapshots(LocalDate today) {
        BigDecimal baselineCapital = systemConfigService.getSystemConfig().getBaselineCapital();
        BigDecimal actualCashBalance = systemConfigService.getActualAccount().getCashBalance();

        List<ActualTrade> allTrades = actualTradeRepository.findAll();

        for (ActualTrade trade : allTrades) {
            Optional<KLineData> kDataOpt = tushareService.getDailyKLine(trade.getStockCode(), today, true);
            if (kDataOpt.isEmpty()) continue;
            KLineData kData = kDataOpt.get();

            List<ActualTrade> buys = actualTradeRepository.findUnmatchedBuys(trade.getStockCode()).stream()
                    .filter(t -> t.getDirection() == TradeDirection.BUY)
                    .toList();
            BigDecimal totalQty = buys.stream()
                    .map(ActualTrade::getQuantity)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal marketValue = kData.close.multiply(totalQty);
            BigDecimal totalValue = actualCashBalance.add(marketValue);
            BigDecimal actualReturnPct = totalValue.subtract(baselineCapital)
                    .divide(baselineCapital, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(4, RoundingMode.HALF_UP);

            DailySnapshot snapshot = DailySnapshot.builder()
                    .actualTradeId(trade.getId())
                    .snapshotDate(today)
                    .stockCode(trade.getStockCode())
                    .stockName(trade.getStockName())
                    .closePrice(kData.close)
                    .highPrice(kData.high)
                    .lowPrice(kData.low)
                    .actualCashBalance(actualCashBalance)
                    .actualMarketValue(marketValue)
                    .actualTotalValue(totalValue)
                    .actualReturnPercent(actualReturnPct)
                    .build();

            snapshotRepository.save(snapshot);
        }
    }
}
