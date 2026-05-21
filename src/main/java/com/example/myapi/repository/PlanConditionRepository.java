package com.example.myapi.repository;

import com.example.myapi.entity.PlanCondition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PlanConditionRepository extends JpaRepository<PlanCondition, Long> {

    List<PlanCondition> findByPlanId(Long planId);

    List<PlanCondition> findByPlanIdAndIsActiveTrue(Long planId);
}
