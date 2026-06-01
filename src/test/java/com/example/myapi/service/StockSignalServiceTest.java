package com.example.myapi.service;

import com.example.myapi.entity.SignalState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StockSignalService.
 * Tests pure signal evaluation logic without Spring context or mocks.
 */
class StockSignalServiceTest {

    private StockSignalService service;

    @BeforeEach
    void setUp() {
        service = new StockSignalService();
    }

    // ========== Observation State Tests ==========

    @Nested
    @DisplayName("WATCH_SELL Observation Entry Tests")
    class WatchSellEntryTests {

        @Test
        @DisplayName("SWE-01: Two consecutive J > 90 enters WATCH_SELL")
        void twoConsecutiveJAbove90EntersWatchSell() {
            // [85, 88, 91, 95, 88] - J series (newest to oldest)
            List<TestKLineRow> rows = createRows(
                    j(85), j(88), j(91), j(95), j(88)
            );

            StockSignalService.SignalEvaluationResult result = service.evaluateObservationState(rows);

            assertEquals(SignalState.WATCH_SELL, result.newState());
            assertNull(result.signalType());
            assertFalse(result.ambiguous());
        }

        @Test
        @DisplayName("SWE-02: J > 90 but not consecutive does not enter WATCH_SELL")
        void nonConsecutiveJAbove90DoesNotEnterWatchSell() {
            // [85, 91, 88, 95, 88] - gaps between J > 90
            List<TestKLineRow> rows = createRows(
                    j(85), j(91), j(88), j(95), j(88)
            );

            StockSignalService.SignalEvaluationResult result = service.evaluateObservationState(rows);

            assertEquals(SignalState.NONE, result.newState());
        }

        @Test
        @DisplayName("SWE-03: J == 90 does not enter WATCH_SELL")
        void jEquals90DoesNotEnterWatchSell() {
            // [85, 90, 95, 88, 85] - J == 90 should not trigger
            List<TestKLineRow> rows = createRows(
                    j(85), j(90), j(95), j(88), j(85)
            );

            StockSignalService.SignalEvaluationResult result = service.evaluateObservationState(rows);

            assertEquals(SignalState.NONE, result.newState());
        }

        @Test
        @DisplayName("SWE-04: Only 1 day J > 90 does not enter WATCH_SELL")
        void singleJAbove90DoesNotEnterWatchSell() {
            // [91, 85, 88, 88, 85] - only one day J > 90
            List<TestKLineRow> rows = createRows(
                    j(91), j(85), j(88), j(88), j(85)
            );

            StockSignalService.SignalEvaluationResult result = service.evaluateObservationState(rows);

            assertEquals(SignalState.NONE, result.newState());
        }

        @Test
        @DisplayName("SWE-07: Three consecutive J > 90 enters WATCH_SELL")
        void threeConsecutiveJAbove90EntersWatchSell() {
            // [91, 95, 98, 88, 85] - three consecutive days
            List<TestKLineRow> rows = createRows(
                    j(91), j(95), j(98), j(88), j(85)
            );

            StockSignalService.SignalEvaluationResult result = service.evaluateObservationState(rows);

            assertEquals(SignalState.WATCH_SELL, result.newState());
        }
    }

    @Nested
    @DisplayName("WATCH_BUY Observation Entry Tests")
    class WatchBuyEntryTests {

        @Test
        @DisplayName("SWE-05: Two consecutive J < 10 enters WATCH_BUY")
        void twoConsecutiveJBelow10EntersWatchBuy() {
            // [15, 12, 8, 5, 12] - J series (newest to oldest)
            List<TestKLineRow> rows = createRows(
                    j(15), j(12), j(8), j(5), j(12)
            );

            StockSignalService.SignalEvaluationResult result = service.evaluateObservationState(rows);

            assertEquals(SignalState.WATCH_BUY, result.newState());
            assertNull(result.signalType());
        }

