package com.example.myapi.scheduler;

import com.example.myapi.entity.*;
import com.example.myapi.service.TushareService.KLineData;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class MarketCloseTaskTestFixtures {

    public static Plan makePlan(PlanStatus status, PlanCycle cycle) {
        Plan plan = Plan.builder()
                .name("测试预案")
                .stockCode("000001")
                .stockName("平安银行")
                .cycle(cycle)
                .status(status)
                .isLocked(false)
                .executionQuantity(new BigDecimal("100"))
                .build();
        setField(plan, "id", 1L);
        setField(plan, "createdAt", LocalDateTime.of(2026, 5, 20, 10, 0));
        setField(plan, "updatedAt", LocalDateTime.of(2026, 5, 20, 10, 0));
        return plan;
    }

    public static PlanCondition makeCondition(Plan plan, TradeDirection direction,
            ConditionType type, BigDecimal targetPrice, boolean active) {
        PlanCondition cond = PlanCondition.builder()
                .conditionType(type)
                .direction(direction)
                .maPeriod(5)
                .targetPrice(targetPrice)
                .isActive(active)
                .build();
        setField(cond, "id", 1L);
        setField(cond, "plan", plan);
        return cond;
    }

    public static KLineData makeKLine(String close) {
        return new KLineData("000001", LocalDate.now(),
                new BigDecimal("10.5"), new BigDecimal("11.5"),
                new BigDecimal("10.5"), new BigDecimal(close), 1_000_000);
    }

    public static void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Exception ignored) {}
    }
}
