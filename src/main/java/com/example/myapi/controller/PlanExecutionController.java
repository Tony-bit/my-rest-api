package com.example.myapi.controller;

import com.example.myapi.dto.ApiResponse;
import com.example.myapi.entity.PlanExecution;
import com.example.myapi.service.PlanExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/plans/{planId}/executions")
@RequiredArgsConstructor
public class PlanExecutionController {

    private final PlanExecutionService executionService;

    @GetMapping
    public ApiResponse<List<PlanExecution>> list(@PathVariable Long planId) {
        return ApiResponse.ok(executionService.getByPlanId(planId));
    }
}
