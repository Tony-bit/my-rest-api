package com.example.myapi.entity;

/**
 * Signal trigger source.
 * SCHEDULED: Triggered by scheduled task
 * MANUAL_CHECK: Triggered by manual "check now" operation
 */
public enum TriggerSource {
    SCHEDULED,
    MANUAL_CHECK
}
