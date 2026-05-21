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

public class ViewDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PeriodSummaryResponse {
        private String period;
        private PlanSummary planSummary;
        private ActualSummary actualSummary;
        private BigDecimal gapPercent;
        private java.util.List<PlanPeriodItem> planList;
        private java.util.List<ActualPeriodItem> actualList;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlanSummary {
        private Integer totalTrades;
        private Integer pendingCount;
        private BigDecimal totalReturn;
        private BigDecimal avgReturn;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ActualSummary {
        private Integer totalTrades;
        private BigDecimal totalReturn;
        private BigDecimal avgReturn;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlanPeriodItem {
        private Long planId;
        private String name;
        private String stockCode;
        private PlanStatus status;
        private BigDecimal returnPercent;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ActualPeriodItem {
        private Long id;
        private String stockCode;
        private TradeDirection direction;
        private LocalDate tradeDate;
        private BigDecimal profitLossPercent;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HoldingsResponse {
        private BigDecimal baselineCapital;
        private BigDecimal planCashBalance;
        private BigDecimal actualCashBalance;
        private Summary summary;
        private java.util.List<PlanHoldingDTO> planHoldings;
        private java.util.List<ActualHoldingDTO> actualHoldings;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Summary {
        private BigDecimal totalPlanUnrealizedPL;
        private BigDecimal totalPlanUnrealizedPLPercent;
        private BigDecimal totalActualUnrealizedPL;
        private BigDecimal totalActualUnrealizedPLPercent;
        private BigDecimal holdingGap;
        private BigDecimal planTotalValue;
        private BigDecimal actualTotalValue;
        private BigDecimal planReturnPercent;
        private BigDecimal actualReturnPercent;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlanHoldingDTO {
        private Long planId;
        private String planName;
        private String stockCode;
        private String stockName;
        private PlanStatus status;
        private BigDecimal costPrice;
        private BigDecimal quantity;
        private BigDecimal currentPrice;
        private BigDecimal unrealizedPLAmount;
        private BigDecimal unrealizedPLPercent;
        private Integer holdDays;
        private BigDecimal highPrice;
        private BigDecimal lowPrice;
        private BigDecimal closePrice;
        private LocalDate entryDate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ActualHoldingDTO {
        private String stockCode;
        private String stockName;
        private BigDecimal quantity;
        private BigDecimal avgCostPrice;
        private BigDecimal currentPrice;
        private BigDecimal unrealizedPLAmount;
        private BigDecimal unrealizedPLPercent;
    }
}
