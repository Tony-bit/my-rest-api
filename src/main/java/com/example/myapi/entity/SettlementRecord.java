package com.example.myapi.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "settlement_record", indexes = {
        @Index(name = "idx_sr_batch", columnList = "batch_id"),
        @Index(name = "idx_sr_settlement_key", columnList = "settlement_unique_key"),
        @Index(name = "idx_sr_trade_date", columnList = "trade_date"),
        @Index(name = "idx_sr_stock", columnList = "stock_code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", nullable = false)
    private SettlementImportBatch batch;

    @Column(name = "`row_number`", nullable = false)
    private int rowNumber;

    @Column(name = "raw_line", columnDefinition = "TEXT")
    private String rawLine;

    @Column(name = "stock_code", length = 20)
    private String stockCode;

    @Column(name = "stock_name", length = 50)
    private String stockName;

    @Column(name = "turnover_amount", precision = 18, scale = 4)
    private BigDecimal turnoverAmount;

    @Column(name = "settlement_amount", precision = 18, scale = 4)
    private BigDecimal settlementAmount;

    @Column(name = "stamp_tax", precision = 18, scale = 4)
    private BigDecimal stampTax;

    @Column(name = "transfer_fee", precision = 18, scale = 4)
    private BigDecimal transferFee;

    @Column(name = "trade_date")
    private LocalDate tradeDate;

    @Column(name = "trade_type", length = 40)
    private String tradeType;

    @Column(name = "currency", length = 20)
    private String currency;

    @Column(name = "quantity", precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(name = "price", precision = 18, scale = 4)
    private BigDecimal price;

    @Column(name = "account_number", length = 40)
    private String accountNumber;

    @Column(name = "commission", precision = 18, scale = 4)
    private BigDecimal commission;

    @Column(name = "other_fee", precision = 18, scale = 4)
    private BigDecimal otherFee;

    @Column(name = "remark", length = 255)
    private String remark;

    @Column(name = "settlement_unique_key", length = 255)
    private String settlementUniqueKey;

    @Column(name = "import_status", nullable = false, length = 20)
    private String importStatus;

    @Column(name = "skip_reason", length = 255)
    private String skipReason;

    @Column(name = "actual_trade_id")
    private Long actualTradeId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (importStatus == null) {
            importStatus = "ARCHIVED";
        }
    }
}
