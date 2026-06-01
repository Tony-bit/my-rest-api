package com.example.myapi.service;

import com.example.myapi.entity.SignalState;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Pure signal state evaluation service.
 * No network or database dependencies - purely functional.
 * Evaluates KDJ/MACD indicators to determine observation state and signal confirmation.
 */
@Service
public class StockSignalService {

    private static final BigDecimal J_SELL_THRESHOLD = new BigDecimal("90");
    private static final BigDecimal J_BUY_THRESHOLD = new BigDecimal("10");

    /**
     * Result of signal evaluation.
     */
    public record SignalEvaluationResult(
            SignalState newState,
            String signalType, // "BUY", "SELL", or null
            String reason,
            boolean ambiguous
    ) {}

    /**
     * Evaluates the observation state based on historical K-line data.
     * Scans for two consecutive J values above 90 (sell watch) or below 10 (buy watch).
     *
     * @param kLineRows List of K-line rows sorted by date descending (newest first)
     * @return SignalEvaluationResult with new state
     */
    public SignalEvaluationResult evaluateObservationState(List<? extends KLineRowInput> kLineRows) {
        if (kLineRows == null || kLineRows.size() < 2) {
            return new SignalEvaluationResult(SignalState.NONE, null, "Insufficient data", false);
        }

        // Need at least 2 consecutive rows
        // Iterate from newest to oldest (rows are sorted descending)
        BigDecimal sellEntryDate = null;
        BigDecimal buyEntryDate = null;

        for (int i = 0; i < kLineRows.size() - 1; i++) {
            BigDecimal jToday = kLineRows.get(i).getJ();
            BigDecimal jYesterday = kLineRows.get(i + 1).getJ();

            if (jToday == null || jYesterday == null) {
                continue;
            }

            // Check for WATCH_SELL: two consecutive J > 90 (not >=)
            if (jToday.compareTo(J_SELL_THRESHOLD) > 0 && jYesterday.compareTo(J_SELL_THRESHOLD) > 0) {
                if (sellEntryDate == null) {
                    // Use the older date as entry date
                    sellEntryDate = jYesterday;
                }
            }

            // Check for WATCH_BUY: two consecutive J < 10 (not <=)
            if (jToday.compareTo(J_BUY_THRESHOLD) < 0 && jYesterday.compareTo(J_BUY_THRESHOLD) < 0) {
                if (buyEntryDate == null) {
                    buyEntryDate = jYesterday;
                }
            }
        }

        // Determine the state - later entry wins if both present
        if (sellEntryDate != null && buyEntryDate != null) {
            // Both present - later entry wins (we scan newest to oldest, so buy entry comes later if its index is higher)
            // Actually since we scan from top, sell is checked first at higher indices
            // The later one is whichever we found last in the scan
            // We need to compare dates, but we only have J values
            // Strategy: if both found, use the one closer to today (higher index in our scan)
            // Since we iterate i from 0, rows[i] is newer, rows[i+1] is older
            // So sellEntryDate/buyEntryDate store jYesterday value which is from older row
            // We should use the last one found
            // Simple approach: scan and track last found
        }

        // For simplicity, if both found, we need to determine which came later
        // Let's rescan to find the most recent entry
        BigDecimal latestSellEntryIndex = null;
        BigDecimal latestBuyEntryIndex = null;

        for (int i = 0; i < kLineRows.size() - 1; i++) {
            BigDecimal jToday = kLineRows.get(i).getJ();
            BigDecimal jYesterday = kLineRows.get(i + 1).getJ();

            if (jToday == null || jYesterday == null) {
                continue;
            }

            // Check sell watch
            if (jToday.compareTo(J_SELL_THRESHOLD) > 0 && jYesterday.compareTo(J_SELL_THRESHOLD) > 0) {
                latestSellEntryIndex = new BigDecimal(i);
            }

            // Check buy watch
            if (jToday.compareTo(J_BUY_THRESHOLD) < 0 && jYesterday.compareTo(J_BUY_THRESHOLD) < 0) {
                latestBuyEntryIndex = new BigDecimal(i);
            }
        }

        // Determine state: later entry wins
        SignalState newState = SignalState.NONE;
        String reason = null;

        if (latestSellEntryIndex != null && latestBuyEntryIndex != null) {
            // Both present - later entry wins (smaller index = newer)
            if (latestSellEntryIndex.compareTo(latestBuyEntryIndex) <= 0) {
                newState = SignalState.WATCH_SELL;
                reason = "J极值观察：最近出现卖出观察信号";
            } else {
                newState = SignalState.WATCH_BUY;
                reason = "J极值观察：最近出现买入观察信号";
            }
        } else if (latestSellEntryIndex != null) {
            newState = SignalState.WATCH_SELL;
            reason = "J极值观察：连续两天J>90";
        } else if (latestBuyEntryIndex != null) {
            newState = SignalState.WATCH_BUY;
            reason = "J极值观察：连续两天J<10";
        } else {
            newState = SignalState.NONE;
            reason = "未检测到J极值信号";
        }

        return new SignalEvaluationResult(newState, null, reason, false);
    }

