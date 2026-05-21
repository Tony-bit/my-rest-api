package com.example.myapi.repository;

import com.example.myapi.entity.PlanExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PlanExecutionRepository extends JpaRepository<PlanExecution, Long> {

    List<PlanExecution> findByPlanId(Long planId);

    List<PlanExecution> findByPlanIdOrderByTradeDateAsc(Long planId);

    boolean existsByPlanIdAndExecutedTrue(Long planId);
}
