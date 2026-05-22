package com.example.myapi.service;

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
import java.util.List;
import java.util.Optional;

/**
 * 手动生成每日快照的服务。
 * 当用户录入实盘数据后，可立即触发快照生成，使对比分析界面即时可见。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManualSnapshotService {

    private final DailySnapshotRepository snapshotRepository;
    private final PlanRepository planRepository;
    private final PlanExecutionRepository executionRepository;
    private final ActualTradeRepository tradeRepository;
    private final TushareService tushareService;
    private final SystemConfigService systemConfigService;

    @Transactional
    public SnapshotResult generateSnapshots(LocalDate targetDate) {
        log.info("Manual snapshot generation started for date={}", targetDate);

        int planSnapshots = generatePlanSnapshots(targetDate);
        int actualSnapshots = generateActualTradeSnapshots(targetDate);

        log.info("Manual snapshot generation completed: planSnapshots={}, actualSnapshots={}",
                planSnapshots, actualSnapshots);

        return new SnapshotResult(targetDate, planSnapshots, actualSnapshots);
    }

    private int generatePlanSnapshots(LocalDate targetDate) {
        BigDecimal baselineCapital = systemConfigService.getSystemConfig().getBaselineCapital();
        BigDecimal planCashBalance = systemConfigService.getPlanAccount().getCashBalance();

        List<Plan> activePlans = planRepository.findByStatusIn(
                List.of(PlanStatus.PENDING, PlanStatus.HOLDING));

        int count = 0;
        for (Plan plan : activePlans) {
            // 删除该预案在该日期的旧快照（避免重复）
            snapshotRepository.deleteByPlanIdAndSnapshotDate(plan.getId(), targetDate);

            Optional<KLineData> kDataOpt = tushareService.getDailyKLine(plan.getStockCode(), targetDate, true);
            if (kDataOpt.isEmpty()) {
                log.warn("No K-line data for plan {} stock {} on {}", plan.getId(), plan.getStockCode(), targetDate);
                continue;
            }
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
                            .multiply(plan.getExecutionQuantity() != null ? plan.getExecutionQuantity() : BigDecimal.ONE);
                    BigDecimal marketValue = kData.close.multiply(quantity);
                    BigDecimal totalValue = planCashBalance.add(marketValue);
                    planReturnPct = totalValue.subtract(baselineCapital)
                            .divide(baselineCapital, 6, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(4, RoundingMode.HALF_UP);
                }
            }

            BigDecimal totalHoldingValue = planCashBalance.add(
                    kData.close.multiply(plan.getExecutionQuantity() != null ? plan.getExecutionQuantity() : BigDecimal.ZERO));

            boolean hasActualTrade = !tradeRepository.findByStockCode(plan.getStockCode()).isEmpty();

            DailySnapshot snapshot = DailySnapshot.builder()
                    .planId(plan.getId())
                    .snapshotDate(targetDate)
                    .stockCode(plan.getStockCode())
                    .stockName(plan.getStockName())
                    .planStatus(plan.getStatus())
                    .hasActualTrade(hasActualTrade)
                    .closePrice(kData.close)
                    .highPrice(kData.high)
                    .lowPrice(kData.low)
                    .planCashBalance(planCashBalance)
                    .planMarketValue(kData.close.multiply(
                            plan.getExecutionQuantity() != null ? plan.getExecutionQuantity() : BigDecimal.ZERO))
                    .planTotalValue(totalHoldingValue)
                    .planReturnPercent(planReturnPct)
                    .build();

            snapshotRepository.save(snapshot);
            count++;
            log.debug("Generated plan snapshot for planId={} date={}", plan.getId(), targetDate);
        }

        return count;
    }

    private int generateActualTradeSnapshots(LocalDate targetDate) {
        BigDecimal baselineCapital = systemConfigService.getSystemConfig().getBaselineCapital();
        BigDecimal actualCashBalance = systemConfigService.getActualAccount().getCashBalance();

        List<ActualTrade> allTrades = tradeRepository.findAll();

        int count = 0;
        for (ActualTrade trade : allTrades) {
            // 删除该实盘在该日期的旧快照（避免重复）
            snapshotRepository.deleteByActualTradeIdAndSnapshotDate(trade.getId(), targetDate);

            Optional<KLineData> kDataOpt = tushareService.getDailyKLine(trade.getStockCode(), targetDate, true);
            if (kDataOpt.isEmpty()) {
                log.warn("No K-line data for actual trade {} stock {} on {}",
                        trade.getId(), trade.getStockCode(), targetDate);
                continue;
            }
            KLineData kData = kDataOpt.get();

            List<ActualTrade> buys = tradeRepository.findUnmatchedBuys(trade.getStockCode()).stream()
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
                    .snapshotDate(targetDate)
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
            count++;
        }

        return count;
    }

    public record SnapshotResult(LocalDate date, int planSnapshots, int actualSnapshots) {}
}
