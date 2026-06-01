package com.example.myapi.controller;

import com.example.myapi.dto.ApiResponse;
import com.example.myapi.dto.StockIndicatorDTO;
import com.example.myapi.service.StockMonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stock-monitor/indicators")
@RequiredArgsConstructor
public class StockIndicatorController {

    private final StockMonitorService monitorService;

    @GetMapping("/latest/{stockCode}")
    public ApiResponse<StockIndicatorDTO.SnapshotResponse> getLatestSnapshot(@PathVariable String stockCode) {
        return ApiResponse.ok(monitorService.getLatestSnapshot(stockCode));
    }

    @GetMapping("/history/{stockCode}")
    public ApiResponse<List<StockIndicatorDTO.SnapshotResponse>> getRecentSnapshots(
            @PathVariable String stockCode,
            @RequestParam(defaultValue = "30") int limit) {
        return ApiResponse.ok(monitorService.getRecentSnapshots(stockCode, limit));
    }
}
