package com.example.myapi.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_snapshot", indexes = {
    @Index(name = "idx_snapshot_plan", columnList = "plan_id"),
    @Index(name = "idx_snapshot_date", columnList = "snapshot_date"),
    @Index(name = "idx_snapshot_stock", columnList = "stock_code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plan_id")
    private Long planId;

    @Column(name = "actual_trade_id")
    private Long actualTradeId;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "stock_code", nullable = false, length = 20)
    private String stockCode;

    @Column(name = "stock_name", length = 50)
    private String stockName;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_status", length = 20)
    private PlanStatus planStatus;

    @Column(name = "plan_return", precision = 10, scale = 4)
    private BigDecimal planReturn;

    @Column(name = "plan_return_percent", precision = 10, scale = 4)
    private BigDecimal planReturnPercent;

    @Column(name = "has_actual_trade")
    private Boolean hasActualTrade;

    @Column(name = "actual_return", precision = 10, scale = 4)
    private BigDecimal actualReturn;

    @Column(name = "actual_return_percent", precision = 10, scale = 4)
    private BigDecimal actualReturnPercent;

    @Column(name = "open_quantity", precision = 10, scale = 2)
    private BigDecimal openQuantity;

    @Column(name = "avg_cost_basis", precision = 10, scale = 2)
    private BigDecimal avgCostBasis;

    @Column(name = "close_price", precision = 10, scale = 2)
    private BigDecimal closePrice;

    @Column(name = "high_price", precision = 10, scale = 2)
    private BigDecimal highPrice;

    @Column(name = "low_price", precision = 10, scale = 2)
    private BigDecimal lowPrice;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
