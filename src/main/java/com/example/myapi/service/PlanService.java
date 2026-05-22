package com.example.myapi.service;

import com.example.myapi.dto.ConditionDTO;
import com.example.myapi.dto.PlanDTO;
import com.example.myapi.entity.Plan;
import com.example.myapi.entity.PlanCondition;
import com.example.myapi.entity.PlanStatus;
import com.example.myapi.entity.PlanType;
import com.example.myapi.exception.BusinessException;
import com.example.myapi.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanService {

    private static final int TRIGGER_DATE_VALIDITY_DAYS = 90;
    private static final Set<Integer> VALID_MA_PERIODS = Set.of(5, 10, 20, 60, 120, 250);

    private final PlanRepository planRepository;

    @Transactional
    public PlanDTO.Response create(PlanDTO.CreateRequest request) {
        validateTriggerDate(request.getTriggerDate());

        Plan plan = Plan.builder()
                .name(request.getName())
                .stockCode(request.getStockCode())
                .stockName(request.getStockName())
                .cycle(request.getCycle())
                .planType(request.getPlanType())
                .validUntil(request.getValidUntil())
                .triggerDate(request.getTriggerDate() != null ? request.getTriggerDate() : LocalDate.now())
                .executionQuantity(request.getExecutionQuantity())
                .build();

        if (request.getCondition() != null) {
            validateCondition(request.getCondition());
            PlanCondition cond = mapToCondition(request.getCondition());
            plan.addCondition(cond);
        }

        Plan saved = planRepository.save(plan);

        if (saved.getPlanType() == PlanType.BUY) {
            saved.setTradePlanId(saved.getId());
            saved = planRepository.save(saved);
        }

        log.info("Created plan id={} stockCode={} planType={}", saved.getId(), saved.getStockCode(), saved.getPlanType());
        return toResponse(saved);
    }

    @Transactional
    public PlanDTO.Response createSellPlan(PlanDTO.CreateSellPlanRequest request) {
        Plan buyPlan = planRepository.findById(request.getBuyPlanId())
                .orElseThrow(() -> new BusinessException("关联的买入预案不存在", 404));

        if (buyPlan.getPlanType() != PlanType.BUY) {
            throw new BusinessException("关联的预案必须是买入预案", 400);
        }

        if (buyPlan.getStatus() != PlanStatus.HOLDING) {
            throw new BusinessException("关联的买入预案尚未建仓，无法创建卖出预案", 400);
        }

        boolean hasActiveSellPlan = planRepository
                .findByPlanTypeAndStatusNot(PlanType.SELL, PlanStatus.EXPIRED)
                .stream()
                .anyMatch(p -> p.getTradePlanId() != null && p.getTradePlanId().equals(buyPlan.getId()));
        if (hasActiveSellPlan) {
            throw new BusinessException("该买入预案已存在有效的卖出预案，需先等其过期", 409);
        }

        validateCondition(request.getCondition());

        Plan sellPlan = Plan.builder()
                .name(request.getName())
                .stockCode(buyPlan.getStockCode())
                .stockName(buyPlan.getStockName())
                .cycle(request.getCycle())
                .planType(PlanType.SELL)
                .tradePlanId(buyPlan.getId())
                .buyPlan(buyPlan)
                .executionQuantity(buyPlan.getExecutionQuantity())
                .validUntil(request.getValidUntil())
                .build();

        PlanCondition cond = mapToCondition(request.getCondition());
        sellPlan.addCondition(cond);

        Plan saved = planRepository.save(sellPlan);
        log.info("Created sell plan id={} linked to buyPlanId={}", saved.getId(), buyPlan.getId());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PlanDTO.Response getById(Long id) {
        return toResponse(findPlanById(id));
    }

    @Transactional(readOnly = true)
    public List<PlanDTO.ListResponse> list(PlanStatus status, String stockCode, Long tradePlanId) {
        List<Plan> plans;
        if (tradePlanId != null) {
            plans = planRepository.findByTradePlanId(tradePlanId);
        } else if (status != null && stockCode != null) {
            plans = planRepository.findByStatusAndStockCode(status, stockCode);
        } else if (status != null) {
            plans = planRepository.findByStatus(status);
        } else if (stockCode != null) {
            plans = planRepository.findByStockCode(stockCode);
        } else {
            plans = planRepository.findAll();
        }
        return plans.stream().map(this::toListResponse).collect(Collectors.toList());
    }

    @Transactional
    public PlanDTO.Response update(Long id, PlanDTO.UpdateRequest request) {
        Plan plan = findPlanById(id);
        if (plan.getStatus() == PlanStatus.CLOSED || plan.getStatus() == PlanStatus.EXPIRED) {
            throw new BusinessException("CLOSED 和 EXPIRED 状态的预案不可编辑", 409);
        }
        if (request.getName() != null) plan.setName(request.getName());
        if (request.getStockName() != null) plan.setStockName(request.getStockName());
        if (request.getCycle() != null) plan.setCycle(request.getCycle());
        if (request.getValidUntil() != null) plan.setValidUntil(request.getValidUntil());
        if (request.getExecutionQuantity() != null) plan.setExecutionQuantity(request.getExecutionQuantity());

        if (request.getCondition() != null) {
            validateCondition(request.getCondition());
            if (plan.getConditions().isEmpty()) {
                PlanCondition newCond = mapToCondition(request.getCondition());
                plan.addCondition(newCond);
            } else {
                PlanCondition existingCond = plan.getConditions().get(0);
                existingCond.setConditionType(request.getCondition().getConditionType());
                existingCond.setMaPeriod(request.getCondition().getMaPeriod());
                existingCond.setTargetPrice(request.getCondition().getTargetPrice());
            }
        }

        Plan saved = planRepository.save(plan);
        log.info("Updated plan id={}", id);
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        Plan plan = findPlanById(id);
        if (plan.getStatus() != PlanStatus.PENDING && plan.getStatus() != PlanStatus.EXPIRED) {
            throw new BusinessException("只有 PENDING 和 EXPIRED 状态的预案可以删除", 409);
        }
        planRepository.delete(plan);
        log.info("Deleted plan id={}", id);
    }

    @Transactional(readOnly = true)
    public List<Plan> findActivePlans() {
        return planRepository.findByStatusIn(List.of(PlanStatus.PENDING, PlanStatus.HOLDING));
    }

    @Transactional(readOnly = true)
    public List<Plan> findPendingBuyPlans() {
        return planRepository.findByPlanTypeAndStatus(PlanType.BUY, PlanStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<Plan> findPendingSellPlans() {
        return planRepository.findByPlanTypeAndStatus(PlanType.SELL, PlanStatus.PENDING);
    }

    public Plan findPlanById(Long id) {
        return planRepository.findById(id)
                .orElseThrow(() -> new BusinessException("预案不存在: " + id, 404));
    }

    private void validateCondition(ConditionDTO.CreateRequest request) {
        if (request.getConditionType() == null) {
            throw new BusinessException("conditionType 不能为空");
        }
        if (request.getConditionType() == com.example.myapi.entity.ConditionType.MA) {
            if (request.getMaPeriod() == null || !VALID_MA_PERIODS.contains(request.getMaPeriod())) {
                throw new BusinessException("MA 均线周期必须是 5/10/20/60/120/250 之一");
            }
        }
        if (request.getConditionType() == com.example.myapi.entity.ConditionType.PRICE) {
            if (request.getTargetPrice() == null || request.getTargetPrice().signum() <= 0) {
                throw new BusinessException("PRICE 类型的目标价格必须大于 0");
            }
        }
    }

    private void validateTriggerDate(LocalDate triggerDate) {
        if (triggerDate == null) return;
        LocalDate earliest = LocalDate.now().minusDays(TRIGGER_DATE_VALIDITY_DAYS);
        if (triggerDate.isBefore(earliest)) {
            throw new BusinessException("triggerDate 必须在最近 " + TRIGGER_DATE_VALIDITY_DAYS + " 天内", 400);
        }
    }

    private PlanCondition mapToCondition(ConditionDTO.CreateRequest request) {
        return PlanCondition.builder()
                .conditionType(request.getConditionType())
                .maPeriod(request.getMaPeriod())
                .targetPrice(request.getTargetPrice())
                .build();
    }

    private PlanDTO.Response toResponse(Plan plan) {
        ConditionDTO.Response condResponse = null;
        if (!plan.getConditions().isEmpty()) {
            condResponse = ConditionDTO.Response.toResponse(plan.getConditions().get(0));
        }
        return PlanDTO.Response.builder()
                .id(plan.getId())
                .name(plan.getName())
                .stockCode(plan.getStockCode())
                .stockName(plan.getStockName())
                .cycle(plan.getCycle())
                .planType(plan.getPlanType())
                .status(plan.getStatus())
                .tradePlanId(plan.getTradePlanId())
                .buyPlanId(plan.getBuyPlan() != null ? plan.getBuyPlan().getId() : null)
                .validUntil(plan.getValidUntil())
                .triggerDate(plan.getTriggerDate())
                .executionQuantity(plan.getExecutionQuantity())
                .condition(condResponse)
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .build();
    }

    private PlanDTO.ListResponse toListResponse(Plan plan) {
        return PlanDTO.ListResponse.builder()
                .id(plan.getId())
                .name(plan.getName())
                .stockCode(plan.getStockCode())
                .stockName(plan.getStockName())
                .cycle(plan.getCycle())
                .planType(plan.getPlanType())
                .status(plan.getStatus())
                .tradePlanId(plan.getTradePlanId())
                .validUntil(plan.getValidUntil())
                .triggerDate(plan.getTriggerDate())
                .createdAt(plan.getCreatedAt())
                .build();
    }
}
