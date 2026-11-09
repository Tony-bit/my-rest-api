package com.example.myapi.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;

@Entity
@Table(name="shengyi_review_delivery",uniqueConstraints=@UniqueConstraint(name="uk_shengyi_daily_rule",columnNames={"stock_code","execution_date","rule_version"}))
@Getter @Setter @NoArgsConstructor
public class ShengyiReviewDelivery {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="stock_code",nullable=false,length=10) private String stockCode="600183.SH";
    @Column(name="execution_date",nullable=false) private LocalDate executionDate;
    @Column(name="rule_version",nullable=false,length=60) private String ruleVersion;
    @Column(name="signal_date",nullable=false) private LocalDate signalDate;
    @Column(name="signal_type",nullable=false,length=10) private String signal;
    @Column(nullable=false,length=20) private String status="CLAIMED";
    @Column(name="failure_reason",length=200) private String failureReason;
    @Column(name="created_at",nullable=false) private Instant createdAt=Instant.now();
    @Column(name="sent_at") private Instant sentAt;
}
