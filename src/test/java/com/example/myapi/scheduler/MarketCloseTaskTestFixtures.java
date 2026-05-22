package com.example.myapi.scheduler;

import com.example.myapi.entity.*;
import com.example.myapi.service.TushareService.KLineData;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;

public class MarketCloseTaskTestFixtures {

    public static Plan makeBuyPlan(PlanStatus status, PlanCycle cycle) {
        Plan plan = Plan.builder()
                .name("测试买入预案")
                .stockCode("000001")
                .stockName("平安银行")
                .cycle(cycle)
                .planType(PlanType.BUY)
                .status(status)
                .executionQuantity(new BigDecimal("100"))
                .build();
        setField(plan, "id", 1L);
        setField(plan, "tradePlanId", 1L);
        setField(plan, "createdAt", LocalDateTime.of(2026, 5, 20, 10, 0));
        setField(plan, "updatedAt", LocalDateTime.of(2026, 5, 20, 10, 0));
        setField(plan, "conditions", new ArrayList<>());
        setField(plan, "executions", new ArrayList<>());
        return plan;
    }

    public static Plan makeSellPlan(Plan buyPlan, PlanStatus status) {
        Plan sellPlan = Plan.builder()
                .name("测试卖出预案")
                .stockCode(buyPlan.getStockCode())
                .stockName(buyPlan.getStockName())
                .cycle(buyPlan.getCycle())
                .planType(PlanType.SELL)
                .status(status)
                .tradePlanId(buyPlan.getId())
                .buyPlan(buyPlan)
                .executionQuantity(buyPlan.getExecutionQuantity())
                .build();
        setField(sellPlan, "id", 2L);
        setField(sellPlan, "createdAt", LocalDateTime.now());
        setField(sellPlan, "updatedAt", LocalDateTime.now());
        setField(sellPlan, "conditions", new ArrayList<>());
        setField(sellPlan, "executions", new ArrayList<>());
        return sellPlan;
    }

    public static PlanCondition makeCondition(Plan plan, ConditionType type, BigDecimal targetPrice) {
        PlanCondition cond = PlanCondition.builder()
                .conditionType(type)
                .maPeriod(5)
                .targetPrice(targetPrice)
                .build();
        setField(cond, "id", 1L);
        setField(cond, "plan", plan);
        plan.getConditions().add(cond);
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
