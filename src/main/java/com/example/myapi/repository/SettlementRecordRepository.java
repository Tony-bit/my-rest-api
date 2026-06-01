package com.example.myapi.repository;

import com.example.myapi.entity.SettlementRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SettlementRecordRepository extends JpaRepository<SettlementRecord, Long> {

    List<SettlementRecord> findByBatchId(Long batchId);
}
