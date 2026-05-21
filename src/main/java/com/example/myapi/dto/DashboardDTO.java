package com.example.myapi.dto;

import com.example.myapi.entity.PlanStatus;
import com.example.myapi.entity.TradeDirection;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class DashboardDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private BigDecimal baselineCapital;
        private Kpis kpis;
        private List<TrendPoint> trend;
        private List<PlanHoldingItem> planHoldings;
        private List<ActualHoldingItem> actualHoldings;
        private List<ExecutionLogItem> executionLog;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Kpis {
        private BigDecimal planReturnPercent;
        private BigDecimal actualReturnPercent;
        private BigDecimal holdingGap;
        private BigDecimal planCashBalance;
        private BigDecimal actualCashBalance;
        private Integer activePlanCount;
        private Integer holdingPlanCount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TrendPoint {
        private LocalDate date;
        private BigDecimal planReturnPercent;
        private BigDecimal actualReturnPercent;
        private BigDecimal planTotalValue;
        private BigDecimal actualTotalValue;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlanHoldingItem {
        private Long planId;
        private String planName;
        private String stockCode;
        private PlanStatus status;
        private BigDecimal unrealizedPLAmount;
        private BigDecimal unrealizedPLPercent;
        private Integer holdDays;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ActualHoldingItem {
        private String stockCode;
        private String stockName;
        private BigDecimal unrealizedPLAmount;
        private BigDecimal unrealizedPLPercent;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ExecutionLogItem {
        private LocalDate date;
        private String type;
        private String content;
        private PlanStatus planStatus;
        private TradeDirection direction;
    }
}
