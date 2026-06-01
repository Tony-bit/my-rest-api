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

    BigDecimal determineTriggerPrice(PlanCondition cond, KLineData kData, BigDecimal maValue) {
        if (cond.getConditionType() == ConditionType.PRICE) {
            return cond.getTargetPrice();
        }
        if (cond.getConditionType() == ConditionType.MA) {
            return maValue != null ? maValue : kData.close;
        }
        return kData.close;
    }

    void evaluateTriggerConditions(LocalDate today) {
        evaluateBuyPlans(today);
        evaluateSellPlans(today);
    }

    void evaluateBuyPlans(LocalDate today) {
        List<Plan> pendingBuyPlans = planRepository.findByPlanTypeAndStatus(PlanType.BUY, PlanStatus.PENDING);
        for (Plan plan : pendingBuyPlans) {
            if (isOutsideValidityWindow(plan, today)) {
                continue;
            }

            if (plan.getConditions().isEmpty()) {
                log.info("BUY Plan id={} has no conditions", plan.getId());
                continue;
            }

            Optional<KLineData> kDataOpt = tushareService.getDailyKLine(plan.getStockCode(), today, true);
            if (kDataOpt.isEmpty()) {
                log.warn("No K-line data for stock {} on {}", plan.getStockCode(), today);
                continue;
            }
            KLineData kData = kDataOpt.get();

            PlanCondition cond = plan.getConditions().get(0);
            BigDecimal maValue = null;
            if (cond.getConditionType() == ConditionType.MA) {
                maValue = tushareService.calculateMA(plan.getStockCode(), cond.getMaPeriod(), today);
            }

            if (tushareService.evaluateCondition(cond, PlanType.BUY, kData, maValue)) {
                BigDecimal triggerPrice = determineTriggerPrice(cond, kData, maValue);
                executionService.recordExecution(plan, triggerPrice, kData.close, maValue, null);
                executionService.transitionState(plan, PlanStatus.HOLDING);
                log.info("BUY Plan id={} triggered at price={}, condition type={}", plan.getId(), triggerPrice, cond.getConditionType());
            }
        }
    }

    void evaluateSellPlans(LocalDate today) {
        List<Plan> pendingSellPlans = planRepository.findByPlanTypeAndStatus(PlanType.SELL, PlanStatus.PENDING);
        for (Plan sellPlan : pendingSellPlans) {
            if (isOutsideValidityWindow(sellPlan, today)) {
                continue;
            }

            Plan buyPlan = sellPlan.getBuyPlan();
            if (buyPlan == null || buyPlan.getStatus() != PlanStatus.HOLDING) {
                log.debug("SELL Plan id={} skipped: linked BUY plan not in HOLDING", sellPlan.getId());
                continue;
            }

            if (sellPlan.getConditions().isEmpty()) {
                log.info("SELL Plan id={} has no conditions", sellPlan.getId());
                continue;
            }

            Optional<KLineData> kDataOpt = tushareService.getDailyKLine(sellPlan.getStockCode(), today, true);
            if (kDataOpt.isEmpty()) {
                log.warn("No K-line data for stock {} on {}", sellPlan.getStockCode(), today);
                continue;
            }
            KLineData kData = kDataOpt.get();

            PlanCondition cond = sellPlan.getConditions().get(0);
            BigDecimal maValue = null;
            if (cond.getConditionType() == ConditionType.MA) {
                maValue = tushareService.calculateMA(sellPlan.getStockCode(), cond.getMaPeriod(), today);
            }

            if (tushareService.evaluateCondition(cond, PlanType.SELL, kData, maValue)) {
                BigDecimal triggerPrice = determineTriggerPrice(cond, kData, maValue);
                PlanExecution sellExecution = executionService.recordExecution(sellPlan, triggerPrice, kData.close, maValue, null);
                executionService.transitionState(sellPlan, PlanStatus.CLOSED);
                executionService.closeBuyPlan(sellPlan);
                log.info("SELL Plan id={} triggered at price={}, linked BUY plan closed", sellPlan.getId(), triggerPrice);
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
                List<PlanExecution> buyExecutions = executionRepository.findByPlanId(plan.getId()).stream()
                        .filter(e -> e.getPlan().getPlanType() == PlanType.BUY)
                        .toList();
                if (!buyExecutions.isEmpty()) {
                    BigDecimal totalCost = buyExecutions.stream()
                            .map(PlanExecution::getTriggerPrice)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal quantity = BigDecimal.valueOf(buyExecutions.size())
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
