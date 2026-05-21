package com.example.myapi.service;

import com.example.myapi.entity.Plan;
import com.example.myapi.entity.PlanExecution;
import com.example.myapi.entity.PlanStatus;
import com.example.myapi.entity.TradeDirection;
import com.example.myapi.repository.PlanExecutionRepository;
import com.example.myapi.repository.PlanRepository;
import com.example.myapi.service.TushareService.KLineData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
            TriggerDetail detail = processPlan(plan, targetDate);
            details.add(detail);
            if ("triggered".equals(detail.status())) {
                triggered++;
            } else {
                skipped++;
            }
        }

        return new TriggerResult(targetDate, plans.size(), triggered, skipped, details);
    }

    private TriggerDetail processPlan(Plan plan, LocalDate targetDate) {
        // Idempotency check
        if (executionRepository.existsByPlanIdAndExecutedTrue(plan.getId())) {
            log.info("Plan id={} already executed, skipping", plan.getId());
            return new TriggerDetail(plan.getId(), plan.getStockName(), "already_executed");
        }

        // Fetch K-line data
        Optional<KLineData> kLineOpt = tushareService.getDailyKLine(
                plan.getStockCode(), targetDate, false);

        if (kLineOpt.isEmpty()) {
            log.info("No K-line data for plan id={} on date={}", plan.getId(), targetDate);
            return new TriggerDetail(plan.getId(), plan.getStockName(), "data_unavailable");
        }

        KLineData kLine = kLineOpt.get();

        // Evaluate conditions
        boolean allConditionsMet = plan.getConditions().stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                .allMatch(c -> tushareService.evaluateCondition(c, kLine, null));

        if (!allConditionsMet) {
            log.info("Plan id={} conditions not met", plan.getId());
            return new TriggerDetail(plan.getId(), plan.getStockName(), "condition_not_met");
        }

        // Execute plan
        TradeDirection direction = plan.getConditions().stream()
                .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                .findFirst()
                .map(c -> c.getDirection())
                .orElse(TradeDirection.BUY);

        PlanExecution execution = executionService.recordExecution(
                plan, direction,
                kLine.close, kLine.close,
                null, null);

        executionService.transitionState(plan, PlanStatus.HOLDING);

        log.info("Plan id={} triggered successfully", plan.getId());
        return new TriggerDetail(plan.getId(), plan.getStockName(), "triggered");
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
}
