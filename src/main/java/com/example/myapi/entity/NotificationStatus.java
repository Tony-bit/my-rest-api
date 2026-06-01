package com.example.myapi.entity;

/**
 * Notification delivery status.
 * NOT_SENT: Not yet sent
 * NOT_SENT_MANUAL_CHECK: Not sent, triggered by manual check
 * SENT: Successfully sent
 * FAILED: Failed to send
 */
public enum NotificationStatus {
    NOT_SENT,
    NOT_SENT_MANUAL_CHECK,
    SENT,
    FAILED
}
