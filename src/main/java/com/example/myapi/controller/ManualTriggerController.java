package com.example.myapi.controller;

import com.example.myapi.dto.ApiResponse;
import com.example.myapi.dto.TriggerRequest;
import com.example.myapi.service.ManualTriggerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ManualTriggerController {

    private final ManualTriggerService manualTriggerService;

    @PostMapping("/trigger")
    public ApiResponse<ManualTriggerService.TriggerResult> trigger(@RequestBody TriggerRequest request) {
        ManualTriggerService.TriggerResult result = manualTriggerService.triggerPlans(request.getTargetDate());
        return ApiResponse.ok(result);
    }
}
