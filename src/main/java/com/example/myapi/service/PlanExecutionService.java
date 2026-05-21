package com.example.myapi.service;

import com.example.myapi.entity.Plan;
import com.example.myapi.entity.PlanExecution;
import com.example.myapi.entity.PlanStatus;
import com.example.myapi.entity.TradeDirection;
import com.example.myapi.exception.BusinessException;
import com.example.myapi.repository.PlanExecutionRepository;
import com.example.myapi.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanExecutionService {

    private final PlanExecutionRepository executionRepository;
    private final PlanRepository planRepository;

    @Transactional(readOnly = true)
    public List<PlanExecution> getByPlanId(Long planId) {
        if (!planRepository.existsById(planId)) {
            throw new BusinessException("预案不存在: " + planId, 404);
        }
        return executionRepository.findByPlanIdOrderByTradeDateAsc(planId);
    }

    @Transactional
    public PlanExecution recordExecution(Plan plan, TradeDirection direction, BigDecimal triggerPrice,
                                          BigDecimal closePrice, BigDecimal maValue, Long conditionId) {
        PlanExecution execution = PlanExecution.builder()
                .plan(plan)
                .tradeDate(LocalDate.now())
                .direction(direction)
                .triggered(true)
                .triggerPrice(triggerPrice)
                .closePrice(closePrice)
                .maValue(maValue)
                .executed(true)
                .build();
        if (conditionId != null) {
            execution.setCondition(plan.getConditions().stream()
                    .filter(c -> c.getId().equals(conditionId))
                    .findFirst()
                    .orElse(null));
        }
        PlanExecution saved = executionRepository.save(execution);
        log.info("Recorded execution id={} planId={} direction={} price={}",
                saved.getId(), plan.getId(), direction, triggerPrice);
        return saved;
    }

    @Transactional
    public void transitionState(Plan plan, PlanStatus newStatus) {
        if (newStatus == PlanStatus.HOLDING) {
            plan.setStatus(PlanStatus.HOLDING);
            plan.setIsLocked(true);
        } else if (newStatus == PlanStatus.CLOSED) {
            plan.setStatus(PlanStatus.CLOSED);
        }
        planRepository.save(plan);
        log.info("Plan id={} transitioned to status={}", plan.getId(), newStatus);
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateCurrentReturn(Plan plan, BigDecimal currentPrice) {
        List<PlanExecution> buys = executionRepository.findByPlanId(plan.getId()).stream()
                .filter(e -> e.getDirection() == TradeDirection.BUY && e.getTriggered())
                .toList();
        if (buys.isEmpty()) return BigDecimal.ZERO;

        BigDecimal totalCost = buys.stream()
                .map(e -> e.getTriggerPrice())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avgCost = totalCost.divide(BigDecimal.valueOf(buys.size()), 4, java.math.RoundingMode.HALF_UP);

        return currentPrice.subtract(avgCost).divide(avgCost, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
}
