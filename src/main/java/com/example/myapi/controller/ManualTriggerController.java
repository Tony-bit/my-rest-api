package com.example.myapi.controller;

import com.example.myapi.dto.ApiResponse;
import com.example.myapi.service.ManualTriggerService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ManualTriggerController {

    private final ManualTriggerService manualTriggerService;

    @PostMapping("/trigger")
    public ApiResponse<ManualTriggerService.TriggerResult> trigger(
            @RequestBody @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate) {
        ManualTriggerService.TriggerResult result = manualTriggerService.triggerPlans(targetDate);
        return ApiResponse.ok(result);
    }
}
