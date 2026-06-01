package com.example.myapi.controller;

import com.example.myapi.dto.ActualAccountDTO;
import com.example.myapi.dto.ApiResponse;
import com.example.myapi.dto.SystemConfigDTO;
import com.example.myapi.entity.ActualAccount;
import com.example.myapi.service.ActualAccountService;
import com.example.myapi.service.SystemConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigService systemConfigService;
    private final ActualAccountService actualAccountService;

    @GetMapping("/system-config")
    public ApiResponse<SystemConfigDTO.Response> getConfig() {
        return ApiResponse.ok(systemConfigService.getResponse());
    }

    @PutMapping("/system-config/baseline-capital")
    public ApiResponse<SystemConfigDTO.BaselineCapitalResponse> updateBaselineCapital(
            @RequestBody SystemConfigDTO.UpdateRequest request) {
        return ApiResponse.ok(systemConfigService.updateBaselineCapital(request.getBaselineCapital()));
    }

    @GetMapping("/plan-account")
    public ApiResponse<SystemConfigDTO.PlanAccountDTO> getPlanAccount() {
        var account = systemConfigService.getPlanAccount();
        return ApiResponse.ok(SystemConfigDTO.PlanAccountDTO.builder()
                .id(account.getId())
                .cashBalance(account.getCashBalance())
                .updatedAt(account.getUpdatedAt())
                .build());
    }

    @PutMapping("/plan-account/cash-balance")
    public ApiResponse<SystemConfigDTO.PlanAccountDTO> updatePlanCashBalance(
            @RequestBody SystemConfigDTO.UpdateCashBalanceRequest request) {
        systemConfigService.updatePlanCashBalance(request.getCashBalance());
        var account = systemConfigService.getPlanAccount();
        return ApiResponse.ok(SystemConfigDTO.PlanAccountDTO.builder()
                .id(account.getId())
                .cashBalance(account.getCashBalance())
                .updatedAt(account.getUpdatedAt())
                .build());
    }

    @GetMapping("/actual-account")
    public ApiResponse<ActualAccountDTO.Response> getActualAccount() {
        return ApiResponse.ok(actualAccountService.getResponse());
    }

    @PutMapping("/actual-account/cash-balance")
    public ApiResponse<ActualAccountDTO.Response> updateActualCashBalance(
            @Valid @RequestBody ActualAccountDTO.UpdateCashBalanceRequest request) {
        return ApiResponse.ok(actualAccountService.updateCashBalance(request.getCashBalance()));
    }
}
