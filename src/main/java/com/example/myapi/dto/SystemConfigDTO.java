package com.example.myapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class SystemConfigDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private BigDecimal baselineCapital;
        private LocalDateTime updatedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateRequest {
        private BigDecimal baselineCapital;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BaselineCapitalResponse {
        private BigDecimal baselineCapital;
        private BigDecimal planCashBalance;
        private BigDecimal actualCashBalance;
        private String message;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlanAccountDTO {
        private Long id;
        private BigDecimal cashBalance;
        private java.time.LocalDateTime updatedAt;
    }
}
