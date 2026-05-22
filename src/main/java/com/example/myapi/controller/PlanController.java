package com.example.myapi.controller;

import com.example.myapi.dto.ApiResponse;
import com.example.myapi.dto.PlanDTO;
import com.example.myapi.dto.TriggerRequest;
import com.example.myapi.entity.PlanStatus;
import com.example.myapi.service.ManualTriggerService;
import com.example.myapi.service.PlanConditionService;
import com.example.myapi.service.PlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/sell")
    public ApiResponse<PlanDTO.Response> createSellPlan(@Valid @RequestBody PlanDTO.CreateSellPlanRequest request) {
        return ApiResponse.ok(planService.createSellPlan(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<PlanDTO.Response> getById(@PathVariable Long id) {
        return ApiResponse.ok(planService.getById(id));
    }

    @GetMapping
    public ApiResponse<List<PlanDTO.ListResponse>> list(
            @RequestParam(required = false) PlanStatus status,
            @RequestParam(required = false) String stockCode,
            @RequestParam(required = false) Long tradePlanId) {
        return ApiResponse.ok(planService.list(status, stockCode, tradePlanId));
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

    @PostMapping("/{id}/trigger")
    public ApiResponse<ManualTriggerService.TriggerDetail> triggerSingle(
            @PathVariable Long id,
            @RequestBody(required = false) TriggerRequest request) {
        ManualTriggerService.TriggerDetail detail = manualTriggerService.triggerSingle(id, request != null ? request.getTargetDate() : null);
        return ApiResponse.ok(detail);
    }
}