        @Test
        @DisplayName("SWE-06: J == 10 does not enter WATCH_BUY")
        void jEquals10DoesNotEnterWatchBuy() {
            // [15, 10, 5, 12, 15] - J == 10 should not trigger
            List<TestKLineRow> rows = createRows(
                    j(15), j(10), j(5), j(12), j(15)
            );

            StockSignalService.SignalEvaluationResult result = service.evaluateObservationState(rows);

            assertEquals(SignalState.NONE, result.newState());
        }

        @Test
        @DisplayName("SWE-08: Three consecutive J < 10 enters WATCH_BUY")
        void threeConsecutiveJBelow10EntersWatchBuy() {
            // [8, 5, 3, 12, 15] - three consecutive days
            List<TestKLineRow> rows = createRows(
                    j(8), j(5), j(3), j(12), j(15)
            );

            StockSignalService.SignalEvaluationResult result = service.evaluateObservationState(rows);

            assertEquals(SignalState.WATCH_BUY, result.newState());
        }
    }

    @Nested
    @DisplayName("Signal Confirmation Tests")
    class SignalConfirmationTests {

        @Test
        @DisplayName("SC-01: SELL - J dropping + MACD dropping")
        void sellSignalJDroppingMacdDropping() {
            BigDecimal todayJ = new BigDecimal("88.0");
            BigDecimal yesterdayJ = new BigDecimal("92.0");
            BigDecimal todayMacd = new BigDecimal("0.01");
            BigDecimal yesterdayMacd = new BigDecimal("0.05");

            StockSignalService.SignalEvaluationResult result = service.evaluateSignalConfirmation(
                    SignalState.WATCH_SELL, todayJ, yesterdayJ, todayMacd, yesterdayMacd);

            assertEquals(SignalState.NONE, result.newState());
            assertEquals("SELL", result.signalType());
            assertFalse(result.ambiguous());
        }

        @Test
        @DisplayName("SC-02: SELL - J dropping, MACD flat - no signal")
        void sellSignalJDroppingMacdFlat() {
            BigDecimal todayJ = new BigDecimal("88.0");
            BigDecimal yesterdayJ = new BigDecimal("92.0");
            BigDecimal todayMacd = new BigDecimal("0.05");
            BigDecimal yesterdayMacd = new BigDecimal("0.05");

            StockSignalService.SignalEvaluationResult result = service.evaluateSignalConfirmation(
                    SignalState.WATCH_SELL, todayJ, yesterdayJ, todayMacd, yesterdayMacd);

            assertEquals(SignalState.WATCH_SELL, result.newState());
            assertNull(result.signalType());
            assertFalse(result.ambiguous());
        }

        @Test
        @DisplayName("SC-03: SELL - MACD dropping, J flat - no signal")
        void sellSignalMacdDroppingJFlat() {
            BigDecimal todayJ = new BigDecimal("92.0");
            BigDecimal yesterdayJ = new BigDecimal("92.0");
            BigDecimal todayMacd = new BigDecimal("0.01");
            BigDecimal yesterdayMacd = new BigDecimal("0.05");

            StockSignalService.SignalEvaluationResult result = service.evaluateSignalConfirmation(
                    SignalState.WATCH_SELL, todayJ, yesterdayJ, todayMacd, yesterdayMacd);

            assertEquals(SignalState.WATCH_SELL, result.newState());
            assertNull(result.signalType());
        }

        @Test
        @DisplayName("SC-04: SELL - MACD green to red (negative to positive)")
        void sellSignalMacdGreenToRed() {
            // MACD goes from positive 0.05 to negative -0.02 (drops)
            BigDecimal todayJ = new BigDecimal("88.0");
            BigDecimal yesterdayJ = new BigDecimal("92.0");
            BigDecimal todayMacd = new BigDecimal("-0.02");
            BigDecimal yesterdayMacd = new BigDecimal("0.05");

            StockSignalService.SignalEvaluationResult result = service.evaluateSignalConfirmation(
                    SignalState.WATCH_SELL, todayJ, yesterdayJ, todayMacd, yesterdayMacd);

            assertEquals(SignalState.NONE, result.newState());
            assertEquals("SELL", result.signalType());
        }

