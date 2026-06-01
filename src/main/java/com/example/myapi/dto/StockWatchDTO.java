package com.example.myapi.dto;

import com.example.myapi.entity.SignalState;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class StockWatchDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private String stockCode;
        private String stockName;
        private Boolean enabled;
        private SignalState signalState;
        private LocalDate watchStartDate;
        private String lastSignalType;
        private LocalDate lastSignalDate;
        private LocalDateTime lastCheckedAt;
        private String lastCheckStatus;
        private String lastCheckMessage;
        private LocalDateTime createdAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ListResponse {
        private Long id;
        private String stockCode;
        private String stockName;
        private Boolean enabled;
        private SignalState signalState;
        private LocalDate latestTradeDate;
        private String closePrice;
        private String jValue;
        private String macdValue;
        private String lastSignalType;
        private LocalDateTime lastCheckedAt;
        private String lastCheckStatus;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank(message = "Stock code is required")
        @Pattern(regexp = "^[0-9]{6}$", message = "Stock code must be 6 digits")
        private String stockCode;

        private String stockName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        private String stockName;
        private Boolean enabled;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CheckResult {
        private String stockCode;
        private String stockName;
        private LocalDate latestTradeDate;
        private String closePrice;
        private String jToday;
        private String jYesterday;
        private String macdToday;
        private String macdYesterday;
        private SignalState signalState;
        private String signalResult; // "BUY", "SELL", or null
        private String noSignalReason;
        private String checkStatus;
        private String checkMessage;
    }
}
