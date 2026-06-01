package com.example.myapi.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XueqiuService.
 * Tests stock code validation and conversion.
 */
class XueqiuServiceTest {

    private XueqiuService service;

    @BeforeEach
    void setUp() {
        service = new XueqiuService();
    }

    // ========== Stock Code Validation Tests ==========

    @Nested
    @DisplayName("Stock Code Validation Tests")
    class StockCodeValidationTests {

        @Test
        @DisplayName("XSV-01: Valid Shanghai code 600000")
        void validShanghaiCode() {
            assertTrue(service.isValidStockCode("600000"));
        }

        @Test
        @DisplayName("XSV-02: Valid Shenzhen code 000001")
        void validShenzhenCode() {
            assertTrue(service.isValidStockCode("000001"));
        }

        @Test
        @DisplayName("XSV-03: Valid GEM code 300001")
        void validGemCode() {
            assertTrue(service.isValidStockCode("300001"));
        }

        @Test
        @DisplayName("XSV-04: Invalid 5-digit code")
        void invalid5DigitCode() {
            assertFalse(service.isValidStockCode("60000"));
        }

        @Test
        @DisplayName("XSV-05: Invalid prefix 7xxxxx")
        void invalidPrefix7() {
            assertFalse(service.isValidStockCode("700000"));
        }

        @Test
        @DisplayName("XSV-06: Invalid alphabetic code")
        void invalidAlphabeticCode() {
            assertFalse(service.isValidStockCode("ABCDEF"));
        }

        @Test
        @DisplayName("XSV-07: Empty string")
        void emptyString() {
            assertFalse(service.isValidStockCode(""));
        }

        @Test
        @DisplayName("XSV-08: Null code")
        void nullCode() {
            assertFalse(service.isValidStockCode(null));
        }

        @Test
        @DisplayName("7-digit code is invalid")
        void sevenDigitCode() {
            assertFalse(service.isValidStockCode("6000001"));
        }
    }

    // ========== Stock Code Conversion Tests ==========

    @Nested
    @DisplayName("Stock Code Conversion Tests")
    class StockCodeConversionTests {

        @Test
        @DisplayName("6xxxxx converts to SH6xxxxx")
        void shanghaiConversion() {
            assertEquals("SH600000", service.toXueqiuCode("600000"));
        }

        @Test
        @DisplayName("0xxxxx converts to SZ0xxxxx")
        void shenzhenConversion() {
            assertEquals("SZ000001", service.toXueqiuCode("000001"));
        }

        @Test
        @DisplayName("3xxxxx converts to SZ3xxxxx")
        void gemConversion() {
            assertEquals("SZ300001", service.toXueqiuCode("300001"));
        }

        @Test
        @DisplayName("Invalid code throws exception")
        void invalidCodeThrows() {
            assertThrows(IllegalArgumentException.class, () -> service.toXueqiuCode("ABCDEF"));
        }

        @Test
        @DisplayName("Null code throws exception")
        void nullCodeThrows() {
            assertThrows(IllegalArgumentException.class, () -> service.toXueqiuCode(null));
        }

        @Test
        @DisplayName("Edge case: 600001")
        void edgeCase600001() {
            assertEquals("SH600001", service.toXueqiuCode("600001"));
        }

        @Test
        @DisplayName("Edge case: 000002")
        void edgeCase000002() {
            assertEquals("SZ000002", service.toXueqiuCode("000002"));
        }

        @Test
        @DisplayName("Edge case: 399001")
        void edgeCase399001() {
            assertEquals("SZ399001", service.toXueqiuCode("399001"));
        }
    }
}
