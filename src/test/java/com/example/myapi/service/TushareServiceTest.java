package com.example.myapi.service;

import com.example.myapi.config.TushareConfig;
import com.example.myapi.entity.ConditionType;
import com.example.myapi.entity.PlanCondition;
import com.example.myapi.entity.TradeDirection;
import com.example.myapi.service.TushareService.KLineData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TushareServiceTest {

    @Mock
    private TushareConfig tushareConfig;

    private TushareService service;

    @BeforeEach
    void setUp() {
        service = new TushareService(tushareConfig);
    }

    @Test
    void kLineData_publicFields() {
        KLineData kd = new KLineData(
                "000001", LocalDate.of(2026, 5, 20),
                new BigDecimal("10.5"), new BigDecimal("11.5"),
                new BigDecimal("10.5"), new BigDecimal("11.0"), 1_000_000);

        assertEquals("000001", kd.stockCode);
        assertEquals(new BigDecimal("11.0"), kd.close);
        assertEquals(new BigDecimal("11.5"), kd.high);
        assertEquals(new BigDecimal("10.5"), kd.low);
        assertEquals(new BigDecimal("10.5"), kd.open);
        assertEquals(1_000_000, kd.volume, 0);
    }

    @Test
    void maCalculation_verifyMath() {
        var closes = java.util.Arrays.asList(
                new BigDecimal("10"), new BigDecimal("11"), new BigDecimal("12"),
                new BigDecimal("13"), new BigDecimal("14"));
        BigDecimal sum = closes.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal ma = sum.divide(new BigDecimal("5"), 4, java.math.RoundingMode.HALF_UP);
        assertEquals(new BigDecimal("12.0000"), ma);
    }

    // 1.3 evaluateCondition

    @Test
    void evaluateCondition_priceBuy_triggers() {
        PlanCondition cond = TestFixtures.conditionBuilder()
                .conditionType(ConditionType.PRICE)
                .direction(TradeDirection.BUY)
                .targetPrice(new BigDecimal("10.00"))
                .build();
        KLineData kd = TestFixtures.kLineBuilder().close(new BigDecimal("11.00")).build();

        assertTrue(service.evaluateCondition(cond, kd, null));
    }

    @Test
    void evaluateCondition_priceBuy_notTriggers() {
        PlanCondition cond = TestFixtures.conditionBuilder()
                .conditionType(ConditionType.PRICE)
                .direction(TradeDirection.BUY)
                .targetPrice(new BigDecimal("12.00"))
                .build();
        KLineData kd = TestFixtures.kLineBuilder().close(new BigDecimal("11.00")).build();

        assertFalse(service.evaluateCondition(cond, kd, null));
    }

    @Test
    void evaluateCondition_priceSell_triggers() {
        PlanCondition cond = TestFixtures.conditionBuilder()
                .conditionType(ConditionType.PRICE)
                .direction(TradeDirection.SELL)
                .targetPrice(new BigDecimal("12.00"))
                .build();
        KLineData kd = TestFixtures.kLineBuilder().close(new BigDecimal("11.00")).build();

        assertTrue(service.evaluateCondition(cond, kd, null));
    }

    @Test
    void evaluateCondition_priceSell_notTriggers() {
        PlanCondition cond = TestFixtures.conditionBuilder()
                .conditionType(ConditionType.PRICE)
                .direction(TradeDirection.SELL)
                .targetPrice(new BigDecimal("10.00"))
                .build();
        KLineData kd = TestFixtures.kLineBuilder().close(new BigDecimal("11.00")).build();

        assertFalse(service.evaluateCondition(cond, kd, null));
    }

    @Test
    void evaluateCondition_maBuy_triggers() {
        PlanCondition cond = TestFixtures.conditionBuilder()
                .conditionType(ConditionType.MA)
                .direction(TradeDirection.BUY)
                .maPeriod(5)
                .build();
        KLineData kd = TestFixtures.kLineBuilder().close(new BigDecimal("11.033")).build();

        assertTrue(service.evaluateCondition(cond, kd, new BigDecimal("11.00")));
        // 触碰型: |11.033 - 11.00| / 11.033 = 0.3% 刚好触发
    }

    @Test
    void evaluateCondition_maBuy_notTriggers() {
        PlanCondition cond = TestFixtures.conditionBuilder()
                .conditionType(ConditionType.MA)
                .direction(TradeDirection.BUY)
                .build();
        KLineData kd = TestFixtures.kLineBuilder().close(new BigDecimal("11.04")).build();

        assertFalse(service.evaluateCondition(cond, kd, new BigDecimal("11.00")));
        // 触碰型: |11.04 - 11.00| / 11.04 = 0.36% 超过 0.3% 不触发
    }

    @Test
    void evaluateCondition_maSell_triggers() {
        PlanCondition cond = TestFixtures.conditionBuilder()
                .conditionType(ConditionType.MA)
                .direction(TradeDirection.SELL)
                .build();
        KLineData kd = TestFixtures.kLineBuilder().close(new BigDecimal("10.97")).build();

        assertTrue(service.evaluateCondition(cond, kd, new BigDecimal("11.00")));
        // 触碰型: |10.97 - 11.00| / 10.97 = 0.273% <= 0.3% 触发
    }

    @Test
    void evaluateCondition_maSell_notTriggers() {
        PlanCondition cond = TestFixtures.conditionBuilder()
                .conditionType(ConditionType.MA)
                .direction(TradeDirection.SELL)
                .build();
        KLineData kd = TestFixtures.kLineBuilder().close(new BigDecimal("10.93")).build();

        assertFalse(service.evaluateCondition(cond, kd, new BigDecimal("11.00")));
        // 触碰型: |10.93 - 11.00| / 10.93 = 0.64% 超过 0.3% 不触发
    }

    @Test
    void evaluateCondition_maWithNullMaValue_notTriggers() {
        PlanCondition cond = TestFixtures.conditionBuilder()
                .conditionType(ConditionType.MA)
                .direction(TradeDirection.BUY)
                .build();
        KLineData kd = TestFixtures.kLineBuilder().close(new BigDecimal("12.00")).build();

        assertFalse(service.evaluateCondition(cond, kd, null));
    }

    @Test
    void evaluateCondition_maBuy_boundaryWithinTolerance_triggers() {
        PlanCondition cond = TestFixtures.conditionBuilder()
                .conditionType(ConditionType.MA)
                .direction(TradeDirection.BUY)
                .build();
        KLineData kd = TestFixtures.kLineBuilder().close(new BigDecimal("11.00")).build();

        assertTrue(service.evaluateCondition(cond, kd, new BigDecimal("11.00")));
        // 触碰型: |11.00 - 11.00| / 11.00 = 0% <= 0.3% 触发
    }

    @Test
    void evaluateCondition_priceBuy_boundaryEquals_triggers() {
        PlanCondition cond = TestFixtures.conditionBuilder()
                .conditionType(ConditionType.PRICE)
                .direction(TradeDirection.BUY)
                .targetPrice(new BigDecimal("10.00"))
                .build();
        KLineData kd = TestFixtures.kLineBuilder().close(new BigDecimal("10.00")).build();

        assertTrue(service.evaluateCondition(cond, kd, null));
    }

    @Test
    void isTradingDay_noToken_returnsTrue() {
        when(tushareConfig.getToken()).thenReturn("");
        assertTrue(service.isTradingDay(LocalDate.of(2026, 5, 21)));
    }
}
