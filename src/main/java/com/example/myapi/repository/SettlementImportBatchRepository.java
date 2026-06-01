package com.example.myapi.repository;

import com.example.myapi.entity.SettlementImportBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SettlementImportBatchRepository extends JpaRepository<SettlementImportBatch, Long> {
}
