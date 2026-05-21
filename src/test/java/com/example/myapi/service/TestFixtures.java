package com.example.myapi.service;

import com.example.myapi.entity.*;
import com.example.myapi.service.TushareService.KLineData;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class TestFixtures {

    public static PlanBuilder planBuilder() {
        return new PlanBuilder();
    }

    public static ConditionBuilder conditionBuilder() {
        return new ConditionBuilder();
    }

    public static ExecutionBuilder executionBuilder() {
        return new ExecutionBuilder();
    }

    public static TradeBuilder tradeBuilder() {
        return new TradeBuilder();
    }

    public static KLineBuilder kLineBuilder() {
        return new KLineBuilder();
    }

    public static class PlanBuilder {
        private Long id = 1L;
        private String name = "测试预案";
        private String stockCode = "000001";
        private String stockName = "平安银行";
        private PlanCycle cycle = PlanCycle.DAILY;
        private PlanStatus status = PlanStatus.PENDING;
        private Boolean isLocked = false;
        private LocalDate validUntil;
        private LocalDate triggerDate;
        private BigDecimal executionQuantity = new BigDecimal("100");
        private LocalDateTime createdAt = LocalDateTime.of(2026, 5, 20, 10, 0);
        private LocalDateTime updatedAt = LocalDateTime.of(2026, 5, 20, 10, 0);

        public PlanBuilder id(Long id) { this.id = id; return this; }
        public PlanBuilder name(String name) { this.name = name; return this; }
        public PlanBuilder stockCode(String stockCode) { this.stockCode = stockCode; return this; }
        public PlanBuilder stockName(String stockName) { this.stockName = stockName; return this; }
        public PlanBuilder cycle(PlanCycle cycle) { this.cycle = cycle; return this; }
        public PlanBuilder status(PlanStatus status) { this.status = status; return this; }
        public PlanBuilder isLocked(Boolean isLocked) { this.isLocked = isLocked; return this; }
        public PlanBuilder validUntil(LocalDate validUntil) { this.validUntil = validUntil; return this; }
        public PlanBuilder triggerDate(LocalDate triggerDate) { this.triggerDate = triggerDate; return this; }
        public PlanBuilder executionQuantity(BigDecimal executionQuantity) { this.executionQuantity = executionQuantity; return this; }
        public PlanBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }

        public Plan build() {
            Plan plan = Plan.builder()
                    .name(name)
                    .stockCode(stockCode)
                    .stockName(stockName)
                    .cycle(cycle)
                    .status(status)
                    .isLocked(isLocked)
                    .validUntil(validUntil)
                    .triggerDate(triggerDate)
                    .executionQuantity(executionQuantity)
                    .build();
            setField(plan, "id", id);
            setField(plan, "createdAt", createdAt);
            setField(plan, "updatedAt", updatedAt);
            return plan;
        }

        private void setField(Object target, String fieldName, Object value) {
            try {
                java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
                f.setAccessible(true);
                f.set(target, value);
            } catch (Exception ignored) {}
        }
    }

    public static class ConditionBuilder {
        private Long id = 1L;
        private Plan plan;
        private ConditionType conditionType = ConditionType.PRICE;
        private TradeDirection direction = TradeDirection.BUY;
        private Integer maPeriod = 5;
        private BigDecimal targetPrice = new BigDecimal("10.00");
        private Boolean isActive = true;

        public ConditionBuilder id(Long id) { this.id = id; return this; }
        public ConditionBuilder plan(Plan plan) { this.plan = plan; return this; }
        public ConditionBuilder conditionType(ConditionType type) { this.conditionType = type; return this; }
        public ConditionBuilder direction(TradeDirection direction) { this.direction = direction; return this; }
        public ConditionBuilder maPeriod(Integer maPeriod) { this.maPeriod = maPeriod; return this; }
        public ConditionBuilder targetPrice(BigDecimal targetPrice) { this.targetPrice = targetPrice; return this; }
        public ConditionBuilder isActive(Boolean isActive) { this.isActive = isActive; return this; }

        public PlanCondition build() {
            Plan p = plan != null ? plan : planBuilder().build();
            PlanCondition cond = PlanCondition.builder()
                    .conditionType(conditionType)
                    .direction(direction)
                    .maPeriod(maPeriod)
                    .targetPrice(targetPrice)
                    .isActive(isActive)
                    .build();
            setField(cond, "id", id);
            setField(cond, "plan", p);
            return cond;
        }

        private void setField(Object target, String fieldName, Object value) {
            try {
                java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
                f.setAccessible(true);
                f.set(target, value);
            } catch (Exception ignored) {}
        }
    }

    public static class ExecutionBuilder {
        private Long id = 1L;
        private Plan plan;
        private LocalDate tradeDate = LocalDate.of(2026, 5, 20);
        private TradeDirection direction = TradeDirection.BUY;
        private Boolean triggered = true;
        private BigDecimal triggerPrice = new BigDecimal("10.00");
        private BigDecimal closePrice = new BigDecimal("10.00");
        private BigDecimal maValue;
        private PlanCondition condition;
        private Boolean executed = true;

        public ExecutionBuilder id(Long id) { this.id = id; return this; }
        public ExecutionBuilder plan(Plan plan) { this.plan = plan; return this; }
        public ExecutionBuilder tradeDate(LocalDate tradeDate) { this.tradeDate = tradeDate; return this; }
        public ExecutionBuilder direction(TradeDirection direction) { this.direction = direction; return this; }
        public ExecutionBuilder triggerPrice(BigDecimal triggerPrice) { this.triggerPrice = triggerPrice; return this; }
        public ExecutionBuilder closePrice(BigDecimal closePrice) { this.closePrice = closePrice; return this; }
        public ExecutionBuilder maValue(BigDecimal maValue) { this.maValue = maValue; return this; }
        public ExecutionBuilder condition(PlanCondition condition) { this.condition = condition; return this; }

        public PlanExecution build() {
            Plan p = plan != null ? plan : planBuilder().build();
            PlanExecution exec = PlanExecution.builder()
                    .tradeDate(tradeDate)
                    .direction(direction)
                    .triggered(triggered)
                    .triggerPrice(triggerPrice)
                    .closePrice(closePrice)
                    .maValue(maValue)
                    .executed(executed)
                    .build();
            setField(exec, "id", id);
            setField(exec, "plan", p);
            if (condition != null) setField(exec, "condition", condition);
            return exec;
        }

        private void setField(Object target, String fieldName, Object value) {
            try {
                java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
                f.setAccessible(true);
                f.set(target, value);
            } catch (Exception ignored) {}
        }
    }

    public static class TradeBuilder {
        private Long id = 1L;
        private String stockCode = "000001";
        private String stockName = "平安银行";
        private TradeDirection direction = TradeDirection.BUY;
        private BigDecimal price = new BigDecimal("10.00");
        private BigDecimal quantity = new BigDecimal("100");
        private LocalDate tradeDate = LocalDate.of(2026, 5, 20);
        private BigDecimal profitLoss;
        private BigDecimal profitLossPercent;
        private Boolean isMatched = false;
        private Long matchedBuyId;

        public TradeBuilder id(Long id) { this.id = id; return this; }
        public TradeBuilder stockCode(String stockCode) { this.stockCode = stockCode; return this; }
        public TradeBuilder stockName(String stockName) { this.stockName = stockName; return this; }
        public TradeBuilder direction(TradeDirection direction) { this.direction = direction; return this; }
        public TradeBuilder price(BigDecimal price) { this.price = price; return this; }
        public TradeBuilder quantity(BigDecimal quantity) { this.quantity = quantity; return this; }
        public TradeBuilder tradeDate(LocalDate tradeDate) { this.tradeDate = tradeDate; return this; }
        public TradeBuilder profitLoss(BigDecimal profitLoss) { this.profitLoss = profitLoss; return this; }
        public TradeBuilder profitLossPercent(BigDecimal profitLossPercent) { this.profitLossPercent = profitLossPercent; return this; }
        public TradeBuilder isMatched(Boolean isMatched) { this.isMatched = isMatched; return this; }
        public TradeBuilder matchedBuyId(Long matchedBuyId) { this.matchedBuyId = matchedBuyId; return this; }

        public ActualTrade build() {
            ActualTrade trade = ActualTrade.builder()
                    .stockCode(stockCode)
                    .stockName(stockName)
                    .direction(direction)
                    .price(price)
                    .quantity(quantity)
                    .tradeDate(tradeDate)
                    .profitLoss(profitLoss)
                    .profitLossPercent(profitLossPercent)
                    .isMatched(isMatched)
                    .matchedBuyId(matchedBuyId)
                    .build();
            setField(trade, "id", id);
            return trade;
        }

        private void setField(Object target, String fieldName, Object value) {
            try {
                java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
                f.setAccessible(true);
                f.set(target, value);
            } catch (Exception ignored) {}
        }
    }

    public static class KLineBuilder {
        private String stockCode = "000001.SZ";
        private LocalDate date = LocalDate.of(2026, 5, 20);
        private BigDecimal open = new BigDecimal("10.50");
        private BigDecimal high = new BigDecimal("11.50");
        private BigDecimal low = new BigDecimal("10.50");
        private BigDecimal close = new BigDecimal("11.00");
        private double volume = 1_000_000;

        public KLineBuilder stockCode(String stockCode) { this.stockCode = stockCode; return this; }
        public KLineBuilder date(LocalDate date) { this.date = date; return this; }
        public KLineBuilder open(BigDecimal open) { this.open = open; return this; }
        public KLineBuilder high(BigDecimal high) { this.high = high; return this; }
        public KLineBuilder low(BigDecimal low) { this.low = low; return this; }
        public KLineBuilder close(BigDecimal close) { this.close = close; return this; }

        public KLineData build() {
            return new KLineData(stockCode, date, open, high, low, close, volume);
        }
    }
}
