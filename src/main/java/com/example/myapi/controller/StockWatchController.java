package com.example.myapi.controller;

import com.example.myapi.dto.ApiResponse;
import com.example.myapi.dto.StockWatchDTO;
import com.example.myapi.service.StockMonitorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stock-monitor/watches")
@RequiredArgsConstructor
public class StockWatchController {

    private final StockMonitorService monitorService;

    @GetMapping
    public ApiResponse<List<StockWatchDTO.ListResponse>> list() {
        return ApiResponse.ok(monitorService.listWatches());
    }

    @GetMapping("/{id}")
    public ApiResponse<StockWatchDTO.Response> getById(@PathVariable Long id) {
        return ApiResponse.ok(monitorService.getWatch(id));
    }

    @PostMapping
    public ApiResponse<StockWatchDTO.Response> create(@Valid @RequestBody StockWatchDTO.CreateRequest request) {
        return ApiResponse.ok(monitorService.createWatch(request), "股票已添加");
    }

    @PutMapping("/{id}")
    public ApiResponse<StockWatchDTO.Response> update(
            @PathVariable Long id,
            @RequestBody StockWatchDTO.UpdateRequest request) {
        return ApiResponse.ok(monitorService.updateWatch(id, request), "股票信息已更新");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        monitorService.deleteWatch(id);
        return ApiResponse.ok(null, "股票已删除");
    }

    @PostMapping("/{id}/check")
    public ApiResponse<StockWatchDTO.CheckResult> checkNow(@PathVariable Long id) {
        return ApiResponse.ok(monitorService.checkNow(id));
    }
}
