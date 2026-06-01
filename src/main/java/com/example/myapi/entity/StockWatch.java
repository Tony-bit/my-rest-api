package com.example.myapi.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Monitored stock with observation state.
 */
@Entity
@Table(name = "stock_watch", indexes = {
        @Index(name = "idx_sw_enabled", columnList = "enabled"),
        @Index(name = "idx_sw_signal_state", columnList = "signal_state")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockWatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_code", nullable = false, unique = true, length = 10)
    private String stockCode;

    @Column(name = "stock_name", length = 100)
    private String stockName;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_state", nullable = false, length = 20)
    @Builder.Default
    private SignalState signalState = SignalState.NONE;

    @Column(name = "watch_start_date")
    private LocalDate watchStartDate;

    @Column(name = "last_signal_type", length = 20)
    private String lastSignalType;

    @Column(name = "last_signal_date")
    private LocalDate lastSignalDate;

    @Column(name = "last_checked_at")
    private LocalDateTime lastCheckedAt;

    @Column(name = "last_check_status", length = 30)
    private String lastCheckStatus;

    @Column(name = "last_check_message", length = 500)
    private String lastCheckMessage;

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
