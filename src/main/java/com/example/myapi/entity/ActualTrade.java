package com.example.myapi.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "actual_trade", indexes = {
    @Index(name = "idx_at_stock", columnList = "stock_code"),
    @Index(name = "idx_at_date", columnList = "trade_date"),
    @Index(name = "idx_at_direction", columnList = "direction")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActualTrade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_code", nullable = false, length = 20)
    private String stockCode;

    @Column(name = "stock_name", length = 50)
    private String stockName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TradeDirection direction;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal price;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "turnover_amount", precision = 18, scale = 4)
    private BigDecimal turnoverAmount;

    @Column(name = "settlement_amount", precision = 18, scale = 4)
    private BigDecimal settlementAmount;

    @Column(name = "stamp_tax", precision = 18, scale = 4)
    private BigDecimal stampTax;

    @Column(name = "transfer_fee", precision = 18, scale = 4)
    private BigDecimal transferFee;

    @Column(name = "commission", precision = 18, scale = 4)
    private BigDecimal commission;

    @Column(name = "other_fee", precision = 18, scale = 4)
    private BigDecimal otherFee;

    @Column(name = "total_fee", precision = 18, scale = 4)
    private BigDecimal totalFee;

    @Column(name = "settlement_account_number", length = 40)
    private String settlementAccountNumber;

    @Column(name = "settlement_trade_type", length = 40)
    private String settlementTradeType;

    @Column(name = "settlement_unique_key", length = 255, unique = true)
    private String settlementUniqueKey;

    @Column(name = "settlement_record_id")
    private Long settlementRecordId;

    @Column(name = "profit_loss", precision = 12, scale = 2)
    private BigDecimal profitLoss;

    @Column(name = "profit_loss_percent", precision = 8, scale = 4)
    private BigDecimal profitLossPercent;

    @Column(name = "matched_buy_id")
    private Long matchedBuyId;

    @Column(name = "is_matched", nullable = false)
    @Builder.Default
    private Boolean isMatched = false;

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
