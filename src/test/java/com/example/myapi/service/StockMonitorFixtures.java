package com.example.myapi.service;

import com.example.myapi.entity.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Test fixtures for stock monitor functionality.
 */
public class StockMonitorFixtures {

    public static SnapshotBuilder snapshotBuilder() {
        return new SnapshotBuilder();
    }

    public static SignalLogBuilder signalLogBuilder() {
        return new SignalLogBuilder();
    }

    public static WatchBuilder watchBuilder() {
        return new WatchBuilder();
    }

    public static KLineRowBuilder kLineRow() {
        return new KLineRowBuilder();
    }

    public static XueqiuResponseBuilder xueqiuResponse() {
        return new XueqiuResponseBuilder();
    }

    // ========== SnapshotBuilder ==========

    public static class SnapshotBuilder {
        private Long id = 1L;
        private String stockCode = "600000";
        private LocalDate tradeDate = LocalDate.now();
        private BigDecimal close = new BigDecimal("10.00");
        private BigDecimal kdjj = new BigDecimal("50.0");
        private BigDecimal macd = new BigDecimal("0.05");
        private BigDecimal open = new BigDecimal("9.90");
        private BigDecimal high = new BigDecimal("10.10");
        private BigDecimal low = new BigDecimal("9.85");
        private BigDecimal kdjk = new BigDecimal("55.0");
        private BigDecimal kdjd = new BigDecimal("52.0");
        private BigDecimal dif = new BigDecimal("0.02");
        private BigDecimal dea = new BigDecimal("0.03");
        private long sourceTimestamp = System.currentTimeMillis();
        private LocalDateTime lastCheckedAt = LocalDateTime.now();

        public SnapshotBuilder id(Long id) { this.id = id; return this; }
        public SnapshotBuilder stockCode(String stockCode) { this.stockCode = stockCode; return this; }
        public SnapshotBuilder tradeDate(LocalDate tradeDate) { this.tradeDate = tradeDate; return this; }
        public SnapshotBuilder close(BigDecimal close) { this.close = close; return this; }
        public SnapshotBuilder kdjj(BigDecimal kdjj) { this.kdjj = kdjj; return this; }
        public SnapshotBuilder macd(BigDecimal macd) { this.macd = macd; return this; }
        public SnapshotBuilder open(BigDecimal open) { this.open = open; return this; }
        public SnapshotBuilder high(BigDecimal high) { this.high = high; return this; }
        public SnapshotBuilder low(BigDecimal low) { this.low = low; return this; }
        public SnapshotBuilder kdjk(BigDecimal kdjk) { this.kdjk = kdjk; return this; }
        public SnapshotBuilder kdjd(BigDecimal kdjd) { this.kdjd = kdjd; return this; }
        public SnapshotBuilder dif(BigDecimal dif) { this.dif = dif; return this; }
        public SnapshotBuilder dea(BigDecimal dea) { this.dea = dea; return this; }

        public StockIndicatorSnapshot build() {
            StockIndicatorSnapshot snapshot = StockIndicatorSnapshot.builder()
                    .stockCode(stockCode)
                    .tradeDate(tradeDate)
                    .openPrice(open)
                    .highPrice(high)
                    .lowPrice(low)
                    .closePrice(close)
                    .dif(dif)
                    .dea(dea)
                    .macd(macd)
                    .kdjk(kdjk)
                    .kdjd(kdjd)
                    .kdjj(kdjj)
                    .sourceTimestamp(sourceTimestamp)
                    .lastCheckedAt(lastCheckedAt)
                    .build();
            setField(snapshot, "id", id);
            return snapshot;
        }

        private void setField(Object target, String fieldName, Object value) {
            try {
                java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
                f.setAccessible(true);
                f.set(target, value);
            } catch (Exception ignored) {}
        }
    }

    // ========== WatchBuilder ==========

    public static class WatchBuilder {
        private Long id = 1L;
        private String stockCode = "600000";
        private String stockName = "浦发银行";
        private SignalState signalState = SignalState.NONE;
        private Boolean enabled = true;
        private LocalDate watchStartDate;
        private String lastSignalType;
        private LocalDate lastSignalDate;
        private LocalDateTime lastCheckedAt;
        private String lastCheckStatus;
        private String lastCheckMessage;

