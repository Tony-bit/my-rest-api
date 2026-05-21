package com.example.myapi.repository;

import com.example.myapi.entity.PlanAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlanAccountRepository extends JpaRepository<PlanAccount, Long> {
}
