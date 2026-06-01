package com.example.myapi.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Daily K-line indicator snapshot (KDJ, MACD) for a stock on a trade date.
 */
@Entity
@Table(name = "stock_indicator_snapshot", indexes = {
        @Index(name = "idx_sis_trade_date", columnList = "trade_date"),
        @Index(name = "idx_sis_stock_code", columnList = "stock_code")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_stock_snapshot_stock_date", columnNames = {"stock_code", "trade_date"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockIndicatorSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_code", nullable = false, length = 10)
    private String stockCode;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "open_price", precision = 18, scale = 4)
    private BigDecimal openPrice;

    @Column(name = "high_price", precision = 18, scale = 4)
    private BigDecimal highPrice;

    @Column(name = "low_price", precision = 18, scale = 4)
    private BigDecimal lowPrice;

    @Column(name = "close_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal closePrice;

    @Column(precision = 18, scale = 6)
    private BigDecimal dif;

    @Column(precision = 18, scale = 6)
    private BigDecimal dea;

    @Column(precision = 18, scale = 6)
    private BigDecimal macd;

    @Column(name = "kdjk", precision = 18, scale = 6)
    private BigDecimal kdjk;

    @Column(name = "kdjd", precision = 18, scale = 6)
    private BigDecimal kdjd;

    @Column(name = "kdjj", precision = 18, scale = 6)
    private BigDecimal kdjj;

    @Column(name = "volume", precision = 18, scale = 2)
    private BigDecimal volume;

    @Column(name = "source_timestamp")
    private Long sourceTimestamp;

    @Column(name = "last_checked_at", nullable = false)
    private LocalDateTime lastCheckedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (lastCheckedAt == null) {
            lastCheckedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        lastCheckedAt = LocalDateTime.now();
    }
}
