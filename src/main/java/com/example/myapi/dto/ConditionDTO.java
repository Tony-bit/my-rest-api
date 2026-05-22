package com.example.myapi.dto;

import com.example.myapi.entity.ConditionType;
import com.example.myapi.entity.PlanCondition;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

public class ConditionDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateRequest {
        @NotNull(message = "条件类型不能为空")
        private ConditionType conditionType;

        private Integer maPeriod;

        private BigDecimal targetPrice;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateRequest {
        private ConditionType conditionType;
        private Integer maPeriod;
        private BigDecimal targetPrice;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private Long planId;
        private ConditionType conditionType;
        private Integer maPeriod;
        private BigDecimal targetPrice;

        public static Response toResponse(PlanCondition cond) {
            return Response.builder()
                    .id(cond.getId())
                    .planId(cond.getPlan().getId())
                    .conditionType(cond.getConditionType())
                    .maPeriod(cond.getMaPeriod())
                    .targetPrice(cond.getTargetPrice())
                    .build();
        }
    }
}
