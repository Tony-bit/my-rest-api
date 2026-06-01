package com.example.myapi.service;

import com.example.myapi.entity.Plan;
import com.example.myapi.entity.PlanCondition;
import com.example.myapi.entity.PlanExecution;
import com.example.myapi.entity.PlanStatus;
import com.example.myapi.entity.PlanType;
import com.example.myapi.entity.DailySnapshot;
import com.example.myapi.entity.ActualTrade;
import com.example.myapi.repository.ActualTradeRepository;
import com.example.myapi.repository.DailySnapshotRepository;
import com.example.myapi.exception.BusinessException;
import com.example.myapi.repository.PlanExecutionRepository;
import com.example.myapi.repository.PlanRepository;
import com.example.myapi.service.TushareService.KLineData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManualTriggerService {

    private final PlanRepository planRepository;
    private final PlanExecutionRepository executionRepository;
    private final ActualTradeRepository actualTradeRepository;
    private final DailySnapshotRepository snapshotRepository;
    private final TushareService tushareService;
    private final PlanExecutionService executionService;
    private final SystemConfigService systemConfigService;

    @Transactional
    public TriggerResult triggerPlans(LocalDate targetDate) {
        List<Plan> plans = planRepository.findByTriggerDateAndStatus(targetDate, PlanStatus.PENDING);
        List<TriggerDetail> details = new ArrayList<>();
        int triggered = 0;
        int skipped = 0;

        for (Plan plan : plans) {
            TriggerDetail detail = processPlan(plan, targetDate, false);
            details.add(detail);
            if ("triggered".equals(detail.status())) {
                triggered++;
            } else {
                skipped++;
            }
        }

        return new TriggerResult(targetDate, plans.size(), triggered, skipped, details);
    }

    private TriggerDetail processPlan(Plan plan, LocalDate targetDate, boolean allowYesterdayFallback) {
        if (executionRepository.existsByPlanIdAndExecutedTrue(plan.getId())) {
            log.info("Plan id={} already executed, skipping", plan.getId());
            return new TriggerDetail(plan.getId(), plan.getStockName(), "already_executed");
        }

        // 使用 validUntil 那天的 K 线数据来判断条件（历史重现场景）
        LocalDate signalDate = plan.getValidUntil();
        if (signalDate == null) {
            signalDate = plan.getTriggerDate();
        }
        if (signalDate == null) {
            signalDate = LocalDate.now();
        }

        Optional<KLineData> kLineOpt = tushareService.getDailyKLine(
                plan.getStockCode(), signalDate, false);
        // 如果没查到数据，兜底查前一天
        if (allowYesterdayFallback && kLineOpt.isEmpty()) {
            LocalDate previousDay = signalDate.minusDays(1);
            log.info("No K-line data for plan id={} on date={}, falling back to previousDay={}",
                    plan.getId(), signalDate, previousDay);
            signalDate = previousDay;
            kLineOpt = tushareService.getDailyKLine(plan.getStockCode(), signalDate, false);
        }

        if (kLineOpt.isEmpty()) {
            log.info("No K-line data for plan id={} on date={}", plan.getId(), signalDate);
            return new TriggerDetail(plan.getId(), plan.getStockName(), "data_unavailable");
        }

        KLineData kLine = kLineOpt.get();

        if (plan.getConditions().isEmpty()) {
            log.info("Plan id={} has no conditions", plan.getId());
            return new TriggerDetail(plan.getId(), plan.getStockName(), "no_conditions");
        }

        PlanCondition cond = plan.getConditions().get(0);
        BigDecimal maValue = null;
        if (cond.getConditionType() == com.example.myapi.entity.ConditionType.MA) {
            maValue = tushareService.calculateMA(plan.getStockCode(), cond.getMaPeriod(), signalDate);
        }

        boolean conditionMet = tushareService.evaluateCondition(cond, plan.getPlanType(), kLine, maValue);

        if (!conditionMet) {
            log.info("Plan id={} conditions not met", plan.getId());
            return new TriggerDetail(plan.getId(), plan.getStockName(), "condition_not_met");
        }

        BigDecimal triggerPrice = determineTriggerPrice(cond, kLine, maValue);

        PlanExecution execution = executionService.recordExecution(
                plan, triggerPrice, kLine.close, maValue, null);

        if (plan.getPlanType() == PlanType.BUY) {
            executionService.transitionState(plan, PlanStatus.HOLDING);
        } else if (plan.getPlanType() == PlanType.SELL) {
            executionService.transitionState(plan, PlanStatus.CLOSED);
            executionService.closeBuyPlan(plan);
        }

        // 触发成功后立即生成快照，确保仪表盘可见最新状态
        generatePlanSnapshot(plan, signalDate, kLine);

        log.info("Plan id={} triggered successfully", plan.getId());
        return new TriggerDetail(plan.getId(), plan.getStockName(), "triggered");
    }

    private void generatePlanSnapshot(Plan plan, LocalDate snapshotDate, KLineData kLine) {
        try {
            BigDecimal baselineCapital = systemConfigService.getSystemConfig().getBaselineCapital();
            BigDecimal planCashBalance = systemConfigService.getPlanAccount().getCashBalance();

            // 删除该预案在该日期的旧快照（盘中快照会被收盘快照覆盖）
            snapshotRepository.deleteByPlanIdAndSnapshotDate(plan.getId(), snapshotDate);

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
                    BigDecimal marketValue = kLine.close.multiply(quantity);
                    BigDecimal totalValue = planCashBalance.add(marketValue);
                    planReturnPct = totalValue.subtract(baselineCapital)
                            .divide(baselineCapital, 6, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(4, RoundingMode.HALF_UP);
                }
            }

            BigDecimal totalHoldingValue = planCashBalance.add(
                    kLine.close.multiply(plan.getExecutionQuantity() != null ? plan.getExecutionQuantity() : BigDecimal.ZERO));
            boolean hasActualTrade = !actualTradeRepository.findByStockCode(plan.getStockCode()).isEmpty();

            DailySnapshot snapshot = DailySnapshot.builder()
                    .planId(plan.getId())
                    .snapshotDate(snapshotDate)
                    .stockCode(plan.getStockCode())
                    .stockName(plan.getStockName())
                    .planStatus(plan.getStatus())
                    .hasActualTrade(hasActualTrade)
                    .closePrice(kLine.close)
                    .highPrice(kLine.high)
                    .lowPrice(kLine.low)
                    .planCashBalance(planCashBalance)
                    .planMarketValue(kLine.close.multiply(
                            plan.getExecutionQuantity() != null ? plan.getExecutionQuantity() : BigDecimal.ZERO))
                    .planTotalValue(totalHoldingValue)
                    .planReturnPercent(planReturnPct)
                    .build();

            snapshotRepository.save(snapshot);
            log.info("Generated snapshot for plan id={} on date={}", plan.getId(), snapshotDate);
        } catch (Exception e) {
            log.warn("Failed to generate snapshot for plan id={} on date={}: {}", plan.getId(), snapshotDate, e.getMessage());
        }
    }

    public record TriggerResult(
            LocalDate targetDate,
            int totalPlans,
            int triggered,
            int skipped,
            List<TriggerDetail> details
    ) {}

    public record TriggerDetail(
            Long planId,
            String stockName,
            String status
    ) {}

    @Transactional
    public TriggerDetail triggerSingle(Long planId, LocalDate targetDate) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new BusinessException("预案不存在: " + planId, 404));

        return processPlan(plan, targetDate, true);
    }

    private BigDecimal determineTriggerPrice(PlanCondition cond, KLineData kLine, BigDecimal maValue) {
        if (cond.getConditionType() == com.example.myapi.entity.ConditionType.PRICE) {
            return cond.getTargetPrice();
        }
        if (cond.getConditionType() == com.example.myapi.entity.ConditionType.MA) {
            return maValue != null ? maValue : kLine.close;
        }
        return kLine.close;
    }
}
