package com.example.myapi.entity;

/**
 * Stock signal observation state.
 * NONE: No observation state
 * WATCH_SELL: Watching for sell signal (two consecutive J > 90)
 * WATCH_BUY: Watching for buy signal (two consecutive J < 10)
 */
public enum SignalState {
    NONE,
    WATCH_SELL,
    WATCH_BUY
}