    /**
     * Evaluates signal confirmation based on today's and yesterday's indicators.
     *
     * @param currentState Current observation state (WATCH_SELL or WATCH_BUY)
     * @param todayJ Today's J value
     * @param yesterdayJ Yesterday's J value
     * @param todayMacd Today's raw MACD value
     * @param yesterdayMacd Yesterday's raw MACD value
     * @return SignalEvaluationResult with confirmed signal type or null
     */
    public SignalEvaluationResult evaluateSignalConfirmation(
            SignalState currentState,
            BigDecimal todayJ,
            BigDecimal yesterdayJ,
            BigDecimal todayMacd,
            BigDecimal yesterdayMacd) {

        if (currentState == SignalState.NONE) {
            return new SignalEvaluationResult(SignalState.NONE, null, "无观察状态", false);
        }

        if (currentState == SignalState.WATCH_SELL) {
            return evaluateSellSignal(todayJ, yesterdayJ, todayMacd, yesterdayMacd);
        }

        if (currentState == SignalState.WATCH_BUY) {
            return evaluateBuySignal(todayJ, yesterdayJ, todayMacd, yesterdayMacd);
        }

        return new SignalEvaluationResult(SignalState.NONE, null, "未知状态", false);
    }

    private SignalEvaluationResult evaluateSellSignal(
            BigDecimal todayJ,
            BigDecimal yesterdayJ,
            BigDecimal todayMacd,
            BigDecimal yesterdayMacd) {

        // Check if both conditions are met
        boolean jDropping = todayJ != null && yesterdayJ != null && todayJ.compareTo(yesterdayJ) < 0;
        boolean macdDropping = todayMacd != null && yesterdayMacd != null && todayMacd.compareTo(yesterdayMacd) < 0;

        // Check for ambiguous signal (both buy and sell conditions met on same day)
        boolean ambiguousBuy = todayJ != null && yesterdayJ != null &&
                todayJ.compareTo(yesterdayJ) > 0 &&
                todayMacd != null && yesterdayMacd != null &&
                todayMacd.compareTo(yesterdayMacd) > 0;

        if (jDropping && macdDropping && ambiguousBuy) {
            return new SignalEvaluationResult(SignalState.NONE, null, "买卖信号冲突：数据异常", true);
        }

        if (jDropping && macdDropping) {
            return new SignalEvaluationResult(
                    SignalState.NONE,
                    "SELL",
                    String.format("卖出信号确认：J从%.2f降至%.2f，MACD从%.4f降至%.4f",
                            yesterdayJ, todayJ, yesterdayMacd, todayMacd),
                    false);
        }

        // Build reason for no signal
        StringBuilder reason = new StringBuilder("未满足卖出条件：");
        if (!jDropping) {
            if (todayJ == null || yesterdayJ == null) {
                reason.append("J值缺失 ");
            } else if (todayJ.compareTo(yesterdayJ) >= 0) {
                reason.append(String.format("J未下降(%.2f->%.2f) ", yesterdayJ, todayJ));
            }
        }
        if (!macdDropping) {
            if (todayMacd == null || yesterdayMacd == null) {
                reason.append("MACD值缺失 ");
            } else if (todayMacd.compareTo(yesterdayMacd) >= 0) {
                reason.append(String.format("MACD未下降(%.4f->%.4f) ", yesterdayMacd, todayMacd));
            }
        }

        return new SignalEvaluationResult(SignalState.WATCH_SELL, null, reason.toString().trim(), false);
    }

    private SignalEvaluationResult evaluateBuySignal(
            BigDecimal todayJ,
            BigDecimal yesterdayJ,
            BigDecimal todayMacd,
            BigDecimal yesterdayMacd) {

        // Check if both conditions are met
        boolean jRising = todayJ != null && yesterdayJ != null && todayJ.compareTo(yesterdayJ) > 0;
        boolean macdRising = todayMacd != null && yesterdayMacd != null && todayMacd.compareTo(yesterdayMacd) > 0;

        // Check for ambiguous signal (both buy and sell conditions met on same day)
        boolean ambiguousSell = todayJ != null && yesterdayJ != null &&
                todayJ.compareTo(yesterdayJ) < 0 &&
                todayMacd != null && yesterdayMacd != null &&
                todayMacd.compareTo(yesterdayMacd) < 0;

        if (jRising && macdRising && ambiguousSell) {
            return new SignalEvaluationResult(SignalState.NONE, null, "买卖信号冲突：数据异常", true);
        }

        if (jRising && macdRising) {
            return new SignalEvaluationResult(
                    SignalState.NONE,
                    "BUY",
                    String.format("买入信号确认：J从%.2f升至%.2f，MACD从%.4f升至%.4f",
                            yesterdayJ, todayJ, yesterdayMacd, todayMacd),
                    false);
        }

        // Build reason for no signal
        StringBuilder reason = new StringBuilder("未满足买入条件：");
        if (!jRising) {
            if (todayJ == null || yesterdayJ == null) {
                reason.append("J值缺失 ");
            } else if (todayJ.compareTo(yesterdayJ) <= 0) {
                reason.append(String.format("J未上升(%.2f->%.2f) ", yesterdayJ, todayJ));
            }
        }
        if (!macdRising) {
            if (todayMacd == null || yesterdayMacd == null) {
                reason.append("MACD值缺失 ");
            } else if (todayMacd.compareTo(yesterdayMacd) <= 0) {
                reason.append(String.format("MACD未上升(%.4f->%.4f) ", yesterdayMacd, todayMacd));
            }
        }

        return new SignalEvaluationResult(SignalState.WATCH_BUY, null, reason.toString().trim(), false);
    }

    /**
     * Interface for K-line row input.
     * Allows using different implementations (XueqiuService.KLineRow, test fixtures, etc.)
     */
    public interface KLineRowInput {
        BigDecimal getJ();
        BigDecimal getMacd();
    }
}
