package com.example.myapi.repository;

import com.example.myapi.entity.PlanExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PlanExecutionRepository extends JpaRepository<PlanExecution, Long> {

    List<PlanExecution> findByPlanId(Long planId);

    @Query("SELECT e FROM PlanExecution e JOIN FETCH e.plan WHERE e.plan.id = :planId ORDER BY e.tradeDate ASC")
    List<PlanExecution> findByPlanIdWithPlan(@Param("planId") Long planId);

    @Query("SELECT e FROM PlanExecution e JOIN FETCH e.plan ORDER BY e.tradeDate DESC")
    List<PlanExecution> findAllWithPlan();

    List<PlanExecution> findByPlanIdOrderByTradeDateAsc(Long planId);

    boolean existsByPlanIdAndExecutedTrue(Long planId);
}
