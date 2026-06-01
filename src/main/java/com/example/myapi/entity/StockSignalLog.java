package com.example.myapi.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Signal detection audit log. Allows duplicates for repeated checks.
 */
@Entity
@Table(name = "stock_signal_log", indexes = {
        @Index(name = "idx_ssl_stock_date_type", columnList = "stock_code, trade_date, signal_type"),
        @Index(name = "idx_ssl_created_at", columnList = "created_at"),
        @Index(name = "idx_ssl_notification_status", columnList = "notification_status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockSignalLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_code", nullable = false, length = 10)
    private String stockCode;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "signal_type", nullable = false, length = 20)
    private String signalType;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_source", nullable = false, length = 20)
    private TriggerSource triggerSource;

    @Column(name = "close_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal closePrice;

    @Column(name = "j_today", precision = 18, scale = 6)
    private BigDecimal jToday;

    @Column(name = "j_yesterday", precision = 18, scale = 6)
    private BigDecimal jYesterday;

    @Column(name = "macd_today", precision = 18, scale = 6)
    private BigDecimal macdToday;

    @Column(name = "macd_yesterday", precision = 18, scale = 6)
    private BigDecimal macdYesterday;

    @Column(name = "evaluation_summary", length = 500)
    private String evaluationSummary;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_status", nullable = false, length = 30)
    @Builder.Default
    private NotificationStatus notificationStatus = NotificationStatus.NOT_SENT;

    @Column(name = "notification_attempts", nullable = false)
    @Builder.Default
    private Integer notificationAttempts = 0;

    @Column(name = "notification_sent_at")
    private LocalDateTime notificationSentAt;

    @Column(name = "notification_failure_reason", length = 500)
    private String notificationFailureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
