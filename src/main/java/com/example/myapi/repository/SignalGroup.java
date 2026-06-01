package com.example.myapi.repository;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Projection for grouped signal queries.
 */
public record SignalGroup(String stockCode, LocalDate tradeDate, String signalType) {}
