package com.example.myapi.controller;
import com.example.myapi.service.ShengyiReviewService;
import com.example.myapi.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.*;

@RestController @RequiredArgsConstructor
@RequestMapping("/api/stock-monitor/shengyi-review")
public class ShengyiReviewController {
    private final ShengyiReviewService service;
    @GetMapping("/preview")
    public ApiResponse<ShengyiReviewService.Preview> preview(){
        return ApiResponse.ok(service.preview(LocalDate.now(ZoneId.of("Asia/Shanghai"))));
    }
}
