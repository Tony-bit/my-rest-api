package com.example.myapi.service;

import com.example.myapi.dto.ConditionDTO;
import com.example.myapi.entity.ConditionType;
import com.example.myapi.entity.Plan;
import com.example.myapi.entity.PlanCondition;
import com.example.myapi.entity.PlanStatus;
import com.example.myapi.exception.BusinessException;
import com.example.myapi.repository.PlanConditionRepository;
import com.example.myapi.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanConditionService {

    private static final Set<Integer> VALID_MA_PERIODS = Set.of(5, 10, 20, 60, 120, 250);

    private final PlanConditionRepository conditionRepository;
    private final PlanRepository planRepository;

    @Transactional
    public ConditionDTO.Response create(Long planId, ConditionDTO.CreateRequest request) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new BusinessException("预案不存在: " + planId, 404));
        if (plan.getStatus() != PlanStatus.PENDING) {
            throw new BusinessException("只有 PENDING 状态的预案可以添加条件", 409);
        }
        if (!plan.getConditions().isEmpty()) {
            throw new BusinessException("每个预案只能有一个条件", 409);
        }
        validate(request);

        PlanCondition cond = PlanCondition.builder()
                .plan(plan)
                .conditionType(request.getConditionType())
                .maPeriod(request.getMaPeriod())
                .targetPrice(request.getTargetPrice())
                .build();

        PlanCondition saved = conditionRepository.save(cond);
        log.info("Created condition id={} for planId={}", saved.getId(), planId);
        return ConditionDTO.Response.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ConditionDTO.Response> listByPlan(Long planId) {
        if (!planRepository.existsById(planId)) {
            throw new BusinessException("预案不存在: " + planId, 404);
        }
        return conditionRepository.findByPlanId(planId).stream()
                .map(ConditionDTO.Response::toResponse)
                .collect(java.util.stream.Collectors.toList());
    }

    @Transactional
    public ConditionDTO.Response update(Long planId, Long conditionId, ConditionDTO.UpdateRequest request) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new BusinessException("预案不存在: " + planId, 404));
        if (plan.getStatus() != PlanStatus.PENDING) {
            throw new BusinessException("只有 PENDING 状态的预案可以编辑条件", 409);
        }
        PlanCondition cond = conditionRepository.findById(conditionId)
                .orElseThrow(() -> new BusinessException("条件不存在: " + conditionId, 404));
        if (!cond.getPlan().getId().equals(planId)) {
            throw new BusinessException("该条件不属于指定预案", 400);
        }

        if (request.getConditionType() != null) cond.setConditionType(request.getConditionType());
        if (request.getMaPeriod() != null) cond.setMaPeriod(request.getMaPeriod());
        if (request.getTargetPrice() != null) cond.setTargetPrice(request.getTargetPrice());

        if (cond.getConditionType() == ConditionType.MA && cond.getMaPeriod() != null
                && !VALID_MA_PERIODS.contains(cond.getMaPeriod())) {
            throw new BusinessException("MA 均线周期必须是 5/10/20/60/120/250 之一");
        }
        if (cond.getConditionType() == ConditionType.PRICE && cond.getTargetPrice() != null
                && cond.getTargetPrice().signum() <= 0) {
            throw new BusinessException("PRICE 类型的目标价格必须大于 0");
        }

        ConditionDTO.Response resp = ConditionDTO.Response.toResponse(conditionRepository.save(cond));
        log.info("Updated condition id={}", conditionId);
        return resp;
    }

    @Transactional
    public void delete(Long planId, Long conditionId) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new BusinessException("预案不存在: " + planId, 404));
        if (plan.getStatus() != PlanStatus.PENDING) {
            throw new BusinessException("只有 PENDING 状态的预案可以删除条件", 409);
        }
        PlanCondition cond = conditionRepository.findById(conditionId)
                .orElseThrow(() -> new BusinessException("条件不存在: " + conditionId, 404));
        if (!cond.getPlan().getId().equals(planId)) {
            throw new BusinessException("该条件不属于指定预案", 400);
        }
        conditionRepository.delete(cond);
        log.info("Deleted condition id={}", conditionId);
    }

    private void validate(ConditionDTO.CreateRequest request) {
        if (request.getConditionType() == ConditionType.MA) {
            if (request.getMaPeriod() == null || !VALID_MA_PERIODS.contains(request.getMaPeriod())) {
                throw new BusinessException("MA 均线周期必须是 5/10/20/60/120/250 之一");
            }
        }
        if (request.getConditionType() == ConditionType.PRICE) {
            if (request.getTargetPrice() == null || request.getTargetPrice().signum() <= 0) {
                throw new BusinessException("PRICE 类型的目标价格必须大于 0");
            }
        }
    }
}
