package com.example.myapi.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "plan", indexes = {
    @Index(name = "idx_plan_status", columnList = "status"),
    @Index(name = "idx_plan_stock", columnList = "stock_code"),
    @Index(name = "idx_plan_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "stock_code", nullable = false, length = 20)
    private String stockCode;

    @Column(name = "stock_name", length = 50)
    private String stockName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanCycle cycle;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false, length = 10)
    private PlanType planType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PlanStatus status = PlanStatus.PENDING;

    @Column(name = "trade_plan_id")
    private Long tradePlanId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buy_plan_id")
    private Plan buyPlan;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "trigger_date")
    private LocalDate triggerDate;

    @Column(name = "execution_quantity", precision = 10, scale = 2)
    @Builder.Default
    private java.math.BigDecimal executionQuantity = new java.math.BigDecimal("100");

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PlanCondition> conditions = new ArrayList<>();

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<PlanExecution> executions = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addCondition(PlanCondition condition) {
        conditions.add(condition);
        condition.setPlan(this);
    }

    public void removeCondition(PlanCondition condition) {
        conditions.remove(condition);
        condition.setPlan(null);
    }
}
