package com.example.myapi.controller;

import com.example.myapi.dto.ApiResponse;
import com.example.myapi.dto.StockSignalDTO;
import com.example.myapi.service.StockMonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/stock-monitor/signals")
@RequiredArgsConstructor
public class StockSignalController {

    private final StockMonitorService monitorService;

    @GetMapping("/groups/{stockCode}")
    public ApiResponse<List<StockSignalDTO.GroupResponse>> getSignalGroups(@PathVariable String stockCode) {
        return ApiResponse.ok(monitorService.getSignalGroups(stockCode));
    }

    @GetMapping("/details")
    public ApiResponse<List<StockSignalDTO.DetailResponse>> getSignalDetails(
            @RequestParam String stockCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate,
            @RequestParam String signalType) {
        return ApiResponse.ok(monitorService.getSignalDetails(stockCode, tradeDate, signalType));
    }

    @PostMapping("/retry")
    public ApiResponse<Void> retryNotification(
            @RequestParam String stockCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDate,
            @RequestParam String signalType) {
        monitorService.retryNotification(stockCode, tradeDate, signalType);
        return ApiResponse.ok(null, "通知已重试");
    }
}
