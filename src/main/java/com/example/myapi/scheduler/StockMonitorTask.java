package com.example.myapi.scheduler;

import com.example.myapi.entity.StockWatch;
import com.example.myapi.entity.TriggerSource;
import com.example.myapi.repository.StockMonitorConfigRepository;
import com.example.myapi.repository.StockWatchRepository;
import com.example.myapi.service.StockMonitorService;
import com.example.myapi.service.TushareService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Scheduled task for daily stock indicator monitoring.
 * Runs at 17:10 Asia/Shanghai on weekdays (Mon-Fri).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockMonitorTask {

    private final StockWatchRepository watchRepository;
    private final StockMonitorConfigRepository configRepository;
    private final StockMonitorService monitorService;
    private final TushareService tushareService;

    private static final int MAX_RETRIES = 3;

    @Scheduled(cron = "0 10 17 * * MON-FRI", zone = "Asia/Shanghai")
    public void onScheduledMonitor() {
        log.info("Scheduled stock monitor task started");

        // Validate Xueqiu cookie
        if (!isXueqiuConfigured()) {
            log.error("Stock monitor task aborted: Xueqiu cookie not configured");
            return;
        }

        // Check if today is a trading day
        LocalDate today = LocalDate.now();
        if (!tushareService.isTradingDay(today)) {
            log.info("Skipping non-trading day: {}", today);
            return;
        }

        // Process enabled watches with retry
        executeWithRetry();

        log.info("Scheduled stock monitor task completed for {}", today);
    }

    private boolean isXueqiuConfigured() {
        return configRepository.getConfig() != null &&
               Boolean.TRUE.equals(configRepository.getConfig().getXueqiuConfigured());
    }

    private void executeWithRetry() {
        int attempt = 0;
        Exception lastException = null;

        while (attempt < MAX_RETRIES) {
            attempt++;
            try {
                executeMonitorCycle();
                return; // Success
            } catch (Exception e) {
                lastException = e;
                log.warn("Stock monitor attempt {} failed: {}", attempt, e.getMessage());
                if (attempt < MAX_RETRIES) {
                    // Exponential backoff: 2s, 4s, 8s
                    try {
                        Thread.sleep(2000L * (1L << (attempt - 1)));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        log.error("Stock monitor task failed after {} attempts", MAX_RETRIES, lastException);
    }

    private void executeMonitorCycle() {
        List<StockWatch> enabledWatches = watchRepository.findByEnabledTrue();
        log.info("Processing {} enabled stock watches", enabledWatches.size());

        int successCount = 0;
        int failCount = 0;

        for (StockWatch watch : enabledWatches) {
            try {
                monitorService.performCheck(watch, TriggerSource.SCHEDULED);
                successCount++;
            } catch (Exception e) {
                log.error("Failed to check stock {}: {}", watch.getStockCode(), e.getMessage());
                failCount++;
                // Continue with other stocks
            }
        }

        log.info("Stock monitor cycle completed: {} success, {} failed", successCount, failCount);
    }
}