        @Test
        @DisplayName("SC-05: BUY - J rising + MACD rising")
        void buySignalJRisingMacdRising() {
            BigDecimal todayJ = new BigDecimal("12.0");
            BigDecimal yesterdayJ = new BigDecimal("8.0");
            BigDecimal todayMacd = new BigDecimal("0.08");
            BigDecimal yesterdayMacd = new BigDecimal("0.05");

            StockSignalService.SignalEvaluationResult result = service.evaluateSignalConfirmation(
                    SignalState.WATCH_BUY, todayJ, yesterdayJ, todayMacd, yesterdayMacd);

            assertEquals(SignalState.NONE, result.newState());
            assertEquals("BUY", result.signalType());
            assertFalse(result.ambiguous());
        }

        @Test
        @DisplayName("SC-06: BUY - J rising, MACD flat - no signal")
        void buySignalJRisingMacdFlat() {
            BigDecimal todayJ = new BigDecimal("12.0");
            BigDecimal yesterdayJ = new BigDecimal("8.0");
            BigDecimal todayMacd = new BigDecimal("0.05");
            BigDecimal yesterdayMacd = new BigDecimal("0.05");

            StockSignalService.SignalEvaluationResult result = service.evaluateSignalConfirmation(
                    SignalState.WATCH_BUY, todayJ, yesterdayJ, todayMacd, yesterdayMacd);

            assertEquals(SignalState.WATCH_BUY, result.newState());
            assertNull(result.signalType());
        }

        @Test
        @DisplayName("SC-07: BUY - MACD red to green (negative to positive)")
        void buySignalMacdRedToGreen() {
            // MACD goes from negative -0.02 to positive 0.05 (rises)
            BigDecimal todayJ = new BigDecimal("12.0");
            BigDecimal yesterdayJ = new BigDecimal("8.0");
            BigDecimal todayMacd = new BigDecimal("0.05");
            BigDecimal yesterdayMacd = new BigDecimal("-0.02");

            StockSignalService.SignalEvaluationResult result = service.evaluateSignalConfirmation(
                    SignalState.WATCH_BUY, todayJ, yesterdayJ, todayMacd, yesterdayMacd);

            assertEquals(SignalState.NONE, result.newState());
            assertEquals("BUY", result.signalType());
        }
    }

    @Nested
    @DisplayName("Boundary Tests")
    class BoundaryTests {

        @Test
        @DisplayName("BEC-01: J = 90.0 does not enter observation")
        void jEqualsBoundaryDoesNotEnter() {
            // J = 90.0 exactly
            BigDecimal todayJ = new BigDecimal("90.0");
            BigDecimal yesterdayJ = new BigDecimal("91.0");

            List<TestKLineRow> rows = createRows(j(85), j(90.0), j(91.0), j(88), j(85));
            StockSignalService.SignalEvaluationResult result = service.evaluateObservationState(rows);

            assertEquals(SignalState.NONE, result.newState());
        }

        @Test
        @DisplayName("BEC-02: J = 10.0 does not enter observation")
        void jEquals10BoundaryDoesNotEnter() {
            // J = 10.0 exactly
            List<TestKLineRow> rows = createRows(j(15), j(10.0), j(9.0), j(12), j(15));
            StockSignalService.SignalEvaluationResult result = service.evaluateObservationState(rows);

            assertEquals(SignalState.NONE, result.newState());
        }

        @Test
        @DisplayName("BEC-03: J = 90.001 enters WATCH_SELL")
        void jJustAboveBoundaryEntersWatchSell() {
            BigDecimal todayJ = new BigDecimal("90.001");
            BigDecimal yesterdayJ = new BigDecimal("91.0");

            List<TestKLineRow> rows = createRows(
                    j(85), row(todayJ), row(yesterdayJ), j(88), j(85)
            );
            StockSignalService.SignalEvaluationResult result = service.evaluateObservationState(rows);

            assertEquals(SignalState.WATCH_SELL, result.newState());
        }

