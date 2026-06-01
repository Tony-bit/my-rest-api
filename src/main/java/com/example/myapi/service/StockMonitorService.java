package com.example.myapi.service;

import com.example.myapi.dto.StockIndicatorDTO;
import com.example.myapi.dto.StockSignalDTO;
import com.example.myapi.dto.StockWatchDTO;
import com.example.myapi.entity.*;
import com.example.myapi.exception.XueqiuApiException;
import com.example.myapi.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;

/**
 * Main service for stock indicator monitoring.
 * Coordinates Xueqiu data fetching, signal evaluation, and notification sending.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockMonitorService {

    private final StockMonitorConfigRepository configRepository;
    private final StockWatchRepository watchRepository;
    private final StockIndicatorSnapshotRepository snapshotRepository;
    private final StockSignalLogRepository signalLogRepository;
    private final XueqiuService xueqiuService;
    private final StockSignalService signalService;
    private final WeChatNotificationService weChatService;
    private final TushareService tushareService;
    private final Clock clock;

    private static final int KLINE_COUNT = 60;
    private static final LocalTime POST_CLOSE_WINDOW = LocalTime.of(15, 30);
    private static final int INIT_HISTORY_DAYS = 5;

    /**
     * Get masked configuration status.
     */
    public StockMonitorConfig getConfig() {
        return configRepository.getConfig();
    }

    /**
     * Data source types for K-line data.
     */
    public enum DataSource {
        XUEQIU,
        TUSHARE
    }

    /**
     * Result wrapper for K-line data fetching.
     */
    public record KLineFetchResult(
            List<KLineRowData> rows,
            boolean suspended,
            DataSource source
    ) {}

    /**
     * Common interface for K-line rows from different data sources.
     */
    public interface KLineRowData extends StockSignalService.KLineRowInput {
        LocalDate toLocalDate();
        BigDecimal close();
        BigDecimal open();
        BigDecimal high();
        BigDecimal low();
        BigDecimal volume();
        BigDecimal kdjj();
        BigDecimal macd();
    }

    /**
     * Get current data source.
     */
    public DataSource getDataSource() {
        StockMonitorConfig config = configRepository.getConfig();
        try {
            return DataSource.valueOf(config.getDataSource());
        } catch (Exception e) {
            return DataSource.XUEQIU;
        }
    }

    /**
     * Set data source.
     */
    @Transactional
    public StockMonitorConfig setDataSource(DataSource dataSource) {
        StockMonitorConfig config = configRepository.getConfig();
        config.setDataSource(dataSource.name());
        return configRepository.save(config);
    }

    /**
     * Fetch K-line data from the configured data source.
     */
    private KLineFetchResult fetchKLineData(String stockCode, int count) {
        DataSource source = getDataSource();

        if (source == DataSource.TUSHARE) {
            try {
                TushareService.KLineResult result = tushareService.fetchKLineData(stockCode, count);
                List<KLineRowData> rows = result.rows().stream()
                        .map(r -> (KLineRowData) new TushareKLineRowAdapter(r))
                        .toList();
                return new KLineFetchResult(rows, result.suspended(), source);
            } catch (Exception e) {
                log.warn("Tushare fetch failed for {}, falling back to Xueqiu: {}", stockCode, e.getMessage());
            }
        }

        // Default to Xueqiu
        StockMonitorConfig config = configRepository.getConfig();
        if (!Boolean.TRUE.equals(config.getXueqiuConfigured())) {
            return new KLineFetchResult(List.of(), false, DataSource.XUEQIU);
        }

        try {
            XueqiuService.KLineResult result = xueqiuService.fetchKLineData(stockCode, config.getXueqiuCookie(), count);
            List<KLineRowData> rows = result.rows().stream()
                    .map(r -> (KLineRowData) new XueqiuKLineRowAdapter(r))
                    .toList();
            return new KLineFetchResult(rows, result.suspended(), DataSource.XUEQIU);
        } catch (Exception e) {
            log.error("Xueqiu fetch failed for {}", stockCode, e);
            return new KLineFetchResult(List.of(), false, DataSource.XUEQIU);
        }
    }

    /**
     * Adapter for Xueqiu K-line rows.
     */
    private static class XueqiuKLineRowAdapter implements KLineRowData {
        private final XueqiuService.KLineRow row;

        XueqiuKLineRowAdapter(XueqiuService.KLineRow row) {
            this.row = row;
        }

        @Override
        public LocalDate toLocalDate() {
            return row.toLocalDate();
        }

        @Override
        public BigDecimal close() {
            return row.close();
        }

        @Override
        public BigDecimal open() {
            return row.open();
        }

        @Override
        public BigDecimal high() {
            return row.high();
        }

        @Override
        public BigDecimal low() {
            return row.low();
        }

        @Override
        public BigDecimal volume() {
            return row.volume();
        }

        @Override
        public BigDecimal kdjj() {
            return row.kdjj();
        }

        @Override
        public BigDecimal macd() {
            return row.macd();
        }

        @Override
        public BigDecimal getJ() {
            return row.kdjj();
        }

        @Override
        public BigDecimal getMacd() {
            return row.macd();
        }
    }

    /**
     * Adapter for Tushare K-line rows.
     */
    private static class TushareKLineRowAdapter implements KLineRowData {
        private final TushareService.KLineRow row;

        TushareKLineRowAdapter(TushareService.KLineRow row) {
            this.row = row;
        }

        @Override
        public LocalDate toLocalDate() {
            return row.toLocalDate();
        }

        @Override
        public BigDecimal close() {
            return row.close();
        }

        @Override
        public BigDecimal open() {
            return row.open();
        }

        @Override
        public BigDecimal high() {
            return row.high();
        }

        @Override
        public BigDecimal low() {
            return row.low();
        }

        @Override
        public BigDecimal volume() {
            return row.volume();
        }

        @Override
        public BigDecimal kdjj() {
            return row.kdjj();
        }

        @Override
        public BigDecimal macd() {
            return row.macd();
        }

        @Override
        public BigDecimal getJ() {
            return row.kdjj();
        }

        @Override
        public BigDecimal getMacd() {
            return row.macd();
        }
    }

    /**
     * Update Xueqiu cookie.
     */
    @Transactional
    public StockMonitorConfig updateXueqiuCookie(String cookie) {
        StockMonitorConfig config = configRepository.getConfig();
        config.setXueqiuCookie(cookie);
        config.setXueqiuConfigured(cookie != null && !cookie.isBlank());
        return configRepository.save(config);
    }

    /**
     * Clear Xueqiu cookie.
     */
    @Transactional
    public StockMonitorConfig clearXueqiuCookie() {
        StockMonitorConfig config = configRepository.getConfig();
        config.setXueqiuCookie(null);
        config.setXueqiuConfigured(false);
        return configRepository.save(config);
    }

    /**
     * Update WeChat configuration.
     */
    @Transactional
    public StockMonitorConfig updateWeChatConfig(String appId, String appSecret, String templateId, String openId) {
        StockMonitorConfig config = configRepository.getConfig();
        config.setWechatAppId(appId);
        config.setWechatAppSecret(appSecret);
        config.setWechatTemplateId(templateId);
        config.setWechatOpenId(openId);
        config.setWechatConfigured(
                appId != null && !appId.isBlank() &&
                appSecret != null && !appSecret.isBlank() &&
                templateId != null && !templateId.isBlank() &&
                openId != null && !openId.isBlank()
        );
        return configRepository.save(config);
    }

    /**
     * Clear WeChat configuration.
     */
    @Transactional
    public StockMonitorConfig clearWeChatConfig() {
        StockMonitorConfig config = configRepository.getConfig();
        config.setWechatAppId(null);
        config.setWechatAppSecret(null);
        config.setWechatTemplateId(null);
        config.setWechatOpenId(null);
        config.setWechatConfigured(false);
        return configRepository.save(config);
    }

    /**
     * List all stock watches with latest indicator info.
     */
    public List<StockWatchDTO.ListResponse> listWatches() {
        List<StockWatch> watches = watchRepository.findAll();
        return watches.stream().map(this::toListResponse).toList();
    }

    /**
     * Get a stock watch by ID.
     */
    public StockWatchDTO.Response getWatch(Long id) {
        StockWatch watch = watchRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Stock watch not found: " + id));
        return toResponse(watch);
    }

    /**
     * Create a new stock watch with initialization from historical data.
     */
    @Transactional
    public StockWatchDTO.Response createWatch(StockWatchDTO.CreateRequest request) {
        if (!xueqiuService.isValidStockCode(request.getStockCode())) {
            throw new IllegalArgumentException("无效的股票代码: " + request.getStockCode() + "，仅支持6位沪深A股代码");
        }

        if (watchRepository.existsByStockCode(request.getStockCode())) {
            throw new IllegalArgumentException("股票代码已存在: " + request.getStockCode());
        }

        // 自动获取股票名称
        String stockName = request.getStockName();
        if (stockName == null || stockName.isBlank()) {
            stockName = tushareService.getStockName(request.getStockCode()).orElse(null);
        }

        StockWatch watch = StockWatch.builder()
                .stockCode(request.getStockCode())
                .stockName(stockName)
                .signalState(SignalState.NONE)
                .enabled(true)
                .build();

        // Initialize observation state from last 5 trading days
        initializeWatchState(watch);

        return toResponse(watchRepository.save(watch));
    }

    private void initializeWatchState(StockWatch watch) {
        try {
            KLineFetchResult result = fetchKLineData(watch.getStockCode(), KLINE_COUNT);

            // Tushare returns completed trading day data, trust it
            if (result.rows().isEmpty()) {
                log.info("No data available for {}, initializing with NONE state", watch.getStockCode());
                return;
            }

            // Take last 5 trading days from Tushare (data is already completed trading days)
            List<KLineRowData> historyRows = result.rows().size() > INIT_HISTORY_DAYS
                    ? result.rows().subList(0, INIT_HISTORY_DAYS)
                    : result.rows();

            // Evaluate observation state
            StockSignalService.SignalEvaluationResult eval = signalService.evaluateObservationState(historyRows);
            watch.setSignalState(eval.newState());

            if (eval.newState() != SignalState.NONE) {
                watch.setWatchStartDate(historyRows.get(historyRows.size() - 1).toLocalDate());
                log.info("Initialized {} with state {} from {} trading days (source: {})",
                        watch.getStockCode(), eval.newState(), historyRows.size(), result.source());
            }

        } catch (Exception e) {
            log.warn("Failed to initialize watch state for {}: {}", watch.getStockCode(), e.getMessage());
        }
    }

    private List<KLineRowData> filterCompletedDays(List<KLineRowData> rows) {
        LocalTime now = LocalTime.now(clock);
        LocalDate today = LocalDate.now(clock);

        if (now.isBefore(POST_CLOSE_WINDOW)) {
            // Before 15:30, exclude today's data
            return rows.stream()
                    .filter(r -> !r.toLocalDate().equals(today))
                    .toList();
        }
        return rows;
    }

    /**
     * Update a stock watch.
     */
    @Transactional
    public StockWatchDTO.Response updateWatch(Long id, StockWatchDTO.UpdateRequest request) {
        StockWatch watch = watchRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Stock watch not found: " + id));

        if (request.getStockName() != null) {
            watch.setStockName(request.getStockName());
        }
        if (request.getEnabled() != null) {
            watch.setEnabled(request.getEnabled());
        }

        return toResponse(watchRepository.save(watch));
    }

    /**
     * Delete a stock watch.
     */
    @Transactional
    public void deleteWatch(Long id) {
        watchRepository.deleteById(id);
    }

    /**
     * Perform immediate check on a stock watch.
     * Behavior differs based on time window.
     */
    @Transactional
    public StockWatchDTO.CheckResult checkNow(Long id) {
        StockWatch watch = watchRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Stock watch not found: " + id));

        return performCheck(watch, TriggerSource.MANUAL_CHECK);
    }

    /**
     * Check if we're in post-close window (after 15:30).
     */
    public boolean isPostCloseWindow() {
        return LocalTime.now(clock).isAfter(POST_CLOSE_WINDOW) ||
               LocalTime.now(clock).equals(POST_CLOSE_WINDOW);
    }

    /**
     * Perform stock check (scheduled or manual).
     */
    @Transactional
    public StockWatchDTO.CheckResult performCheck(StockWatch watch, TriggerSource triggerSource) {
        LocalTime now = LocalTime.now(clock);
        boolean isPostClose = now.isAfter(POST_CLOSE_WINDOW) || now.equals(POST_CLOSE_WINDOW);

        try {
            // Check if Xueqiu is configured
            StockMonitorConfig config = configRepository.getConfig();
            if (!Boolean.TRUE.equals(config.getXueqiuConfigured())) {
                DataSource source = getDataSource();
                // Only return config missing if we're supposed to use Xueqiu
                if (source != DataSource.TUSHARE) {
                    watch.setLastCheckStatus(CheckStatus.SKIPPED_CONFIG_MISSING.name());
                    watch.setLastCheckMessage("雪球配置缺失");
                    watch.setLastCheckedAt(LocalDateTime.now(clock));
                    watchRepository.save(watch);
                    return buildCheckResult(watch, null, null, CheckStatus.SKIPPED_CONFIG_MISSING,
                            "雪球Cookie未配置", null);
                }
            }

            KLineFetchResult result = fetchKLineData(watch.getStockCode(), KLINE_COUNT);

            // Tushare returns completed trading day data, trust it
            if (result.rows().isEmpty()) {
                watch.setLastCheckStatus(CheckStatus.SKIPPED_INSUFFICIENT_DATA.name());
                watch.setLastCheckMessage("无K线数据 (数据源: " + result.source() + ")");
                watch.setLastCheckedAt(LocalDateTime.now(clock));
                watchRepository.save(watch);
                return buildCheckResult(watch, null, null, CheckStatus.SKIPPED_INSUFFICIENT_DATA,
                        "无K线数据 (数据源: " + result.source() + ")", null);
            }

            KLineRowData latestRow = result.rows().get(0);
            LocalDate latestDate = latestRow.toLocalDate();
            LocalDate today = LocalDate.now(clock);

            // Before post-close window: preview only
            if (!isPostClose) {
                return buildCheckResult(watch, latestRow, null, CheckStatus.SKIPPED_BEFORE_POST_CLOSE_WINDOW,
                        "收盘前窗口，监控状态未更新",
                        new StockSignalService.SignalEvaluationResult(watch.getSignalState(), null,
                                "预览模式", false));
            }

            // Post-close: full check
            if (result.rows().size() < 2) {
                watch.setLastCheckStatus(CheckStatus.SKIPPED_INSUFFICIENT_DATA.name());
                watch.setLastCheckMessage("数据不足，无法评估信号");
                watch.setLastCheckedAt(LocalDateTime.now(clock));
                watchRepository.save(watch);
                return buildCheckResult(watch, latestRow, null, CheckStatus.SKIPPED_INSUFFICIENT_DATA,
                        "数据不足", null);
            }

            KLineRowData yesterdayRow = result.rows().get(1);

            // Save or update snapshot
            saveSnapshot(watch.getStockCode(), latestRow, yesterdayRow);

            // Evaluate signal
            BigDecimal jToday = latestRow.kdjj();
            BigDecimal jYesterday = yesterdayRow.kdjj();
            BigDecimal macdToday = latestRow.macd();
            BigDecimal macdYesterday = yesterdayRow.macd();

            // First check if current state leads to signal confirmation
            StockSignalService.SignalEvaluationResult signalResult = signalService.evaluateSignalConfirmation(
                    watch.getSignalState(), jToday, jYesterday, macdToday, macdYesterday);

            if (signalResult.ambiguous()) {
                watch.setLastCheckStatus(CheckStatus.FAILED_AMBIGUOUS_SIGNAL.name());
                watch.setLastCheckMessage("买卖信号冲突");
                watch.setLastCheckedAt(LocalDateTime.now(clock));
                watchRepository.save(watch);
                return buildCheckResult(watch, latestRow, yesterdayRow, CheckStatus.FAILED_AMBIGUOUS_SIGNAL,
                        signalResult.reason(), signalResult);
            }

            NotificationStatus notifyStatus = triggerSource == TriggerSource.MANUAL_CHECK
                    ? NotificationStatus.NOT_SENT_MANUAL_CHECK
                    : NotificationStatus.NOT_SENT;

            if (signalResult.signalType() != null) {
                // Signal confirmed - write log and reset state
                StockSignalLog signalLog = writeSignalLog(watch, latestRow, jToday, jYesterday,
                        macdToday, macdYesterday, signalResult, triggerSource, notifyStatus);

                watch.setSignalState(SignalState.NONE);
                watch.setLastSignalType(signalResult.signalType());
                watch.setLastSignalDate(latestDate);

                // Try to send notification
                if (triggerSource == TriggerSource.SCHEDULED) {
                    trySendNotification(watch, latestRow, signalResult, signalLog);
                }

                watch.setLastCheckStatus(CheckStatus.SUCCESS.name());
                watch.setLastCheckMessage("信号已确认: " + signalResult.signalType());
            } else {
                // No signal - check for state transition
                StockSignalService.SignalEvaluationResult stateResult = signalService.evaluateObservationState(result.rows());

                if (stateResult.newState() != watch.getSignalState()) {
                    if (stateResult.newState() != SignalState.NONE) {
                        watch.setWatchStartDate(latestDate);
                    }
                    watch.setSignalState(stateResult.newState());
                    log.info("Stock {} state changed to {}", watch.getStockCode(), stateResult.newState());
                }

                watch.setLastCheckStatus(CheckStatus.SUCCESS.name());
                watch.setLastCheckMessage(stateResult.reason());
            }

            watch.setLastCheckedAt(LocalDateTime.now(clock));
            watchRepository.save(watch);

            return buildCheckResult(watch, latestRow, yesterdayRow, CheckStatus.SUCCESS,
                    watch.getLastCheckMessage(), signalResult);

        } catch (XueqiuApiException e) {
            watch.setLastCheckStatus(CheckStatus.FAILED.name());
            watch.setLastCheckMessage("获取数据失败: " + e.getMessage());
            watch.setLastCheckedAt(LocalDateTime.now(clock));
            watchRepository.save(watch);
            return buildCheckResult(watch, null, null, CheckStatus.FAILED,
                    "获取数据失败: " + e.getMessage(), null);
        } catch (Exception e) {
            log.error("Unexpected error checking stock {}", watch.getStockCode(), e);
            watch.setLastCheckStatus(CheckStatus.FAILED.name());
            watch.setLastCheckMessage("检查失败: " + e.getMessage());
            watch.setLastCheckedAt(LocalDateTime.now(clock));
            watchRepository.save(watch);
            return buildCheckResult(watch, null, null, CheckStatus.FAILED,
                    "检查失败: " + e.getMessage(), null);
        }
    }

    private void saveSnapshot(String stockCode, KLineRowData latestRow, KLineRowData yesterdayRow) {
        LocalDate tradeDate = latestRow.toLocalDate();

        StockIndicatorSnapshot snapshot = snapshotRepository
                .findByStockCodeAndTradeDate(stockCode, tradeDate)
                .orElse(StockIndicatorSnapshot.builder()
                        .stockCode(stockCode)
                        .tradeDate(tradeDate)
                        .build());

        snapshot.setOpenPrice(latestRow.open());
        snapshot.setHighPrice(latestRow.high());
        snapshot.setLowPrice(latestRow.low());
        snapshot.setClosePrice(latestRow.close());
        snapshot.setVolume(latestRow.volume());
        snapshot.setLastCheckedAt(LocalDateTime.now(clock));

        snapshotRepository.save(snapshot);
    }

    private StockSignalLog writeSignalLog(StockWatch watch, KLineRowData latestRow,
                                          BigDecimal jToday, BigDecimal jYesterday,
                                          BigDecimal macdToday, BigDecimal macdYesterday,
                                          StockSignalService.SignalEvaluationResult signalResult,
                                          TriggerSource triggerSource,
                                          NotificationStatus notifyStatus) {
        StockSignalLog signalLog = StockSignalLog.builder()
                .stockCode(watch.getStockCode())
                .tradeDate(latestRow.toLocalDate())
                .signalType(signalResult.signalType())
                .triggerSource(triggerSource)
                .closePrice(latestRow.close())
                .jToday(jToday)
                .jYesterday(jYesterday)
                .macdToday(macdToday)
                .macdYesterday(macdYesterday)
                .evaluationSummary(signalResult.reason())
                .notificationStatus(notifyStatus)
                .build();

        return signalLogRepository.save(signalLog);
    }

    private void trySendNotification(StockWatch watch, KLineRowData latestRow,
                                      StockSignalService.SignalEvaluationResult signalResult,
                                      StockSignalLog signalLog) {
        StockMonitorConfig config = configRepository.getConfig();
        if (!Boolean.TRUE.equals(config.getWechatConfigured())) {
            log.warn("WeChat not configured, skipping notification for {}", watch.getStockCode());
            return;
        }

        // Check for duplicate notification
        boolean alreadySent = signalLogRepository.existsByStockCodeAndTradeDateAndSignalTypeAndNotificationStatus(
                watch.getStockCode(), latestRow.toLocalDate(), signalResult.signalType(), NotificationStatus.SENT);

        if (alreadySent) {
            log.info("Notification already sent for {} on {}, skipping", watch.getStockCode(), latestRow.toLocalDate());
            return;
        }

        try {
            weChatService.sendSignalNotification(
                    signalResult.signalType(),
                    watch.getStockCode(),
                    watch.getStockName(),
                    latestRow.close().toString(),
                    latestRow.kdjj() != null ? latestRow.kdjj().toString() : "N/A",
                    latestRow.macd() != null ? latestRow.macd().toString() : "N/A",
                    signalResult.reason()
            );

            // Mark all matching logs as sent
            markNotificationsAsSent(watch.getStockCode(), latestRow.toLocalDate(), signalResult.signalType());
            retryPendingNotifications(watch.getStockCode(), latestRow.toLocalDate(), signalResult.signalType());

        } catch (Exception e) {
            log.error("Failed to send notification for {}: {}", watch.getStockCode(), e.getMessage());
            signalLog.setNotificationStatus(NotificationStatus.FAILED);
            signalLog.setNotificationAttempts(signalLog.getNotificationAttempts() + 1);
            signalLog.setNotificationFailureReason(e.getMessage());
            signalLogRepository.save(signalLog);
        }
    }

    private void markNotificationsAsSent(String stockCode, LocalDate tradeDate, String signalType) {
        List<StockSignalLog> logs = signalLogRepository.findByStockCodeAndTradeDateAndSignalType(
                stockCode, tradeDate, signalType);

        for (StockSignalLog signalLog : logs) {
            signalLog.setNotificationStatus(NotificationStatus.SENT);
            signalLog.setNotificationSentAt(LocalDateTime.now(clock));
        }
        signalLogRepository.saveAll(logs);
    }

    private void retryPendingNotifications(String stockCode, LocalDate tradeDate, String signalType) {
        List<StockSignalLog> pendingLogs = signalLogRepository.findPendingByNotificationKey(
                stockCode, tradeDate, signalType, NotificationStatus.NOT_SENT_MANUAL_CHECK);

        for (StockSignalLog signalLog : pendingLogs) {
            try {
                StockWatch watch = watchRepository.findByStockCode(stockCode).orElse(null);
                weChatService.sendSignalNotification(
                        signalType,
                        stockCode,
                        watch != null ? watch.getStockName() : null,
                        signalLog.getClosePrice().toString(),
                        signalLog.getJToday() != null ? signalLog.getJToday().toString() : "N/A",
                        signalLog.getMacdToday() != null ? signalLog.getMacdToday().toString() : "N/A",
                        signalLog.getEvaluationSummary()
                );
                signalLog.setNotificationStatus(NotificationStatus.SENT);
                signalLog.setNotificationSentAt(LocalDateTime.now(clock));
                signalLogRepository.save(signalLog);
            } catch (Exception e) {
                log.warn("Retry notification failed for log {}: {}", signalLog.getId(), e.getMessage());
            }
        }
    }

    /**
     * Retry notification for a specific signal group.
     */
    @Transactional
    public void retryNotification(String stockCode, LocalDate tradeDate, String signalType) {
        StockMonitorConfig config = configRepository.getConfig();
        if (!Boolean.TRUE.equals(config.getWechatConfigured())) {
            throw new IllegalStateException("微信配置未完成");
        }

        List<StockSignalLog> logs = signalLogRepository.findByStockCodeAndTradeDateAndSignalType(
                stockCode, tradeDate, signalType);

        if (logs.isEmpty()) {
            throw new IllegalArgumentException("未找到信号记录");
        }

        StockSignalLog latestLog = logs.get(0);

        try {
            StockWatch watch = watchRepository.findByStockCode(stockCode).orElse(null);
            weChatService.sendSignalNotification(
                    signalType,
                    stockCode,
                    watch != null ? watch.getStockName() : null,
                    latestLog.getClosePrice().toString(),
                    latestLog.getJToday() != null ? latestLog.getJToday().toString() : "N/A",
                    latestLog.getMacdToday() != null ? latestLog.getMacdToday().toString() : "N/A",
                    latestLog.getEvaluationSummary()
            );

            for (StockSignalLog signalLog : logs) {
                signalLog.setNotificationStatus(NotificationStatus.SENT);
                signalLog.setNotificationSentAt(LocalDateTime.now(clock));
                signalLog.setNotificationAttempts(signalLog.getNotificationAttempts() + 1);
                signalLog.setNotificationFailureReason(null);
            }
            signalLogRepository.saveAll(logs);

        } catch (Exception e) {
            for (StockSignalLog signalLog : logs) {
                signalLog.setNotificationStatus(NotificationStatus.FAILED);
                signalLog.setNotificationAttempts(signalLog.getNotificationAttempts() + 1);
                signalLog.setNotificationFailureReason(e.getMessage());
            }
            signalLogRepository.saveAll(logs);
            throw new RuntimeException("发送通知失败: " + e.getMessage(), e);
        }
    }

    /**
     * Get signal groups for a stock (grouped by stockCode + tradeDate + signalType).
     */
    public List<StockSignalDTO.GroupResponse> getSignalGroups(String stockCode) {
        List<StockSignalLog> logs = signalLogRepository.findByStockCodeOrderByCreatedAtDesc(stockCode);

        return logs.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        log -> log.getStockCode() + "|" + log.getTradeDate() + "|" + log.getSignalType()))
                .values().stream()
                .map(this::toGroupResponse)
                .sorted((a, b) -> {
                    int dateCompare = b.getTradeDate().compareTo(a.getTradeDate());
                    if (dateCompare != 0) return dateCompare;
                    return b.getLastCheckTime().compareTo(a.getLastCheckTime());
                })
                .toList();
    }

    /**
     * Get signal logs for a specific group.
     */
    public List<StockSignalDTO.DetailResponse> getSignalDetails(String stockCode, LocalDate tradeDate, String signalType) {
        return signalLogRepository.findByStockCodeAndTradeDateAndSignalType(stockCode, tradeDate, signalType)
                .stream()
                .map(this::toDetailResponse)
                .toList();
    }

    /**
     * Get latest indicator snapshot for a stock.
     */
    public StockIndicatorDTO.SnapshotResponse getLatestSnapshot(String stockCode) {
        return snapshotRepository.findLatestByStockCode(stockCode, 1).stream()
                .findFirst()
                .map(this::toSnapshotResponse)
                .orElse(null);
    }

    /**
     * Get recent snapshots for a stock (for detail page).
     */
    public List<StockIndicatorDTO.SnapshotResponse> getRecentSnapshots(String stockCode, int limit) {
        return snapshotRepository.findLatestByStockCode(stockCode, limit).stream()
                .map(this::toSnapshotResponse)
                .toList();
    }

    // ========== Conversion Methods ==========

    private StockWatchDTO.Response toResponse(StockWatch watch) {
        return StockWatchDTO.Response.builder()
                .id(watch.getId())
                .stockCode(watch.getStockCode())
                .stockName(watch.getStockName())
                .enabled(watch.getEnabled())
                .signalState(watch.getSignalState())
                .watchStartDate(watch.getWatchStartDate())
                .lastSignalType(watch.getLastSignalType())
                .lastSignalDate(watch.getLastSignalDate())
                .lastCheckedAt(watch.getLastCheckedAt())
                .lastCheckStatus(watch.getLastCheckStatus())
                .lastCheckMessage(watch.getLastCheckMessage())
                .createdAt(watch.getCreatedAt())
                .build();
    }

    private StockWatchDTO.ListResponse toListResponse(StockWatch watch) {
        StockIndicatorSnapshot latest = snapshotRepository.findLatestByStockCode(watch.getStockCode(), 1)
                .stream().findFirst().orElse(null);

        return StockWatchDTO.ListResponse.builder()
                .id(watch.getId())
                .stockCode(watch.getStockCode())
                .stockName(watch.getStockName())
                .enabled(watch.getEnabled())
                .signalState(watch.getSignalState())
                .latestTradeDate(latest != null ? latest.getTradeDate() : null)
                .closePrice(latest != null && latest.getClosePrice() != null ? latest.getClosePrice().toString() : null)
                .jValue(latest != null && latest.getKdjj() != null ? latest.getKdjj().toString() : null)
                .macdValue(latest != null && latest.getMacd() != null ? latest.getMacd().toString() : null)
                .lastSignalType(watch.getLastSignalType())
                .lastCheckedAt(watch.getLastCheckedAt())
                .lastCheckStatus(watch.getLastCheckStatus())
                .build();
    }

    private StockWatchDTO.CheckResult buildCheckResult(StockWatch watch, KLineRowData latestRow,
                                                       KLineRowData yesterdayRow,
                                                       CheckStatus status, String message,
                                                       StockSignalService.SignalEvaluationResult signalResult) {
        return StockWatchDTO.CheckResult.builder()
                .stockCode(watch.getStockCode())
                .stockName(watch.getStockName())
                .latestTradeDate(latestRow != null ? latestRow.toLocalDate() : null)
                .closePrice(latestRow != null && latestRow.close() != null ? latestRow.close().toString() : null)
                .jToday(latestRow != null && latestRow.kdjj() != null ? latestRow.kdjj().toString() : null)
                .jYesterday(yesterdayRow != null && yesterdayRow.kdjj() != null ? yesterdayRow.kdjj().toString() : null)
                .macdToday(latestRow != null && latestRow.macd() != null ? latestRow.macd().toString() : null)
                .macdYesterday(yesterdayRow != null && yesterdayRow.macd() != null ? yesterdayRow.macd().toString() : null)
                .signalState(signalResult != null ? signalResult.newState() : watch.getSignalState())
                .signalResult(signalResult != null ? signalResult.signalType() : null)
                .noSignalReason(signalResult != null && signalResult.signalType() == null ? signalResult.reason() : null)
                .checkStatus(status.name())
                .checkMessage(message)
                .build();
    }

    private StockSignalDTO.GroupResponse toGroupResponse(List<StockSignalLog> logs) {
        StockSignalLog first = logs.get(0);
        StockSignalLog last = logs.get(logs.size() - 1);

        return StockSignalDTO.GroupResponse.builder()
                .stockCode(first.getStockCode())
                .signalType(first.getSignalType())
                .tradeDate(first.getTradeDate())
                .firstTriggerTime(first.getCreatedAt())
                .lastCheckTime(last.getCreatedAt())
                .triggerCount(logs.size())
                .notificationStatus(first.getNotificationStatus().name())
                .lastFailureReason(first.getNotificationFailureReason())
                .build();
    }

    private StockSignalDTO.DetailResponse toDetailResponse(StockSignalLog signalLog) {
        return StockSignalDTO.DetailResponse.builder()
                .id(signalLog.getId())
                .stockCode(signalLog.getStockCode())
                .tradeDate(signalLog.getTradeDate())
                .signalType(signalLog.getSignalType())
                .triggerSource(signalLog.getTriggerSource().name())
                .closePrice(signalLog.getClosePrice())
                .jToday(signalLog.getJToday())
                .jYesterday(signalLog.getJYesterday())
                .macdToday(signalLog.getMacdToday())
                .macdYesterday(signalLog.getMacdYesterday())
                .evaluationSummary(signalLog.getEvaluationSummary())
                .notificationStatus(signalLog.getNotificationStatus().name())
                .notificationAttempts(signalLog.getNotificationAttempts())
                .notificationSentAt(signalLog.getNotificationSentAt())
                .notificationFailureReason(signalLog.getNotificationFailureReason())
                .createdAt(signalLog.getCreatedAt())
                .build();
    }

    private StockIndicatorDTO.SnapshotResponse toSnapshotResponse(StockIndicatorSnapshot snapshot) {
        return StockIndicatorDTO.SnapshotResponse.builder()
                .id(snapshot.getId())
                .stockCode(snapshot.getStockCode())
                .tradeDate(snapshot.getTradeDate())
                .openPrice(snapshot.getOpenPrice())
                .highPrice(snapshot.getHighPrice())
                .lowPrice(snapshot.getLowPrice())
                .closePrice(snapshot.getClosePrice())
                .dif(snapshot.getDif())
                .dea(snapshot.getDea())
                .macd(snapshot.getMacd())
                .kdjk(snapshot.getKdjk())
                .kdjd(snapshot.getKdjd())
                .kdjj(snapshot.getKdjj())
                .sourceTimestamp(snapshot.getSourceTimestamp())
                .lastCheckedAt(snapshot.getLastCheckedAt())
                .build();
    }
}
