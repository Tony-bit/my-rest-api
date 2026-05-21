package com.example.myapi.dto;

import com.example.myapi.entity.PlanStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class SnapshotDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private Long planId;
        private Long actualTradeId;
        private LocalDate snapshotDate;
        private String stockCode;
        private String stockName;
        private PlanStatus planStatus;
        private BigDecimal planReturn;
        private BigDecimal planReturnPercent;
        private Boolean hasActualTrade;
        private BigDecimal actualReturn;
        private BigDecimal actualReturnPercent;
        private BigDecimal openQuantity;
        private BigDecimal avgCostBasis;
        private BigDecimal closePrice;
        private BigDecimal highPrice;
        private BigDecimal lowPrice;
        private LocalDateTime createdAt;
    }
}
