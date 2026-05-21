package com.example.myapi.dto;

import com.example.myapi.entity.TradeDirection;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class ActualTradeDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateRequest {
        @NotBlank(message = "股票代码不能为空")
        private String stockCode;

        private String stockName;

        @NotNull(message = "方向不能为空")
        private TradeDirection direction;

        @NotNull(message = "价格不能为空")
        @Positive(message = "价格必须为正数")
        private BigDecimal price;

        @NotNull(message = "数量不能为空")
        @Positive(message = "数量必须为正数")
        private BigDecimal quantity;

        @NotNull(message = "交易日期不能为空")
        private LocalDate tradeDate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateRequest {
        private String stockName;
        private TradeDirection direction;
        private BigDecimal price;
        private BigDecimal quantity;
        private LocalDate tradeDate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private String stockCode;
        private String stockName;
        private TradeDirection direction;
        private BigDecimal price;
        private BigDecimal quantity;
        private LocalDate tradeDate;
        private BigDecimal profitLossAmount;
        private BigDecimal profitLossPercent;
        private Boolean matched;
        private Long matchedBuyId;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}
