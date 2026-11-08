package com.example.myapi.repository;
import com.example.myapi.entity.ShengyiReviewDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
public interface ShengyiReviewDeliveryRepository extends JpaRepository<ShengyiReviewDelivery,Long> {
    boolean existsByStockCodeAndExecutionDateAndRuleVersion(String stockCode,LocalDate executionDate,String ruleVersion);
}
