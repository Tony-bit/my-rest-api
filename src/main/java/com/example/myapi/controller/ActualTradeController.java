package com.example.myapi.controller;

import com.example.myapi.dto.ActualTradeDTO;
import com.example.myapi.dto.ApiResponse;
import com.example.myapi.entity.TradeDirection;
import com.example.myapi.service.ActualTradeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/actual-trades")
@RequiredArgsConstructor
public class ActualTradeController {

    private final ActualTradeService tradeService;

    @PostMapping
    public ApiResponse<ActualTradeDTO.Response> create(
            @Valid @RequestBody ActualTradeDTO.CreateRequest request) {
        return ApiResponse.ok(tradeService.create(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<ActualTradeDTO.Response> getById(@PathVariable Long id) {
        return ApiResponse.ok(tradeService.getById(id));
    }

    @GetMapping
    public ApiResponse<List<ActualTradeDTO.Response>> list(
            @RequestParam(required = false) String stockCode,
            @RequestParam(required = false) TradeDirection direction,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResponse.ok(tradeService.list(stockCode, direction, startDate, endDate));
    }

    @PutMapping("/{id}")
    public ApiResponse<ActualTradeDTO.Response> update(
            @PathVariable Long id,
            @Valid @RequestBody ActualTradeDTO.UpdateRequest request) {
        return ApiResponse.ok(tradeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        tradeService.delete(id);
        return ApiResponse.ok(null, "删除成功");
    }
}
