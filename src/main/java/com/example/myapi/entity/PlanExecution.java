package com.example.myapi.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "plan_execution", indexes = {
    @Index(name = "idx_exec_plan", columnList = "plan_id"),
    @Index(name = "idx_exec_date", columnList = "trade_date"),
    @Index(name = "idx_exec_direction", columnList = "direction")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlanExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TradeDirection direction;

    @Column(name = "triggered", nullable = false)
    @Builder.Default
    private Boolean triggered = true;

    @Column(name = "trigger_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal triggerPrice;

    @Column(name = "close_price", precision = 10, scale = 2)
    private BigDecimal closePrice;

    @Column(name = "ma_value", precision = 10, scale = 2)
    private BigDecimal maValue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "condition_id")
    private PlanCondition condition;

    @Column(nullable = false)
    @Builder.Default
    private Boolean executed = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
