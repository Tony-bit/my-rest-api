package com.example.myapi.service;

import com.example.myapi.config.TushareConfig;
import com.example.myapi.entity.ConditionType;
import com.example.myapi.entity.PlanCondition;
import com.example.myapi.entity.PlanType;
import com.example.myapi.service.TushareService.KLineData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TushareServiceTest {

    @Mock private TushareConfig tushareConfig;

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

    @Test
    void evaluateCondition_priceBuy_triggers() {
        PlanCondition cond = TestFixtures.conditionBuilder()
                .conditionType(ConditionType.PRICE)
                .targetPrice(new BigDecimal("10.00"))
                .build();
        KLineData kd = TestFixtures.kLineBuilder()
                .low(new BigDecimal("9.50"))
                .close(new BigDecimal("11.00"))
                .build();

        assertTrue(service.evaluateCondition(cond, PlanType.BUY, kd, null));
    }

    @Test
    void evaluateCondition_priceBuy_notTriggers() {
        PlanCondition cond = TestFixtures.conditionBuilder()
                .conditionType(ConditionType.PRICE)
                .targetPrice(new BigDecimal("12.00"))
                .build();
        KLineData kd = TestFixtures.kLineBuilder()
                .high(new BigDecimal("11.50"))
                .close(new BigDecimal("11.00"))
                .build();

        assertFalse(service.evaluateCondition(cond, PlanType.BUY, kd, null));
    }

    @Test
    void evaluateCondition_priceSell_triggers() {
        PlanCondition cond = TestFixtures.conditionBuilder()
                .conditionType(ConditionType.PRICE)
                .targetPrice(new BigDecimal("12.00"))
                .build();
        KLineData kd = TestFixtures.kLineBuilder()
                .high(new BigDecimal("12.50"))
                .close(new BigDecimal("11.00"))
                .build();

        assertTrue(service.evaluateCondition(cond, PlanType.SELL, kd, null));
    }

    @Test
    void evaluateCondition_priceSell_notTriggers() {
        PlanCondition cond = TestFixtures.conditionBuilder()
                .conditionType(ConditionType.PRICE)
                .targetPrice(new BigDecimal("10.00"))
                .build();
        KLineData kd = TestFixtures.kLineBuilder().close(new BigDecimal("11.00")).build();

        assertFalse(service.evaluateCondition(cond, PlanType.SELL, kd, null));
    }

    @Test
    void evaluateCondition_maBuy_triggers() {
        PlanCondition cond = TestFixtures.conditionBuilder()
                .conditionType(ConditionType.MA)
                .maPeriod(5)
                .build();
        KLineData kd = TestFixtures.kLineBuilder().close(new BigDecimal("11.033")).build();

        assertTrue(service.evaluateCondition(cond, PlanType.BUY, kd, new BigDecimal("11.00")));
    }

    @Test
    void evaluateCondition_maBuy_notTriggers() {
        PlanCondition cond = TestFixtures.conditionBuilder()
                .conditionType(ConditionType.MA)
                .maPeriod(5)
                .build();
        KLineData kd = TestFixtures.kLineBuilder().close(new BigDecimal("11.04")).build();

        assertFalse(service.evaluateCondition(cond, PlanType.BUY, kd, new BigDecimal("11.00")));
    }

    @Test
    void evaluateCondition_maSell_triggers() {
        PlanCondition cond = TestFixtures.conditionBuilder()
                .conditionType(ConditionType.MA)
                .maPeriod(5)
                .build();
        KLineData kd = TestFixtures.kLineBuilder().close(new BigDecimal("10.97")).build();

        assertTrue(service.evaluateCondition(cond, PlanType.SELL, kd, new BigDecimal("11.00")));
    }

    @Test
    void evaluateCondition_maSell_notTriggers() {
        PlanCondition cond = TestFixtures.conditionBuilder()
                .conditionType(ConditionType.MA)
                .maPeriod(5)
                .build();
        KLineData kd = TestFixtures.kLineBuilder().close(new BigDecimal("10.93")).build();

        assertFalse(service.evaluateCondition(cond, PlanType.SELL, kd, new BigDecimal("11.00")));
    }

    @Test
    void evaluateCondition_maWithNullMaValue_notTriggers() {
        PlanCondition cond = TestFixtures.conditionBuilder()
                .conditionType(ConditionType.MA)
                .maPeriod(5)
                .build();
        KLineData kd = TestFixtures.kLineBuilder().close(new BigDecimal("12.00")).build();

        assertFalse(service.evaluateCondition(cond, PlanType.BUY, kd, null));
    }

    @Test
    void evaluateCondition_maBuy_boundaryEquals_triggers() {
        PlanCondition cond = TestFixtures.conditionBuilder()
                .conditionType(ConditionType.MA)
                .maPeriod(5)
                .build();
        KLineData kd = TestFixtures.kLineBuilder().close(new BigDecimal("11.00")).build();

        assertTrue(service.evaluateCondition(cond, PlanType.BUY, kd, new BigDecimal("11.00")));
    }

    @Test
    void evaluateCondition_priceBuy_boundaryEquals_triggers() {
        PlanCondition cond = TestFixtures.conditionBuilder()
                .conditionType(ConditionType.PRICE)
                .targetPrice(new BigDecimal("10.00"))
                .build();
        KLineData kd = TestFixtures.kLineBuilder()
                .low(new BigDecimal("10.00"))
                .close(new BigDecimal("10.00"))
                .build();

        assertTrue(service.evaluateCondition(cond, PlanType.BUY, kd, null));
    }

    @Test
    void isTradingDay_noToken_returnsTrue() {
        when(tushareConfig.getToken()).thenReturn("");
        assertTrue(service.isTradingDay(LocalDate.of(2026, 5, 21)));
    }

    // ========== normalizeStockCode tests ==========

    @Test
    void normalizeStockCode_alreadyHasSuffix_returnsAsIs() {
        assertEquals("600000.SH", service.normalizeStockCode("600000.SH"));
        assertEquals("000001.SZ", service.normalizeStockCode("000001.SZ"));
    }

    @Test
    void normalizeStockCode_startsWith6_addsSH() {
        assertEquals("600000.SH", service.normalizeStockCode("600000"));
    }

    @Test
    void normalizeStockCode_startsWith0_addsSZ() {
        assertEquals("000001.SZ", service.normalizeStockCode("000001"));
    }

    @Test
    void normalizeStockCode_startsWith3_addsSZ() {
        assertEquals("300001.SZ", service.normalizeStockCode("300001"));
    }

    @Test
    void normalizeStockCode_nullOrBlank_returnsAsIs() {
        assertNull(service.normalizeStockCode(null));
        assertEquals("  ", service.normalizeStockCode("  "));
    }

    @Test
    void normalizeStockCode_unknownPrefix_noSuffix() {
        assertEquals("123456", service.normalizeStockCode("123456"));
    }
}