        public WatchBuilder id(Long id) { this.id = id; return this; }
        public WatchBuilder stockCode(String stockCode) { this.stockCode = stockCode; return this; }
        public WatchBuilder stockName(String stockName) { this.stockName = stockName; return this; }
        public WatchBuilder signalState(SignalState signalState) { this.signalState = signalState; return this; }
        public WatchBuilder enabled(Boolean enabled) { this.enabled = enabled; return this; }
        public WatchBuilder watchStartDate(LocalDate watchStartDate) { this.watchStartDate = watchStartDate; return this; }
        public WatchBuilder lastSignalType(String lastSignalType) { this.lastSignalType = lastSignalType; return this; }
        public WatchBuilder lastSignalDate(LocalDate lastSignalDate) { this.lastSignalDate = lastSignalDate; return this; }
        public WatchBuilder lastCheckedAt(LocalDateTime lastCheckedAt) { this.lastCheckedAt = lastCheckedAt; return this; }
        public WatchBuilder lastCheckStatus(String lastCheckStatus) { this.lastCheckStatus = lastCheckStatus; return this; }
        public WatchBuilder lastCheckMessage(String lastCheckMessage) { this.lastCheckMessage = lastCheckMessage; return this; }

        public StockWatch build() {
            StockWatch watch = StockWatch.builder()
                    .stockCode(stockCode)
                    .stockName(stockName)
                    .signalState(signalState)
                    .enabled(enabled)
                    .watchStartDate(watchStartDate)
                    .lastSignalType(lastSignalType)
                    .lastSignalDate(lastSignalDate)
                    .lastCheckedAt(lastCheckedAt)
                    .lastCheckStatus(lastCheckStatus)
                    .lastCheckMessage(lastCheckMessage)
                    .build();
            setField(watch, "id", id);
            return watch;
        }

        private void setField(Object target, String fieldName, Object value) {
            try {
                java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
                f.setAccessible(true);
                f.set(target, value);
            } catch (Exception ignored) {}
        }
    }

    // ========== SignalLogBuilder ==========

    public static class SignalLogBuilder {
        private Long id = 1L;
        private String stockCode = "600000";
        private LocalDate tradeDate = LocalDate.now();
        private String signalType = "BUY";
        private TriggerSource triggerSource = TriggerSource.MANUAL_CHECK;
        private BigDecimal closePrice = new BigDecimal("10.00");
        private BigDecimal jToday = new BigDecimal("12.0");
        private BigDecimal jYesterday = new BigDecimal("8.0");
        private BigDecimal macdToday = new BigDecimal("0.08");
        private BigDecimal macdYesterday = new BigDecimal("0.05");
        private String evaluationSummary = "买入信号确认";
        private NotificationStatus notificationStatus = NotificationStatus.NOT_SENT;
        private Integer notificationAttempts = 0;
        private LocalDateTime notificationSentAt;
        private String notificationFailureReason;

        public SignalLogBuilder id(Long id) { this.id = id; return this; }
        public SignalLogBuilder stockCode(String stockCode) { this.stockCode = stockCode; return this; }
        public SignalLogBuilder tradeDate(LocalDate tradeDate) { this.tradeDate = tradeDate; return this; }
        public SignalLogBuilder signalType(String signalType) { this.signalType = signalType; return this; }
        public SignalLogBuilder triggerSource(TriggerSource triggerSource) { this.triggerSource = triggerSource; return this; }
        public SignalLogBuilder closePrice(BigDecimal closePrice) { this.closePrice = closePrice; return this; }
        public SignalLogBuilder jToday(BigDecimal jToday) { this.jToday = jToday; return this; }
        public SignalLogBuilder jYesterday(BigDecimal jYesterday) { this.jYesterday = jYesterday; return this; }
        public SignalLogBuilder macdToday(BigDecimal macdToday) { this.macdToday = macdToday; return this; }
        public SignalLogBuilder macdYesterday(BigDecimal macdYesterday) { this.macdYesterday = macdYesterday; return this; }
        public SignalLogBuilder evaluationSummary(String evaluationSummary) { this.evaluationSummary = evaluationSummary; return this; }
        public SignalLogBuilder notificationStatus(NotificationStatus notificationStatus) { this.notificationStatus = notificationStatus; return this; }
        public SignalLogBuilder notificationAttempts(Integer notificationAttempts) { this.notificationAttempts = notificationAttempts; return this; }

