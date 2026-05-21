package com.example.myapi.repository;

import com.example.myapi.entity.Plan;
import com.example.myapi.entity.PlanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PlanRepository extends JpaRepository<Plan, Long> {

    List<Plan> findByStatus(PlanStatus status);

    List<Plan> findByStockCode(String stockCode);

    List<Plan> findByStatusAndStockCode(PlanStatus status, String stockCode);

    @Query("SELECT p FROM Plan p WHERE p.status IN :statuses")
    List<Plan> findByStatusIn(@Param("statuses") List<PlanStatus> statuses);

    @Query("SELECT p FROM Plan p WHERE p.status = :status AND p.stockCode = :stockCode")
    List<Plan> findActivePlans(@Param("status") PlanStatus status, @Param("stockCode") String stockCode);
}
