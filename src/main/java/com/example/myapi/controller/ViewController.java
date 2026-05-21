package com.example.myapi.controller;

import com.example.myapi.dto.ApiResponse;
import com.example.myapi.dto.ViewDTO;
import com.example.myapi.service.HoldingsService;
import com.example.myapi.service.PeriodSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/views")
@RequiredArgsConstructor
public class ViewController {

    private final PeriodSummaryService periodSummaryService;
    private final HoldingsService holdingsService;

    @GetMapping("/period-summary")
    public ApiResponse<ViewDTO.PeriodSummaryResponse> periodSummary(
            @RequestParam(defaultValue = "WEEK") String period) {
        return ApiResponse.ok(periodSummaryService.getSummary(period));
    }

    @GetMapping("/holdings")
    public ApiResponse<ViewDTO.HoldingsResponse> holdings(
            @RequestParam(defaultValue = "false") boolean refresh) {
        return ApiResponse.ok(holdingsService.getHoldings(refresh));
    }
}
