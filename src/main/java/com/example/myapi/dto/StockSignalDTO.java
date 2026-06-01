package com.example.myapi.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class StockSignalDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GroupResponse {
        private String stockCode;
        private String signalType;
        private LocalDate tradeDate;
        private LocalDateTime firstTriggerTime;
        private LocalDateTime lastCheckTime;
        private Integer triggerCount;
        private String notificationStatus;
        private String lastFailureReason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DetailResponse {
        private Long id;
        private String stockCode;
        private LocalDate tradeDate;
        private String signalType;
        private String triggerSource;
        private BigDecimal closePrice;
        private BigDecimal jToday;
        private BigDecimal jYesterday;
        private BigDecimal macdToday;
        private BigDecimal macdYesterday;
        private String evaluationSummary;
        private String notificationStatus;
        private Integer notificationAttempts;
        private LocalDateTime notificationSentAt;
        private String notificationFailureReason;
        private LocalDateTime createdAt;
    }
}
