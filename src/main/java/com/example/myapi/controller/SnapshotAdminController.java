package com.example.myapi.controller;

import com.example.myapi.dto.ApiResponse;
import com.example.myapi.service.ManualSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 手动触发快照生成的 Controller。
 * 用于在用户录入实盘数据后，立即生成快照，使对比分析界面即时可见。
 */
@RestController
@RequestMapping("/api/admin/snapshots")
@RequiredArgsConstructor
public class SnapshotAdminController {

    private final ManualSnapshotService manualSnapshotService;

    @PostMapping("/generate")
    public ApiResponse<ManualSnapshotService.SnapshotResult> generateSnapshots(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate targetDate = (date != null) ? date : LocalDate.now();
        ManualSnapshotService.SnapshotResult result = manualSnapshotService.generateSnapshots(targetDate);
        return ApiResponse.ok(result);
    }
}