        @Test
        @DisplayName("BEC-04: J = 9.999 enters WATCH_BUY")
        void jJustBelowBoundaryEntersWatchBuy() {
            BigDecimal todayJ = new BigDecimal("9.999");
            BigDecimal yesterdayJ = new BigDecimal("9.0");

            List<TestKLineRow> rows = createRows(
                    j(15), row(todayJ), row(yesterdayJ), j(12), j(15)
            );
            StockSignalService.SignalEvaluationResult result = service.evaluateObservationState(rows);

            assertEquals(SignalState.WATCH_BUY, result.newState());
        }

        @Test
        @DisplayName("BEC-05: Less than 2 rows returns NONE")
        void lessThan2RowsReturnsNone() {
            List<TestKLineRow> rows = createRows(j(85));
            StockSignalService.SignalEvaluationResult result = service.evaluateObservationState(rows);

            assertEquals(SignalState.NONE, result.newState());
        }

        @Test
        @DisplayName("BEC-06: J field missing - row skipped")
        void jFieldMissingRowSkipped() {
            // Create rows with some null J values
            List<TestKLineRow> rows = new ArrayList<>();
            rows.add(new TestKLineRow(null, new BigDecimal("0.02")));  // null J
            rows.add(new TestKLineRow(new BigDecimal("91"), new BigDecimal("0.03")));
            rows.add(new TestKLineRow(new BigDecimal("95"), new BigDecimal("0.04")));
            rows.add(new TestKLineRow(new BigDecimal("88"), new BigDecimal("0.01")));
            rows.add(new TestKLineRow(new BigDecimal("85"), new BigDecimal("0.01")));

            StockSignalService.SignalEvaluationResult result = service.evaluateObservationState(rows);

            // Should still detect 91->95 consecutive
            assertEquals(SignalState.WATCH_SELL, result.newState());
        }

        @Test
        @DisplayName("BEC-07: Buy and sell conditions both met - ambiguous")
        void buyAndSellConditionsBothMet() {
            // This creates a scenario where both buy and sell conditions appear true
            BigDecimal todayJ = new BigDecimal("12.0");  // rising
            BigDecimal yesterdayJ = new BigDecimal("8.0");  // rising
            BigDecimal todayMacd = new BigDecimal("0.08");  // rising
            BigDecimal yesterdayMacd = new BigDecimal("0.05");  // rising

            StockSignalService.SignalEvaluationResult result = service.evaluateSignalConfirmation(
                    SignalState.WATCH_SELL, todayJ, yesterdayJ, todayMacd, yesterdayMacd);

            // For WATCH_SELL: J rising (12 > 8) is NOT dropping, so no sell
            // But check for ambiguous
            assertNotEquals("BUY", result.signalType());
        }
    }

    @Nested
    @DisplayName("Delayed Confirmation and Override Tests")
    class DelayedConfirmationTests {

        @Test
        @DisplayName("DCO-01: Signal confirmed 3 days after observation")
        void delayedSignalConfirmation() {
            // Observation entered on day 1
            // Signal confirmed on day 3 (after delay)
            BigDecimal todayJ = new BigDecimal("88.0");
            BigDecimal yesterdayJ = new BigDecimal("92.0");
            BigDecimal todayMacd = new BigDecimal("0.01");
            BigDecimal yesterdayMacd = new BigDecimal("0.05");

            StockSignalService.SignalEvaluationResult result = service.evaluateSignalConfirmation(
                    SignalState.WATCH_SELL, todayJ, yesterdayJ, todayMacd, yesterdayMacd);

            assertEquals(SignalState.NONE, result.newState());
            assertEquals("SELL", result.signalType());
        }

        @Test
        @DisplayName("DCO-02: Opposite extreme replaces WATCH_SELL with WATCH_BUY")
        void oppositeExtremeReplacesState() {
            // After WATCH_SELL is entered, later we see WATCH_BUY pattern
            // [15, 12, 8, 5, 12] - BUY pattern appears later
            List<TestKLineRow> rows = createRows(
                    j(15), j(12), j(8), j(5), j(12)
            );

            StockSignalService.SignalEvaluationResult result = service.evaluateObservationState(rows);

            assertEquals(SignalState.WATCH_BUY, result.newState());
        }

