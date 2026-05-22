package com.example.myapi.service;

import com.example.myapi.entity.Plan;
import com.example.myapi.entity.PlanExecution;
import com.example.myapi.entity.PlanStatus;
import com.example.myapi.entity.PlanType;
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
    private final SystemConfigService systemConfigService;

    @Transactional(readOnly = true)
    public List<PlanExecution> getByPlanId(Long planId) {
        if (!planRepository.existsById(planId)) {
            throw new BusinessException("预案不存在: " + planId, 404);
        }
        return executionRepository.findByPlanIdOrderByTradeDateAsc(planId);
    }

    @Transactional
    public PlanExecution recordExecution(Plan plan, BigDecimal triggerPrice,
                                         BigDecimal closePrice, BigDecimal maValue, PlanExecution linkedExecution) {
        BigDecimal tradeAmount = triggerPrice.multiply(plan.getExecutionQuantity());

        if (plan.getPlanType() == PlanType.BUY) {
            BigDecimal currentBalance = systemConfigService.getPlanAccount().getCashBalance();
            if (tradeAmount.compareTo(currentBalance) > 0) {
                throw new BusinessException(
                        String.format("买入金额 %.2f 元超过预案账户现金余额 %.2f 元，无法执行买入",
                                tradeAmount, currentBalance), 400);
            }
            systemConfigService.deductPlanCashBalance(tradeAmount);
            log.info("PlanAccount cashBalance deducted: amount={}, remaining={}",
                    tradeAmount, currentBalance.subtract(tradeAmount));
        } else if (plan.getPlanType() == PlanType.SELL) {
            systemConfigService.addPlanCashBalance(tradeAmount);
            log.info("PlanAccount cashBalance added: amount={}", tradeAmount);
        }

        PlanExecution execution = PlanExecution.builder()
                .plan(plan)
                .tradeDate(LocalDate.now())
                .triggered(true)
                .triggerPrice(triggerPrice)
                .closePrice(closePrice)
                .maValue(maValue)
                .linkedExecution(linkedExecution)
                .executed(true)
                .build();

        PlanExecution saved = executionRepository.save(execution);
        log.info("Recorded execution id={} planId={} planType={} price={}",
                saved.getId(), plan.getId(), plan.getPlanType(), triggerPrice);
        return saved;
    }

    @Transactional
    public void transitionState(Plan plan, PlanStatus newStatus) {
        if (newStatus == PlanStatus.HOLDING) {
            plan.setStatus(PlanStatus.HOLDING);
        } else if (newStatus == PlanStatus.CLOSED) {
            plan.setStatus(PlanStatus.CLOSED);
        }
        planRepository.save(plan);
        log.info("Plan id={} transitioned to status={}", plan.getId(), newStatus);
    }

    @Transactional
    public void closeBuyPlan(Plan sellPlan) {
        Plan buyPlan = sellPlan.getBuyPlan();
        if (buyPlan != null && buyPlan.getStatus() == PlanStatus.HOLDING) {
            buyPlan.setStatus(PlanStatus.CLOSED);
            planRepository.save(buyPlan);
            log.info("Buy plan id={} closed due to sell plan id={} execution", buyPlan.getId(), sellPlan.getId());
        }
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateCurrentReturn(Plan plan, BigDecimal currentPrice) {
        List<PlanExecution> buyExecutions = executionRepository.findByPlanId(plan.getId()).stream()
                .filter(e -> e.getPlan().getPlanType() == PlanType.BUY && e.getTriggered())
                .toList();
        if (buyExecutions.isEmpty()) return BigDecimal.ZERO;

        BigDecimal totalCost = buyExecutions.stream()
                .map(PlanExecution::getTriggerPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avgCost = totalCost.divide(BigDecimal.valueOf(buyExecutions.size()), 4, java.math.RoundingMode.HALF_UP);

        return currentPrice.subtract(avgCost).divide(avgCost, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
}