        public StockSignalLog build() {
            StockSignalLog log = StockSignalLog.builder()
                    .stockCode(stockCode)
                    .tradeDate(tradeDate)
                    .signalType(signalType)
                    .triggerSource(triggerSource)
                    .closePrice(closePrice)
                    .jToday(jToday)
                    .jYesterday(jYesterday)
                    .macdToday(macdToday)
                    .macdYesterday(macdYesterday)
                    .evaluationSummary(evaluationSummary)
                    .notificationStatus(notificationStatus)
                    .notificationAttempts(notificationAttempts)
                    .notificationSentAt(notificationSentAt)
                    .notificationFailureReason(notificationFailureReason)
                    .build();
            setField(log, "id", id);
            return log;
        }

        private void setField(Object target, String fieldName, Object value) {
            try {
                java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
                f.setAccessible(true);
                f.set(target, value);
            } catch (Exception ignored) {}
        }
    }

    // ========== KLineRowBuilder ==========

    public static class KLineRowBuilder implements StockSignalService.KLineRowInput {
        private long timestamp = LocalDate.now().atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
        private double kdjj = 50.0;
        private double macd = 0.02;

        public KLineRowBuilder timestamp(long timestamp) { this.timestamp = timestamp; return this; }
        public KLineRowBuilder timestamp(LocalDate date) {
            this.timestamp = date.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
            return this;
        }
        public KLineRowBuilder kdjj(double kdjj) { this.kdjj = kdjj; return this; }
        public KLineRowBuilder macd(double macd) { this.macd = macd; return this; }

        public XueqiuService.KLineRow toXueqiuRow() {
            return new XueqiuService.KLineRow(
                    timestamp,
                    new BigDecimal("10.00"),
                    new BigDecimal("10.20"),
                    new BigDecimal("9.90"),
                    new BigDecimal("10.10"),
                    new BigDecimal("1000000"),
                    new BigDecimal("0.02"),
                    new BigDecimal("0.025"),
                    BigDecimal.valueOf(macd),
                    new BigDecimal("55.0"),
                    new BigDecimal("52.0"),
                    BigDecimal.valueOf(kdjj)
            );
        }

        @Override
        public BigDecimal getJ() {
            return BigDecimal.valueOf(kdjj);
        }

        @Override
        public BigDecimal getMacd() {
            return BigDecimal.valueOf(macd);
        }
    }

    // ========== XueqiuResponseBuilder ==========

    public static class XueqiuResponseBuilder {
        public XueqiuService.KLineResult buildValidResult(int rowCount) {
            java.util.List<XueqiuService.KLineRow> rows = new java.util.ArrayList<>();
            LocalDate today = LocalDate.now();

            for (int i = 0; i < rowCount; i++) {
                LocalDate date = today.minusDays(i);
                rows.add(new XueqiuService.KLineRow(
                        date.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli(),
                        new BigDecimal("10.00"),
                        new BigDecimal("10.20"),
                        new BigDecimal("9.90"),
                        new BigDecimal("10.10"),
                        new BigDecimal("1000000"),
                        new BigDecimal("0.02"),
                        new BigDecimal("0.025"),
                        BigDecimal.valueOf(0.02 - i * 0.001),
                        new BigDecimal("55.0"),
                        new BigDecimal("52.0"),
                        BigDecimal.valueOf(50.0 - i * 2)
                ));
            }

            return new XueqiuService.KLineResult(rows, false);
        }

        public XueqiuService.KLineResult buildSuspendedResult() {
            LocalDate suspendedDate = LocalDate.now().minusDays(10);
            return new XueqiuService.KLineResult(List.of(
                    new XueqiuService.KLineRow(
                            suspendedDate.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli(),
                            new BigDecimal("10.00"),
                            new BigDecimal("10.20"),
                            new BigDecimal("9.90"),
                            new BigDecimal("10.10"),
                            new BigDecimal("1000000"),
                            new BigDecimal("0.02"),
                            new BigDecimal("0.025"),
                            new BigDecimal("0.02"),
                            new BigDecimal("55.0"),
                            new BigDecimal("52.0"),
                            new BigDecimal("50.0")
                    )
            ), true);
        }

        public XueqiuService.KLineResult buildEmptyResult() {
            return new XueqiuService.KLineResult(List.of(), false);
        }
    }
}