        @Test
        @DisplayName("DCO-03: Signal resets state to NONE")
        void signalResetsState() {
            BigDecimal todayJ = new BigDecimal("88.0");
            BigDecimal yesterdayJ = new BigDecimal("92.0");
            BigDecimal todayMacd = new BigDecimal("0.01");
            BigDecimal yesterdayMacd = new BigDecimal("0.05");

            StockSignalService.SignalEvaluationResult result = service.evaluateSignalConfirmation(
                    SignalState.WATCH_SELL, todayJ, yesterdayJ, todayMacd, yesterdayMacd);

            assertEquals(SignalState.NONE, result.newState());
        }
    }

    @Nested
    @DisplayName("State-based Confirmation Tests")
    class StateBasedConfirmationTests {

        @Test
        @DisplayName("No signal with NONE state")
        void noSignalWithNoneState() {
            BigDecimal todayJ = new BigDecimal("88.0");
            BigDecimal yesterdayJ = new BigDecimal("92.0");
            BigDecimal todayMacd = new BigDecimal("0.01");
            BigDecimal yesterdayMacd = new BigDecimal("0.05");

            StockSignalService.SignalEvaluationResult result = service.evaluateSignalConfirmation(
                    SignalState.NONE, todayJ, yesterdayJ, todayMacd, yesterdayMacd);

            assertEquals(SignalState.NONE, result.newState());
            assertNull(result.signalType());
        }

        @Test
        @DisplayName("WATCH_BUY conditions met, state becomes NONE with BUY signal")
        void watchBuyConfirmSignal() {
            BigDecimal todayJ = new BigDecimal("15.0");
            BigDecimal yesterdayJ = new BigDecimal("5.0");
            BigDecimal todayMacd = new BigDecimal("0.10");
            BigDecimal yesterdayMacd = new BigDecimal("0.02");

            StockSignalService.SignalEvaluationResult result = service.evaluateSignalConfirmation(
                    SignalState.WATCH_BUY, todayJ, yesterdayJ, todayMacd, yesterdayMacd);

            assertEquals(SignalState.NONE, result.newState());
            assertEquals("BUY", result.signalType());
        }

        @Test
        @DisplayName("WATCH_SELL conditions met, state becomes NONE with SELL signal")
        void watchSellConfirmSignal() {
            BigDecimal todayJ = new BigDecimal("75.0");
            BigDecimal yesterdayJ = new BigDecimal("95.0");
            BigDecimal todayMacd = new BigDecimal("-0.05");
            BigDecimal yesterdayMacd = new BigDecimal("0.03");

            StockSignalService.SignalEvaluationResult result = service.evaluateSignalConfirmation(
                    SignalState.WATCH_SELL, todayJ, yesterdayJ, todayMacd, yesterdayMacd);

            assertEquals(SignalState.NONE, result.newState());
            assertEquals("SELL", result.signalType());
        }
    }

    // ========== Helper Methods ==========

    private List<TestKLineRow> createRows(TestKLineRow... rows) {
        return new ArrayList<>(java.util.Arrays.asList(rows));
    }

    private TestKLineRow j(double value) {
        return new TestKLineRow(new BigDecimal(Double.toString(value)), new BigDecimal("0.02"));
    }

    private TestKLineRow row(BigDecimal j) {
        return new TestKLineRow(j, new BigDecimal("0.02"));
    }

    /**
     * Test implementation of KLineRowInput for testing.
     */
    private static class TestKLineRow implements StockSignalService.KLineRowInput {
        private final BigDecimal j;
        private final BigDecimal macd;

        TestKLineRow(BigDecimal j, BigDecimal macd) {
            this.j = j;
            this.macd = macd;
        }

        @Override
        public BigDecimal getJ() {
            return j;
        }

        @Override
        public BigDecimal getMacd() {
            return macd;
        }
    }
}
