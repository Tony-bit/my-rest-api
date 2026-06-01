package com.example.myapi.controller;

import com.example.myapi.dto.ApiResponse;
import com.example.myapi.dto.SnapshotDTO;
import com.example.myapi.entity.DailySnapshot;
import com.example.myapi.repository.DailySnapshotRepository;
import com.example.myapi.service.ManualSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/snapshots")
@RequiredArgsConstructor
public class SnapshotController {

    private final DailySnapshotRepository snapshotRepository;
    private final ManualSnapshotService manualSnapshotService;

    @GetMapping
    public ApiResponse<List<SnapshotDTO.Response>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long planId) {
        List<DailySnapshot> snapshots;
        if (date != null) {
            snapshots = snapshotRepository.findBySnapshotDate(date);
        } else if (planId != null) {
            snapshots = snapshotRepository.findByPlanIdOrderBySnapshotDateDesc(planId);
        } else {
            snapshots = snapshotRepository.findAll();
        }

        List<SnapshotDTO.Response> result = snapshots.stream()
                .map(this::toResponse)
                .toList();
        return ApiResponse.ok(result);
    }

    private SnapshotDTO.Response toResponse(DailySnapshot s) {
        return SnapshotDTO.Response.builder()
                .id(s.getId())
                .planId(s.getPlanId())
                .actualTradeId(s.getActualTradeId())
                .snapshotDate(s.getSnapshotDate())
                .stockCode(s.getStockCode())
                .stockName(s.getStockName())
                .planStatus(s.getPlanStatus())
                .planReturn(s.getPlanReturn())
                .planReturnPercent(s.getPlanReturnPercent())
                .hasActualTrade(s.getHasActualTrade())
                .actualReturn(s.getActualReturn())
                .actualReturnPercent(s.getActualReturnPercent())
                .openQuantity(s.getOpenQuantity())
                .avgCostPrice(s.getAvgCostBasis())
                .closePrice(s.getClosePrice())
                .highPrice(s.getHighPrice())
                .lowPrice(s.getLowPrice())
                .planCashBalance(s.getPlanCashBalance())
                .planMarketValue(s.getPlanMarketValue())
                .planTotalValue(s.getPlanTotalValue())
                .planReturnPct(s.getPlanReturnPercent())
                .actualCashBalance(s.getActualCashBalance())
                .actualMarketValue(s.getActualMarketValue())
                .actualTotalValue(s.getActualTotalValue())
                .actualReturnPct(s.getActualReturnPercent())
                .createdAt(s.getCreatedAt())
                .build();
    }

    @PostMapping("/generate")
    public ApiResponse<ManualSnapshotService.SnapshotResult> generate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        ManualSnapshotService.SnapshotResult result = manualSnapshotService.generateSnapshots(date);
        return ApiResponse.ok(result);
    }

    @DeleteMapping("/actual-trades")
    @Transactional
    public ApiResponse<String> deleteActualTradeSnapshots() {
        int deleted = snapshotRepository.deleteAllActualTradeSnapshots();
        return ApiResponse.ok(String.format("已删除 %d 条实盘快照记录", deleted));
    }
}
