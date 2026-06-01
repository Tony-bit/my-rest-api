package com.example.myapi.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class StockIndicatorDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SnapshotResponse {
        private Long id;
        private String stockCode;
        private LocalDate tradeDate;
        private BigDecimal openPrice;
        private BigDecimal highPrice;
        private BigDecimal lowPrice;
        private BigDecimal closePrice;
        private BigDecimal dif;
        private BigDecimal dea;
        private BigDecimal macd;
        private BigDecimal kdjk;
        private BigDecimal kdjd;
        private BigDecimal kdjj;
        private Long sourceTimestamp;
        private LocalDateTime lastCheckedAt;
    }
}
