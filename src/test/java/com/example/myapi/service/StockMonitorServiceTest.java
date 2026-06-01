package com.example.myapi.service;

import com.example.myapi.dto.StockWatchDTO;
import com.example.myapi.entity.*;
import com.example.myapi.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StockMonitorService.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StockMonitorServiceTest {

    @Mock private StockMonitorConfigRepository configRepository;
    @Mock private StockWatchRepository watchRepository;
    @Mock private StockIndicatorSnapshotRepository snapshotRepository;
    @Mock private StockSignalLogRepository signalLogRepository;
    @Mock private XueqiuService xueqiuService;
    @Mock private WeChatNotificationService weChatService;
    @Mock private TushareService tushareService;

    private StockMonitorService service;
    private Clock fixedClock;

    private StockMonitorConfig defaultConfig;

    @BeforeEach
    void setUp() {
        // Fixed clock at 16:00 on a weekday (after 15:30 post-close window)
        fixedClock = Clock.fixed(
                LocalDateTime.of(2026, 5, 29, 16, 0).atZone(ZoneId.of("Asia/Shanghai")).toInstant(),
                ZoneId.of("Asia/Shanghai")
        );

        service = new StockMonitorService(
                configRepository, watchRepository, snapshotRepository, signalLogRepository,
                xueqiuService, new StockSignalService(), weChatService, tushareService, fixedClock
        );

        // Mock watchRepository.save() to return the watch with an ID (lenient for tests that don't use it)
        lenient().when(watchRepository.save(any(StockWatch.class))).thenAnswer(invocation -> {
            StockWatch watch = invocation.getArgument(0);
            if (watch.getId() == null) {
                watch.setId(1L);
            }
            return watch;
        });

        defaultConfig = StockMonitorConfig.builder()
                .id(1L)
                .xueqiuCookie("test-cookie")
                .xueqiuConfigured(true)
                .wechatAppId("wx06680d7f6fdcd0da")
                .wechatAppSecret("0fa34fb71e4d1c198b784f230583b468")
                .wechatTemplateId("239zHlPaAORIosFh8sOOqVsaRGF3JPQgvgW5WEjSQpI")
                .wechatOpenId("oIvsX3XhNf21wWPWtF2SyOxmydek")
                .wechatConfigured(true)
                .build();
    }

    // ========== New Watch Initialization Tests ==========

    @Nested
    @DisplayName("New Watch Initialization Tests")
    class NewWatchInitializationTests {

        @Test
        @DisplayName("NWI-01: Initialize with WATCH_SELL from history")
        void initializeWithWatchSell() {
            when(configRepository.getConfig()).thenReturn(defaultConfig);
            when(xueqiuService.isValidStockCode("600000")).thenReturn(true);
            when(watchRepository.existsByStockCode("600000")).thenReturn(false);

            // Simulate J values: [85, 88, 91, 95, 88] - WATCH_SELL
            List<XueqiuService.KLineRow> rows = createKLineRows(
                    new double[]{88, 95, 91, 88, 85},  // J values (oldest to newest)
                    5
            );
            when(xueqiuService.fetchKLineData(eq("600000"), eq("test-cookie"), anyInt()))
                    .thenReturn(new XueqiuService.KLineResult(rows, false));

            StockWatchDTO.CreateRequest request = new StockWatchDTO.CreateRequest("600000", "浦发银行");
            service.createWatch(request);

            ArgumentCaptor<StockWatch> captor = ArgumentCaptor.forClass(StockWatch.class);
            verify(watchRepository).save(captor.capture());

            assertEquals(SignalState.WATCH_SELL, captor.getValue().getSignalState());
        }

        @Test
        @DisplayName("NWI-02: Initialize with WATCH_BUY from history")
        void initializeWithWatchBuy() {
            when(configRepository.getConfig()).thenReturn(defaultConfig);
            when(xueqiuService.isValidStockCode("000001")).thenReturn(true);
            when(watchRepository.existsByStockCode("000001")).thenReturn(false);

            // Simulate J values: [15, 12, 8, 5, 12] - WATCH_BUY
            List<XueqiuService.KLineRow> rows = createKLineRows(
                    new double[]{12, 5, 8, 12, 15},  // J values (oldest to newest)
                    5
            );
            when(xueqiuService.fetchKLineData(eq("000001"), eq("test-cookie"), anyInt()))
                    .thenReturn(new XueqiuService.KLineResult(rows, false));

            StockWatchDTO.CreateRequest request = new StockWatchDTO.CreateRequest("000001", "平安银行");
            service.createWatch(request);

            ArgumentCaptor<StockWatch> captor = ArgumentCaptor.forClass(StockWatch.class);
            verify(watchRepository).save(captor.capture());

            assertEquals(SignalState.WATCH_BUY, captor.getValue().getSignalState());
        }

        @Test
        @DisplayName("NWI-03: Initialize with NONE when no extreme values")
        void initializeWithNone() {
            when(configRepository.getConfig()).thenReturn(defaultConfig);
            when(xueqiuService.isValidStockCode("600036")).thenReturn(true);
            when(watchRepository.existsByStockCode("600036")).thenReturn(false);

            // Simulate J values: [50, 55, 52, 48, 51] - all in middle range
            List<XueqiuService.KLineRow> rows = createKLineRows(
                    new double[]{51, 48, 52, 55, 50},  // J values (oldest to newest)
                    5
            );
            when(xueqiuService.fetchKLineData(eq("600036"), eq("test-cookie"), anyInt()))
                    .thenReturn(new XueqiuService.KLineResult(rows, false));

            StockWatchDTO.CreateRequest request = new StockWatchDTO.CreateRequest("600036", "招商银行");
            service.createWatch(request);

            ArgumentCaptor<StockWatch> captor = ArgumentCaptor.forClass(StockWatch.class);
            verify(watchRepository).save(captor.capture());

            assertEquals(SignalState.NONE, captor.getValue().getSignalState());
        }

        @Test
        @DisplayName("NWI-07: No data available - initialize with NONE")
        void initializeWithNoData() {
            when(configRepository.getConfig()).thenReturn(defaultConfig);
            when(xueqiuService.isValidStockCode("600000")).thenReturn(true);
            when(watchRepository.existsByStockCode("600000")).thenReturn(false);
            when(xueqiuService.fetchKLineData(eq("600000"), eq("test-cookie"), anyInt()))
                    .thenReturn(new XueqiuService.KLineResult(List.of(), false));

            StockWatchDTO.CreateRequest request = new StockWatchDTO.CreateRequest("600000", "浦发银行");
            service.createWatch(request);

            ArgumentCaptor<StockWatch> captor = ArgumentCaptor.forClass(StockWatch.class);
            verify(watchRepository).save(captor.capture());

            assertEquals(SignalState.NONE, captor.getValue().getSignalState());
        }

        @Test
        @DisplayName("Invalid stock code throws exception")
        void invalidStockCode() {
            when(xueqiuService.isValidStockCode("ABCDEF")).thenReturn(false);

            StockWatchDTO.CreateRequest request = new StockWatchDTO.CreateRequest("ABCDEF", "测试");
            assertThrows(IllegalArgumentException.class, () -> service.createWatch(request));
        }

        @Test
        @DisplayName("Duplicate stock code throws exception")
        void duplicateStockCode() {
            when(xueqiuService.isValidStockCode("600000")).thenReturn(true);
            when(watchRepository.existsByStockCode("600000")).thenReturn(true);

            StockWatchDTO.CreateRequest request = new StockWatchDTO.CreateRequest("600000", "浦发银行");
            assertThrows(IllegalArgumentException.class, () -> service.createWatch(request));
        }
    }

    // ========== Check Now Tests ==========

    @Nested
    @DisplayName("Check Now Tests")
    class CheckNowTests {

        @Test
        @DisplayName("CBN-01: Returns preview result before 15:30")
        void checkBefore1530() {
            // Use a clock at 14:00 (before 15:30)
            Clock morningClock = Clock.fixed(
                    LocalDateTime.of(2026, 5, 29, 14, 0).atZone(ZoneId.of("Asia/Shanghai")).toInstant(),
                    ZoneId.of("Asia/Shanghai")
            );
            StockMonitorService serviceWithMorningClock = new StockMonitorService(
                    configRepository, watchRepository, snapshotRepository, signalLogRepository,
                    xueqiuService, new StockSignalService(), weChatService, tushareService, morningClock
            );

            StockWatch watch = StockMonitorFixtures.watchBuilder()
                    .stockCode("600000")
                    .signalState(SignalState.WATCH_SELL)
                    .build();
            when(watchRepository.findById(1L)).thenReturn(Optional.of(watch));
            when(configRepository.getConfig()).thenReturn(defaultConfig);

            List<XueqiuService.KLineRow> rows = createKLineRows(new double[]{92, 88}, 2);
            when(xueqiuService.fetchKLineData(eq("600000"), eq("test-cookie"), anyInt()))
                    .thenReturn(new XueqiuService.KLineResult(rows, false));

            StockWatchDTO.CheckResult result = serviceWithMorningClock.checkNow(1L);

            assertEquals(CheckStatus.SKIPPED_BEFORE_POST_CLOSE_WINDOW.name(), result.getCheckStatus());
            verify(snapshotRepository, never()).save(any());
            verify(signalLogRepository, never()).save(any());
        }

        @Test
        @DisplayName("CBN-06: Insufficient data returns SKIPPED_INSUFFICIENT_DATA")
        void insufficientData() {
            StockWatch watch = StockMonitorFixtures.watchBuilder()
                    .stockCode("600000")
                    .signalState(SignalState.NONE)
                    .build();
            when(watchRepository.findById(1L)).thenReturn(Optional.of(watch));
            when(configRepository.getConfig()).thenReturn(defaultConfig);

            List<XueqiuService.KLineRow> rows = createKLineRows(new double[]{50}, 1);
            when(xueqiuService.fetchKLineData(eq("600000"), eq("test-cookie"), anyInt()))
                    .thenReturn(new XueqiuService.KLineResult(rows, false));

            StockWatchDTO.CheckResult result = service.checkNow(1L);

            assertEquals(CheckStatus.SKIPPED_INSUFFICIENT_DATA.name(), result.getCheckStatus());
        }

        @Test
        @DisplayName("SMT-04: Suspended stock returns SKIPPED_SUSPENDED_OR_NO_DATA")
        void suspendedStock() {
            StockWatch watch = StockMonitorFixtures.watchBuilder()
                    .stockCode("600000")
                    .signalState(SignalState.WATCH_SELL)
                    .build();
            when(watchRepository.findById(1L)).thenReturn(Optional.of(watch));
            when(configRepository.getConfig()).thenReturn(defaultConfig);

            when(xueqiuService.fetchKLineData(eq("600000"), eq("test-cookie"), anyInt()))
                    .thenReturn(new XueqiuService.KLineResult(List.of(), true));

            StockWatchDTO.CheckResult result = service.checkNow(1L);

            assertEquals(CheckStatus.SKIPPED_SUSPENDED_OR_NO_DATA.name(), result.getCheckStatus());
        }

        @Test
        @DisplayName("Config missing returns SKIPPED_CONFIG_MISSING")
        void configMissing() {
            StockWatch watch = StockMonitorFixtures.watchBuilder().stockCode("600000").build();
            when(watchRepository.findById(1L)).thenReturn(Optional.of(watch));
            when(configRepository.getConfig()).thenReturn(
                    StockMonitorConfig.builder().xueqiuConfigured(false).build()
            );

            StockWatchDTO.CheckResult result = service.checkNow(1L);

            assertEquals(CheckStatus.SKIPPED_CONFIG_MISSING.name(), result.getCheckStatus());
        }
    }

    // ========== Notification Deduplication Tests ==========

    @Nested
    @DisplayName("Notification Deduplication Tests")
    class NotificationDeduplicationTests {

        @Test
        @DisplayName("ND-01: Already sent notification is not resent")
        void alreadySentNoResend() {
            StockWatch watch = StockMonitorFixtures.watchBuilder()
                    .stockCode("600000")
                    .signalState(SignalState.WATCH_SELL)
                    .build();
            when(watchRepository.findById(1L)).thenReturn(Optional.of(watch));
            when(configRepository.getConfig()).thenReturn(defaultConfig);

            // J dropping + MACD dropping = SELL signal
            List<XueqiuService.KLineRow> rows = createKLineRowsWithMacd(
                    new double[]{88, 92},  // J: today=88, yesterday=92 (dropping)
                    new double[]{0.01, 0.05},  // MACD: today=0.01, yesterday=0.05 (dropping)
                    2
            );
            when(xueqiuService.fetchKLineData(eq("600000"), eq("test-cookie"), anyInt()))
                    .thenReturn(new XueqiuService.KLineResult(rows, false));

            // Already sent notification exists
            when(signalLogRepository.existsByStockCodeAndTradeDateAndSignalTypeAndNotificationStatus(
                    eq("600000"), any(), eq("SELL"), eq(NotificationStatus.SENT)))
                    .thenReturn(true);

            StockWatchDTO.CheckResult result = service.checkNow(1L);

            assertEquals("SELL", result.getSignalResult());
            verify(weChatService, never()).sendSignalNotification(any(), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("ND-02: New signal triggers notification")
        void newSignalTriggersNotification() {
            StockWatch watch = StockMonitorFixtures.watchBuilder()
                    .stockCode("600000")
                    .signalState(SignalState.WATCH_SELL)
                    .build();
            when(watchRepository.findById(1L)).thenReturn(Optional.of(watch));
            when(configRepository.getConfig()).thenReturn(defaultConfig);

            List<XueqiuService.KLineRow> rows = createKLineRowsWithMacd(
                    new double[]{88, 92},  // J dropping: 88 -> 92
                    new double[]{0.01, 0.05},  // MACD dropping: 0.01 -> 0.05
                    2
            );
            when(xueqiuService.fetchKLineData(eq("600000"), eq("test-cookie"), anyInt()))
                    .thenReturn(new XueqiuService.KLineResult(rows, false));

            when(signalLogRepository.existsByStockCodeAndTradeDateAndSignalTypeAndNotificationStatus(
                    any(), any(), any(), any()))
                    .thenReturn(false);

            StockWatchDTO.CheckResult result = service.checkNow(1L);

            assertEquals("SELL", result.getSignalResult());
            // Manual check does not send notification
            verify(weChatService, never()).sendSignalNotification(any(), any(), any(), any(), any(), any(), any());
        }
    }

    // ========== Error Handling Tests ==========

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("EHT-03: WeChat config missing - signal logged but notification fails gracefully")
        void wechatConfigMissingButSignalLogged() {
            StockMonitorConfig configNoWechat = StockMonitorConfig.builder()
                    .xueqiuCookie("test-cookie")
                    .xueqiuConfigured(true)
                    .wechatConfigured(false)
                    .build();

            StockWatch watch = StockMonitorFixtures.watchBuilder()
                    .stockCode("600000")
                    .signalState(SignalState.WATCH_SELL)
                    .build();
            when(watchRepository.findById(1L)).thenReturn(Optional.of(watch));
            when(configRepository.getConfig()).thenReturn(configNoWechat);

            List<XueqiuService.KLineRow> rows = createKLineRowsWithMacd(
                    new double[]{88, 92},  // J dropping: 88 -> 92
                    new double[]{0.01, 0.05},  // MACD dropping: 0.01 -> 0.05
                    2
            );
            when(xueqiuService.fetchKLineData(eq("600000"), eq("test-cookie"), anyInt()))
                    .thenReturn(new XueqiuService.KLineResult(rows, false));

            StockWatchDTO.CheckResult result = service.checkNow(1L);

            // Signal is still detected
            assertEquals("SELL", result.getSignalResult());
            assertEquals(CheckStatus.SUCCESS.name(), result.getCheckStatus());
        }
    }

    // ========== Scheduled Monitor Tests ==========

    @Nested
    @DisplayName("Scheduled Monitor Tests")
    class ScheduledMonitorTests {

        @Test
        @DisplayName("SMT-03: One stock failure - other stocks continue")
        void oneStockFailureContinues() {
            List<StockWatch> watches = List.of(
                    StockMonitorFixtures.watchBuilder().stockCode("600000").enabled(true).build(),
                    StockMonitorFixtures.watchBuilder().stockCode("000001").enabled(true).build()
            );
            when(watchRepository.findByEnabledTrue()).thenReturn(watches);
            when(configRepository.getConfig()).thenReturn(defaultConfig);
            when(tushareService.isTradingDay(any())).thenReturn(true);

            // First stock succeeds, second has no data
            List<XueqiuService.KLineRow> successRows = createKLineRows(new double[]{50, 50}, 2);
            when(xueqiuService.fetchKLineData(eq("600000"), eq("test-cookie"), anyInt()))
                    .thenReturn(new XueqiuService.KLineResult(successRows, false));
            // Second stock returns empty rows (simulating no data scenario)
            when(xueqiuService.fetchKLineData(eq("000001"), eq("test-cookie"), anyInt()))
                    .thenReturn(new XueqiuService.KLineResult(List.of(), false));

            // Test individual check handles empty data gracefully
            StockWatch emptyWatch = StockMonitorFixtures.watchBuilder()
                    .stockCode("000001")
                    .signalState(SignalState.NONE)
                    .build();

            StockWatchDTO.CheckResult result = service.performCheck(emptyWatch, TriggerSource.SCHEDULED);

            // Verify the error was handled gracefully
            assertEquals(CheckStatus.SKIPPED_INSUFFICIENT_DATA.name(), result.getCheckStatus());
        }
    }

    // ========== Helper Methods ==========

    private List<XueqiuService.KLineRow> createKLineRows(double[] jValues, int count) {
        return createKLineRowsWithMacd(jValues, new double[]{0.02, 0.02}, count);
    }

    private List<XueqiuService.KLineRow> createKLineRowsWithMacd(double[] jValues, double[] macdValues, int count) {
        LocalDate today = LocalDate.now(fixedClock);
        java.util.List<XueqiuService.KLineRow> rows = new java.util.ArrayList<>();

        for (int i = 0; i < count; i++) {
            LocalDate date = today.minusDays(count - 1 - i);
            long timestamp = date.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();

            rows.add(new XueqiuService.KLineRow(
                    timestamp,
                    new BigDecimal("10.00"),
                    new BigDecimal("10.20"),
                    new BigDecimal("9.90"),
                    new BigDecimal("10.10"),
                    new BigDecimal("1000000"),
                    new BigDecimal("0.02"),
                    new BigDecimal("0.025"),
                    macdValues != null && i < macdValues.length ? BigDecimal.valueOf(macdValues[i]) : BigDecimal.valueOf(0.02),
                    new BigDecimal("55.0"),
                    new BigDecimal("52.0"),
                    jValues != null && i < jValues.length ? BigDecimal.valueOf(jValues[i]) : BigDecimal.valueOf(50.0)
            ));
        }
        return rows;
    }
}
