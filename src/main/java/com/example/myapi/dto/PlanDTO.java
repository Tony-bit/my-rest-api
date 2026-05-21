package com.example.myapi.dto;

import com.example.myapi.entity.PlanCycle;
import com.example.myapi.entity.PlanStatus;
import com.example.myapi.entity.TradeDirection;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class PlanDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateRequest {
        @NotBlank(message = "预案名称不能为空")
        @Size(max = 100, message = "预案名称不能超过100字符")
        private String name;

        @NotBlank(message = "股票代码不能为空")
        @Size(max = 20, message = "股票代码不能超过20字符")
        private String stockCode;

        @Size(max = 50, message = "股票名称不能超过50字符")
        private String stockName;

        @NotNull(message = "周期不能为空")
        private PlanCycle cycle;

        private LocalDate validUntil;

        private LocalDate triggerDate;

        private BigDecimal executionQuantity;

        @Valid
        private List<ConditionDTO.CreateRequest> conditions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateRequest {
        @Size(max = 100, message = "预案名称不能超过100字符")
        private String name;

        @Size(max = 50, message = "股票名称不能超过50字符")
        private String stockName;

        private PlanCycle cycle;
        private LocalDate validUntil;
        private LocalDate triggerDate;
        private BigDecimal executionQuantity;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private String name;
        private String stockCode;
        private String stockName;
        private PlanCycle cycle;
        private PlanStatus status;
        private Boolean isLocked;
        private LocalDate validUntil;
        private LocalDate triggerDate;
        private BigDecimal executionQuantity;
        private List<ConditionDTO.Response> conditions;
        private java.time.LocalDateTime createdAt;
        private java.time.LocalDateTime updatedAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ListResponse {
        private Long id;
        private String name;
        private String stockCode;
        private String stockName;
        private PlanCycle cycle;
        private PlanStatus status;
        private Boolean isLocked;
        private LocalDate validUntil;
        private java.time.LocalDateTime createdAt;
    }
}
