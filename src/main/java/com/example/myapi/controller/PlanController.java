package com.example.myapi.controller;

import com.example.myapi.dto.ApiResponse;
import com.example.myapi.dto.ConditionDTO;
import com.example.myapi.dto.PlanDTO;
import com.example.myapi.entity.PlanStatus;
import com.example.myapi.service.ManualTriggerService;
import com.example.myapi.service.PlanConditionService;
import com.example.myapi.service.PlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/plans")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;
    private final PlanConditionService conditionService;
    private final ManualTriggerService manualTriggerService;

    @PostMapping
    public ApiResponse<PlanDTO.Response> create(@Valid @RequestBody PlanDTO.CreateRequest request) {
        return ApiResponse.ok(planService.create(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<PlanDTO.Response> getById(@PathVariable Long id) {
        return ApiResponse.ok(planService.getById(id));
    }

    @GetMapping
    public ApiResponse<List<PlanDTO.ListResponse>> list(
            @RequestParam(required = false) PlanStatus status,
            @RequestParam(required = false) String stockCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate triggerDate) {
        return ApiResponse.ok(planService.list(status, stockCode, triggerDate));
    }

    @PutMapping("/{id}")
    public ApiResponse<PlanDTO.Response> update(
            @PathVariable Long id,
            @Valid @RequestBody PlanDTO.UpdateRequest request) {
        return ApiResponse.ok(planService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        planService.delete(id);
        return ApiResponse.ok(null, "删除成功");
    }

    @PostMapping("/{planId}/conditions")
    public ApiResponse<ConditionDTO.Response> createCondition(
            @PathVariable Long planId,
            @Valid @RequestBody ConditionDTO.CreateRequest request) {
        return ApiResponse.ok(conditionService.create(planId, request), "条件添加成功");
    }

    @GetMapping("/{planId}/conditions")
    public ApiResponse<List<ConditionDTO.Response>> listConditions(@PathVariable Long planId) {
        return ApiResponse.ok(conditionService.listByPlan(planId));
    }

    @PutMapping("/{planId}/conditions/{conditionId}")
    public ApiResponse<ConditionDTO.Response> updateCondition(
            @PathVariable Long planId,
            @PathVariable Long conditionId,
            @Valid @RequestBody ConditionDTO.UpdateRequest request) {
        return ApiResponse.ok(conditionService.update(planId, conditionId, request));
    }

    @DeleteMapping("/{planId}/conditions/{conditionId}")
    public ApiResponse<Void> deleteCondition(
            @PathVariable Long planId,
            @PathVariable Long conditionId) {
        conditionService.delete(planId, conditionId);
        return ApiResponse.ok(null, "条件删除成功");
    }

    @PostMapping("/{id}/trigger")
    public ApiResponse<ManualTriggerService.TriggerDetail> triggerSingle(
            @PathVariable Long id,
            @RequestBody(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate) {
        ManualTriggerService.TriggerDetail detail = manualTriggerService.triggerSingle(id, targetDate);
        return ApiResponse.ok(detail);
    }
}
