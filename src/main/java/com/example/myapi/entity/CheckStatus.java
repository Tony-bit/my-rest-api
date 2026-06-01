package com.example.myapi.entity;

/**
 * Stock check status result.
 */
public enum CheckStatus {
    SUCCESS,
    FAILED,
    FAILED_AMBIGUOUS_SIGNAL,
    SKIPPED_SUSPENDED_OR_NO_DATA,
    SKIPPED_INSUFFICIENT_DATA,
    SKIPPED_CONFIG_MISSING,
    SKIPPED_BEFORE_POST_CLOSE_WINDOW
}
